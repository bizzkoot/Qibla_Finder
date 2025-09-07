package com.bizzkoot.qiblafinder.model

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import android.hardware.GeomagneticField
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt
import timber.log.Timber

sealed interface OrientationState {
    object Initializing : OrientationState
    data class Available(
        val trueHeading: Float,
        val compassStatus: CompassStatus,
        val isPhoneFlat: Boolean = true,
        val isPhoneVertical: Boolean = false,
        val phoneTiltAngle: Float = 0f
    ) : OrientationState
}

enum class CompassStatus {
    OK,
    NEEDS_CALIBRATION,
    INTERFERENCE
}

class SensorRepository @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationMatrix = FloatArray(9)
    private val adjustedRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastHeading: Float = 0f
    private var calibrationOffset: Double = 0.0
    private var cachedDeclination: Float = 0f
    private var isPhoneFlat: Boolean = true
    private var isPhoneVertical: Boolean = false
    private var phoneTiltAngle: Float = 0f
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var outOfBandCount = 0
    private var inBandCount = 0

    private val _orientationState = MutableStateFlow<OrientationState>(OrientationState.Initializing)
    val orientationState: Flow<OrientationState> = _orientationState.asStateFlow()

    fun getOrientationFlow(): Flow<OrientationState> = callbackFlow {
        Timber.d("🔧 Starting compass sensor initialization...")

        // Keep declination updated from location repository while this flow is active
        val locationJob = launch {
            try {
                locationRepository.getLocation().collect { state ->
                    if (state is LocationState.Available) {
                        val loc = state.location
                        val gf = GeomagneticField(
                            loc.latitude.toFloat(),
                            loc.longitude.toFloat(),
                            (loc.altitude.takeIf { !it.isNaN() } ?: 0.0).toFloat(),
                            System.currentTimeMillis()
                        )
                        cachedDeclination = gf.declination
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "⚠️ Declination update failed; using last cached value: $cachedDeclination")
            }
        }

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                        // Remap matrix based on display rotation for consistent azimuth
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        val rotation = windowManager.defaultDisplay.rotation
                        val (axisX, axisY) = when (rotation) {
                            Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                        }
                        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix)
                        SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles)

                        val magneticAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        val magneticHeading = (magneticAzimuth + 360) % 360

                        val declination = getMagneticDeclination()
                        val trueHeading = (magneticHeading + declination + 360) % 360

                        val alpha = 0.15f
                        val smoothedHeading = smoothAngle(lastHeading, trueHeading, alpha)
                        lastHeading = smoothedHeading

                        val calibratedHeading = (smoothedHeading + calibrationOffset.toFloat() + 360) % 360

                        val currentStatus = (_orientationState.value as? OrientationState.Available)?.compassStatus ?: CompassStatus.OK

                        val newState = OrientationState.Available(
                            trueHeading = calibratedHeading,
                            compassStatus = currentStatus,
                            isPhoneFlat = isPhoneFlat,
                            isPhoneVertical = isPhoneVertical,
                            phoneTiltAngle = phoneTiltAngle
                        )
                        _orientationState.value = newState
                        trySend(newState) // <-- THE FIX
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        checkInterference(event.values)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                        checkPhoneOrientation()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    val status = when (accuracy) {
                        SensorManager.SENSOR_STATUS_UNRELIABLE,
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassStatus.NEEDS_CALIBRATION
                        else -> CompassStatus.OK
                    }
                    if (_orientationState.value is OrientationState.Available) {
                        val currentState = _orientationState.value as OrientationState.Available
                        _orientationState.value = currentState.copy(compassStatus = status)
                    }
                }
            }
        }

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (rotationVectorSensor == null) {
            Timber.e("❌ Rotation Vector Sensor not available.")
            trySend(OrientationState.Initializing) // Or a specific error state
            awaitClose()
            return@callbackFlow
        }
        
        sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(sensorEventListener)
            locationJob.cancel()
        }
    }

    private fun updateOrientation(rotationVector: FloatArray) {
        try {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val magneticAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val magneticHeading = (magneticAzimuth + 360) % 360

            val declination = getMagneticDeclination()
            val trueHeading = (magneticHeading + declination + 360) % 360

            val alpha = 0.15f
            val smoothedHeading = lastHeading + alpha * (trueHeading - lastHeading)
            lastHeading = smoothedHeading

            val calibratedHeading = (smoothedHeading + calibrationOffset.toFloat() + 360) % 360

            val currentStatus = (_orientationState.value as? OrientationState.Available)?.compassStatus ?: CompassStatus.OK

            val newState = OrientationState.Available(
                trueHeading = calibratedHeading,
                compassStatus = currentStatus,
                isPhoneFlat = isPhoneFlat,
                isPhoneVertical = isPhoneVertical,
                phoneTiltAngle = phoneTiltAngle
            )
            _orientationState.value = newState
        } catch (e: Exception) {
            Timber.e(e, "❌ Error processing orientation data")
        }
    }

    
    private fun getMagneticDeclination(): Float = cachedDeclination

    private fun checkInterference(magneticField: FloatArray) {
        val magnitude = sqrt(magneticField[0].pow(2) + magneticField[1].pow(2) + magneticField[2].pow(2))
        // Typical Earth's field ~25–65 µT; consider 20–80 as acceptable band
        val inBand = magnitude in 20.0..80.0

        if (inBand) {
            inBandCount = (inBandCount + 1).coerceAtMost(1000)
            outOfBandCount = 0
        } else {
            outOfBandCount = (outOfBandCount + 1).coerceAtMost(1000)
            inBandCount = 0
        }

        val status = if (outOfBandCount >= 10) CompassStatus.INTERFERENCE else CompassStatus.OK

        if (_orientationState.value is OrientationState.Available) {
            val currentState = _orientationState.value as OrientationState.Available
            // Do not override explicit NEEDS_CALIBRATION from magnetometer accuracy
            if (currentState.compassStatus != CompassStatus.NEEDS_CALIBRATION) {
                // Require sustained in-band readings to clear INTERFERENCE
                if (status == CompassStatus.OK && inBandCount < 20 && currentState.compassStatus == CompassStatus.INTERFERENCE) return
                _orientationState.value = currentState.copy(compassStatus = status)
            }
        }
    }
    
    /**
     * Sets the calibration offset to be applied to compass readings
     */
    fun setCalibrationOffset(offset: Double) {
        calibrationOffset = offset
    }
    
    
    /**
     * Checks if phone is flat for accurate compass readings
     */
    private fun checkPhoneOrientation() {
        // Calculate tilt angle from accelerometer data
        val x = accelerometerReading[0]
        val y = accelerometerReading[1]
        val z = accelerometerReading[2]
        
        // Calculate tilt angle (deviation from vertical)
        val tiltAngle = Math.toDegrees(kotlin.math.atan2(
            kotlin.math.sqrt((x * x + y * y).toDouble()), z.toDouble()
        )).toFloat()
        
        phoneTiltAngle = tiltAngle
        
        // Phone is considered flat if tilt is within 25 degrees of horizontal (more forgiving)
        // Vertical is when tilt is close to 0° or 180° (within 10 degrees - more strict)
        val wasFlat = isPhoneFlat
        val wasVertical = isPhoneVertical
        
        isPhoneFlat = tiltAngle >= 65f && tiltAngle <= 115f
        isPhoneVertical = (tiltAngle <= 10f) || (tiltAngle >= 170f)
        
        if (wasFlat != isPhoneFlat || wasVertical != isPhoneVertical) {
            Timber.d("📱 Phone orientation changed: ${if (isPhoneFlat) "FLAT" else "NOT FLAT"}, ${if (isPhoneVertical) "VERTICAL" else "NOT VERTICAL"} (tilt: ${tiltAngle.toInt()}°)")
        }
    }

    private fun smoothAngle(prev: Float, current: Float, alpha: Float): Float {
        val delta = ((current - prev + 540f) % 360f) - 180f
        return (prev + alpha * delta + 360f) % 360f
    }
    
    /**
     * Gets current phone orientation status
     */
    fun isPhoneFlat(): Boolean = isPhoneFlat
    
    /**
     * Gets current phone vertical status
     */
    fun isPhoneVertical(): Boolean = isPhoneVertical
    
    /**
     * Gets current phone tilt angle
     */
    fun getPhoneTiltAngle(): Float = phoneTiltAngle
    
}
