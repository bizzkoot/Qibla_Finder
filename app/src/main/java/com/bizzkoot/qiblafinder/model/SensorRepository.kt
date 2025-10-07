package com.bizzkoot.qiblafinder.model

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import timber.log.Timber

data class CompassFilterConfig(
    val targetSamplingRateHz: Int = 50,
    val minSamplingRateHz: Int = 20,
    val accelLowPassAlpha: Float = 0.85f,
    val motionNoiseFloor: Float = 0.015f,
    val motionClamp: Float = 3f,
    val motionDecay: Float = 0.6f,
    val motionRecentWeight: Float = 0.4f,
    val headingContribution: Float = 0.1f,
    val highMotionThreshold: Float = 1.5f,
    val mediumMotionThreshold: Float = 0.4f,
    val minAlpha: Float = 0.05f,
    val maxAlpha: Float = 0.6f,
    val defaultDeltaSeconds: Float = 0.02f
)

data class CalibrationPromptConfig(
    val accuracyDebounceMs: Long = 1_000L,
    val triggerDurationMs: Long = 5_000L,
    val cooldownMs: Long = 60_000L
)

interface CompassAnalytics {
    fun onMagnetometerAccuracyChanged(accuracy: Int) {}
    fun onCalibrationPromptShown(source: CalibrationPromptSource) {}
    fun onCalibrationPromptDismissed(reason: CalibrationDismissReason) {}
    fun onLinearAccelerationMeasured(magnitude: Float) {}

    object NO_OP : CompassAnalytics
}

enum class CalibrationPromptSource { AUTOMATIC, MANUAL }

enum class CalibrationDismissReason { USER_DISMISSED, ACCURACY_RECOVERED }

internal class AdaptiveCompassFilter(
    private val config: CompassFilterConfig,
    private val analytics: CompassAnalytics
) {
    private var lastTimestampNs: Long = 0L
    private var motionEstimate: Float = 0f
    private var lastRawHeading: Float? = null
    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)
    private var hasGravitySample = false

    fun reset() {
        lastTimestampNs = 0L
        motionEstimate = 0f
        lastRawHeading = null
        gravity.fill(0f)
        linearAcceleration.fill(0f)
        hasGravitySample = false
    }

    fun onAccelerometerEvent(event: SensorEvent) {
        for (i in 0..2) {
            val value = event.values[i]
            val gravitySample = if (hasGravitySample) {
                config.accelLowPassAlpha * gravity[i] + (1f - config.accelLowPassAlpha) * value
            } else {
                value
            }
            gravity[i] = gravitySample
            linearAcceleration[i] = value - gravitySample
        }
        hasGravitySample = true

        val magnitude = sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
                linearAcceleration[1] * linearAcceleration[1] +
                linearAcceleration[2] * linearAcceleration[2]
        )
        val adjustedMagnitude = (magnitude - config.motionNoiseFloor).coerceAtLeast(0f)
        val clampedMagnitude = adjustedMagnitude.coerceAtMost(config.motionClamp)
        motionEstimate = (motionEstimate * config.motionDecay) + (clampedMagnitude * config.motionRecentWeight)
        analytics.onLinearAccelerationMeasured(clampedMagnitude)
    }

    fun computeAlpha(eventTimestampNs: Long, rawHeading: Float): Float {
        val previousTimestampNs = lastTimestampNs
        lastTimestampNs = eventTimestampNs

        val deltaSeconds = if (previousTimestampNs == 0L) {
            config.defaultDeltaSeconds
        } else {
            ((eventTimestampNs - previousTimestampNs) / 1_000_000_000f).coerceAtLeast(0.001f)
        }

        val previousHeading = lastRawHeading
        lastRawHeading = rawHeading

        val headingDelta = if (previousHeading == null) {
            0f
        } else {
            angularDistanceDegrees(previousHeading, rawHeading)
        }

        val combinedMotion = motionEstimate + (headingDelta * config.headingContribution)

        val timeConstant = when {
            combinedMotion > config.highMotionThreshold -> 0.08f
            combinedMotion > config.mediumMotionThreshold -> 0.12f
            else -> 0.20f
        }

        val alpha = deltaSeconds / (timeConstant + deltaSeconds)
        return alpha.coerceIn(config.minAlpha, config.maxAlpha)
    }

    companion object {
        private fun angularDistanceDegrees(from: Float, to: Float): Float {
            val diff = ((to - from + 540f) % 360f) - 180f
            return abs(diff)
        }
    }
}

