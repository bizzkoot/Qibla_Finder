package com.bizzkoot.qiblafinder.model

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
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
    private val orientationAngles = FloatArray(3)
    private var lastHeading: Float = 0f
    private var calibrationOffset: Double = 0.0
    private var isPhoneFlat: Boolean = true
    private var isPhoneVertical: Boolean = false
    private var phoneTiltAngle: Float = 0f
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val _orientationState = MutableStateFlow<OrientationState>(OrientationState.Initializing)
    val orientationState: Flow<OrientationState> = _orientationState.asStateFlow()

    fun getOrientationFlow(): Flow<OrientationState> = callbackFlow {
        Timber.d("🔧 Starting compass sensor initialization...")

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
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
                if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val status = when (accuracy) {
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassStatus.NEEDS_CALIBRATION
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

    
    private fun getMagneticDeclination(): Float {
        // Since we can't collect from Flow synchronously, we'll use a default value
        // In a real app, this should be handled with proper coroutine scope
        return 0.0f
    }

    private fun checkInterference(magneticField: FloatArray) {
        val magnitude = sqrt(magneticField[0].pow(2) + magneticField[1].pow(2) + magneticField[2].pow(2))
        val status = if (magnitude > 100.0) CompassStatus.INTERFERENCE else CompassStatus.OK

        if (_orientationState.value is OrientationState.Available) {
            val currentState = _orientationState.value as OrientationState.Available
            if (currentState.compassStatus != CompassStatus.NEEDS_CALIBRATION) {
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