sealed interface OrientationState {
    object Initializing : OrientationState
    data class Available(
        val trueHeading: Float,
        val compassStatus: CompassStatus,
        val isPhoneFlat: Boolean = true,
        val isPhoneVertical: Boolean = false,
        val phoneTiltAngle: Float = 0f,
        val shouldShowCalibration: Boolean = false
    ) : OrientationState
}

enum class CompassStatus {
    OK,
    NEEDS_CALIBRATION,
    INTERFERENCE
}

private enum class OrientationMode {
    ROTATION_VECTOR,
    KALMAN_FUSION
}

class SensorRepository @Inject constructor(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val analytics: CompassAnalytics = CompassAnalytics.NO_OP,
    private val filterConfig: CompassFilterConfig = CompassFilterConfig(),
    private val calibrationConfig: CalibrationPromptConfig = CalibrationPromptConfig()
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationMatrix = FloatArray(9)
    private val adjustedRotationMatrix = FloatArray(9)
    private val inclinationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastHeading: Float = 0f
    private var calibrationOffset: Double = 0.0
    private var cachedDeclination: Float = 0f
    private var isPhoneFlat: Boolean = true
    private var isPhoneVertical: Boolean = false
    private var phoneTiltAngle: Float = 0f
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometerSample = false
    private var hasMagnetometerSample = false
    private var outOfBandCount = 0
    private var inBandCount = 0
    private val adaptiveFilter = AdaptiveCompassFilter(filterConfig, analytics)
    private val kalmanFusion = KalmanCompassFusion()
    private var orientationMode: OrientationMode = OrientationMode.ROTATION_VECTOR
    private var lastGyroTimestampNs: Long = 0L
    private var magnetometerAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var lastAccuracyChangeMs: Long = 0L
    private var lowAccuracyStartMs: Long = 0L
    private var interferenceStartMs: Long = 0L
    private var calibrationCooldownUntilMs: Long = 0L
    private var calibrationVisible = false

    private val _orientationState = MutableStateFlow<OrientationState>(OrientationState.Initializing)
    val orientationState: Flow<OrientationState> = _orientationState.asStateFlow()

    fun getOrientationFlow(): Flow<OrientationState> = callbackFlow {
        Timber.d("🔧 Starting compass sensor initialization...")

        adaptiveFilter.reset()
        kalmanFusion.reset()
        hasAccelerometerSample = false
        hasMagnetometerSample = false
        lastGyroTimestampNs = 0L

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        orientationMode = if (accelerometer != null && magnetometer != null) {
            OrientationMode.KALMAN_FUSION
        } else {
            OrientationMode.ROTATION_VECTOR
        }

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

        fun computeTiltCompensatedHeading(): Float? {
            if (!hasAccelerometerSample || !hasMagnetometerSample) return null
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                inclinationMatrix,
                accelerometerReading,
                magnetometerReading
            )
            if (!success) return null

            val rotation = getDisplayRotation()
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
            return (magneticHeading + declination + 360) % 360
        }

        fun emitFusedHeading(timestampNs: Long, measurementHeading: Float? = null, isPredictUpdate: Boolean = false) {
            measurementHeading?.let {
                if (!kalmanFusion.hasEstimate()) {
                    kalmanFusion.reset(it)
                } else {
                    kalmanFusion.correct(it)
                }
            }

            val fusedHeading = kalmanFusion.getHeading() ?: measurementHeading ?: return

            val alpha = adaptiveFilter.computeAlpha(timestampNs, fusedHeading)
            val accuracyAcceptable = hasAcceptableAccuracy()
            val shouldUpdateHeading = accuracyAcceptable || lastHeading == 0f || isPredictUpdate
            val smoothedHeading = if (shouldUpdateHeading) {
                val next = smoothAngle(lastHeading, fusedHeading, alpha)
                lastHeading = next
                next
            } else {
                lastHeading
            }

            val calibratedHeading = (smoothedHeading + calibrationOffset.toFloat() + 360) % 360

            val currentStatus = (_orientationState.value as? OrientationState.Available)?.compassStatus ?: CompassStatus.OK

            val newState = OrientationState.Available(
                trueHeading = calibratedHeading,
                compassStatus = currentStatus,
                isPhoneFlat = isPhoneFlat,
                isPhoneVertical = isPhoneVertical,
                phoneTiltAngle = phoneTiltAngle,
                shouldShowCalibration = calibrationVisible
            )
            _orientationState.value = newState
            trySend(newState)
        }

        fun handleTiltCompensatedMeasurement(timestampNs: Long) {
            val heading = computeTiltCompensatedHeading() ?: return
            emitFusedHeading(timestampNs, measurementHeading = heading)
        }

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        if (orientationMode != OrientationMode.ROTATION_VECTOR) return
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                        // Remap matrix based on display rotation for consistent azimuth
                        val rotation = getDisplayRotation()
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

                        val alpha = adaptiveFilter.computeAlpha(event.timestamp, trueHeading)
                        val accuracyAcceptable = hasAcceptableAccuracy()
                        val shouldUpdateHeading = accuracyAcceptable || lastHeading == 0f
                        val smoothedHeading = if (shouldUpdateHeading) {
                            val next = smoothAngle(lastHeading, trueHeading, alpha)
                            lastHeading = next
                            next
                        } else {
                            lastHeading
                        }

                        val calibratedHeading = (smoothedHeading + calibrationOffset.toFloat() + 360) % 360

                        val currentStatus = (_orientationState.value as? OrientationState.Available)?.compassStatus ?: CompassStatus.OK

                        val newState = OrientationState.Available(
                            trueHeading = calibratedHeading,
                            compassStatus = currentStatus,
                            isPhoneFlat = isPhoneFlat,
                            isPhoneVertical = isPhoneVertical,
                            phoneTiltAngle = phoneTiltAngle,
                            shouldShowCalibration = calibrationVisible
                        )
                        _orientationState.value = newState
                        trySend(newState)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                        hasMagnetometerSample = true
                        checkInterference(event.values)
                        if (orientationMode == OrientationMode.KALMAN_FUSION && hasAccelerometerSample) {
                            handleTiltCompensatedMeasurement(event.timestamp)
                        }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        adaptiveFilter.onAccelerometerEvent(event)
                        System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                        hasAccelerometerSample = true
                        checkPhoneOrientation()
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        if (orientationMode != OrientationMode.KALMAN_FUSION) return
                        val previousTimestamp = lastGyroTimestampNs
                        lastGyroTimestampNs = event.timestamp
                        if (!kalmanFusion.hasEstimate()) return
                        if (previousTimestamp != 0L) {
                            val deltaSeconds = ((event.timestamp - previousTimestamp) / 1_000_000_000f).coerceAtLeast(0.0005f)
                            val gyroRateDegPerSec = Math.toDegrees(event.values[2].toDouble()).toFloat()
                            kalmanFusion.predict(gyroRateDegPerSec, deltaSeconds)
                            emitFusedHeading(event.timestamp, isPredictUpdate = true)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return

                val now = SystemClock.elapsedRealtime()
                if (magnetometerAccuracy != accuracy) {
                    lastAccuracyChangeMs = now
                }
                magnetometerAccuracy = accuracy
                analytics.onMagnetometerAccuracyChanged(accuracy)

                val status = when (accuracy) {
                    SensorManager.SENSOR_STATUS_UNRELIABLE,
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassStatus.NEEDS_CALIBRATION
                    else -> CompassStatus.OK
                }

                updateCompassStatus(status)

                when (status) {
                    CompassStatus.NEEDS_CALIBRATION -> handleLowAccuracy(now, status)
                    CompassStatus.OK -> handleAccuracyRecovered()
                    else -> Unit
                }
            }
        }

        val magneticFieldSensor = magnetometer

        if (orientationMode == OrientationMode.ROTATION_VECTOR && rotationVectorSensor == null) {
            Timber.e("❌ Rotation Vector Sensor not available.")
            trySend(OrientationState.Initializing)
            awaitClose()
            return@callbackFlow
        }
        val sensorThread = HandlerThread("CompassSensors").also { it.start() }
        val sensorHandler = Handler(sensorThread.looper)

        val targetSamplingPeriodUs = (1_000_000f / filterConfig.targetSamplingRateHz).toInt().coerceAtLeast(5_000)
        val fallbackSamplingPeriodUs = (1_000_000f / filterConfig.minSamplingRateHz).toInt().coerceAtLeast(targetSamplingPeriodUs)

        var samplingPeriodUs = targetSamplingPeriodUs
        if (orientationMode == OrientationMode.ROTATION_VECTOR) {
            var rotationRegistered = sensorManager.registerListener(
                sensorEventListener,
                rotationVectorSensor,
                samplingPeriodUs,
                sensorHandler
            )

            if (!rotationRegistered) {
                samplingPeriodUs = fallbackSamplingPeriodUs
                rotationRegistered = sensorManager.registerListener(
                    sensorEventListener,
                    rotationVectorSensor,
                    samplingPeriodUs,
                    sensorHandler
                )

                if (!rotationRegistered) {
                    Timber.e("❌ Failed to register rotation vector sensor at fallback rate")
                    sensorThread.quitSafely()
                    locationJob.cancel()
                    close(IllegalStateException("Rotation vector sensor registration failed"))
                    return@callbackFlow
                }
            }
        }

        magneticFieldSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs, sensorHandler)
        }
        accelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs, sensorHandler)
        }
        if (orientationMode == OrientationMode.KALMAN_FUSION) {
            gyroscope?.let {
                sensorManager.registerListener(sensorEventListener, it, samplingPeriodUs, sensorHandler)
            }
        }

        awaitClose {
            sensorManager.unregisterListener(sensorEventListener)
            locationJob.cancel()
            sensorThread.quitSafely()
        }
    }

    fun onManualCalibrationRequested() {
        Timber.d("🧭 Manual calibration requested")
        showCalibrationPrompt(
            source = CalibrationPromptSource.MANUAL,
            status = (_orientationState.value as? OrientationState.Available)?.compassStatus
                ?: CompassStatus.NEEDS_CALIBRATION
        )
    }

    fun onCalibrationDismissed() {
        Timber.d("🧭 Calibration overlay dismissed by user")
        hideCalibrationPrompt(CalibrationDismissReason.USER_DISMISSED)
    }

    private fun updateCompassStatus(status: CompassStatus) {
        val current = _orientationState.value
        if (current is OrientationState.Available && current.compassStatus != status) {
            _orientationState.value = current.copy(compassStatus = status)
        }
    }

    private fun handleLowAccuracy(nowMs: Long, status: CompassStatus) {
        if (lowAccuracyStartMs == 0L) {
            lowAccuracyStartMs = nowMs
        }
        val elapsed = nowMs - lowAccuracyStartMs
        val sinceChange = nowMs - lastAccuracyChangeMs
        if (elapsed >= calibrationConfig.triggerDurationMs && sinceChange >= calibrationConfig.accuracyDebounceMs) {
            showCalibrationPrompt(CalibrationPromptSource.AUTOMATIC, status)
        }
    }

    private fun handleAccuracyRecovered() {
        lowAccuracyStartMs = 0L
        if (interferenceStartMs == 0L && calibrationVisible) {
            hideCalibrationPrompt(CalibrationDismissReason.ACCURACY_RECOVERED)
        }
    }

    private fun handleInterference(nowMs: Long) {
        if (interferenceStartMs == 0L) {
            interferenceStartMs = nowMs
        }
        if (nowMs - interferenceStartMs >= calibrationConfig.triggerDurationMs) {
            showCalibrationPrompt(CalibrationPromptSource.AUTOMATIC, CompassStatus.INTERFERENCE)
        }
    }

    private fun clearInterference() {
        interferenceStartMs = 0L
        if (lowAccuracyStartMs == 0L && calibrationVisible) {
            hideCalibrationPrompt(CalibrationDismissReason.ACCURACY_RECOVERED)
        }
    }

    private fun showCalibrationPrompt(source: CalibrationPromptSource, status: CompassStatus) {
        val now = SystemClock.elapsedRealtime()
        if (source == CalibrationPromptSource.AUTOMATIC && now < calibrationCooldownUntilMs) {
            Timber.d("🧭 Calibration prompt suppressed (cooldown active)")
            return
        }

        if (!calibrationVisible) {
            calibrationVisible = true
            analytics.onCalibrationPromptShown(source)
        }

        val current = _orientationState.value
        if (current is OrientationState.Available) {
            val updatedStatus = when (status) {
                CompassStatus.NEEDS_CALIBRATION, CompassStatus.INTERFERENCE -> status
                else -> current.compassStatus
            }
            _orientationState.value = current.copy(
                compassStatus = updatedStatus,
                shouldShowCalibration = true
            )
        }
    }

    private fun hideCalibrationPrompt(reason: CalibrationDismissReason) {
        if (!calibrationVisible) return

        calibrationVisible = false
        if (reason == CalibrationDismissReason.USER_DISMISSED) {
            calibrationCooldownUntilMs = SystemClock.elapsedRealtime() + calibrationConfig.cooldownMs
        }
        analytics.onCalibrationPromptDismissed(reason)

        val current = _orientationState.value
        if (current is OrientationState.Available) {
            _orientationState.value = current.copy(shouldShowCalibration = false)
        }
    }

    private fun hasAcceptableAccuracy(): Boolean {
        return magnetometerAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
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

        val now = SystemClock.elapsedRealtime()
        if (status == CompassStatus.INTERFERENCE) {
            handleInterference(now)
        } else {
            clearInterference()
        }

        val current = _orientationState.value
        if (current is OrientationState.Available) {
            if (current.compassStatus != CompassStatus.NEEDS_CALIBRATION) {
                if (status == CompassStatus.OK && inBandCount < 20 && current.compassStatus == CompassStatus.INTERFERENCE) {
                    return
                }
                _orientationState.value = current.copy(compassStatus = status)
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

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }
}
