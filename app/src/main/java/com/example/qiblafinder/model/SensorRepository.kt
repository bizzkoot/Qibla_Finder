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
        val calibrationProgress: Float = 0f,
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

    // Proper sensor fusion for compass
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val remappedRotationMatrix = FloatArray(9)

    private var lastHeading: Float = 0f
    private var currentCalibratedHeading: Float = 0f
    
    // Calibration offset from sun calibration
    private var calibrationOffset: Double = 0.0
    
    // Calibration tracking
    private var calibrationStartTime: Long = 0
    private var calibrationMoves: Int = 0
    private var lastCalibrationMove: Long = 0
    private var isCalibrating: Boolean = false
    private var lastCalibrationHeading: Float = 0f
    
    // Figure-8 movement tracking
    private var movementHistory = mutableListOf<Float>()
    private var totalRotation: Float = 0f
    private var directionChanges: Int = 0
    private var lastMovementDirection: Float = 0f
    
    // Phone orientation tracking
    private var isPhoneFlat: Boolean = true
    private var isPhoneVertical: Boolean = false
    private var phoneTiltAngle: Float = 0f
    
    // Sensor data validation
    private var hasAccelerometerData: Boolean = false
    private var hasMagnetometerData: Boolean = false
    
    // Device orientation tracking
    private var deviceOrientation: Int = 0 // 0, 90, 180, 270 degrees

    private val _orientationState = MutableStateFlow<OrientationState>(OrientationState.Initializing)
    val orientationState: Flow<OrientationState> = _orientationState.asStateFlow()

    fun getOrientationFlow(): Flow<OrientationState> = callbackFlow {
        Timber.d("🔧 Starting compass sensor initialization...")
        
        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        Timber.d("📱 Accelerometer data: ${event.values.joinToString(", ")}")
                        System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                        hasAccelerometerData = true
                        updateOrientation(this@callbackFlow)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        Timber.d("🧲 Magnetic Field data: ${event.values.joinToString(", ")}")
                        System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                        hasMagnetometerData = true
                        checkInterference(event.values)
                        updateOrientation(this@callbackFlow)
                    }
                    else -> {
                        Timber.d("❓ Unknown sensor type: ${event.sensor.type}")
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                val status = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassStatus.NEEDS_CALIBRATION
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassStatus.NEEDS_CALIBRATION
                    else -> CompassStatus.OK
                }
                if (_orientationState.value is OrientationState.Available) {
                    val currentState = _orientationState.value as OrientationState.Available
                    _orientationState.value = currentState.copy(compassStatus = status)
                }
            }
        }

        // Use accelerometer + magnetometer for proper compass implementation
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        Timber.d("🔍 Compass sensors - Accelerometer: ${accelerometer != null}, Magnetometer: ${magnetometer != null}")
        
        if (accelerometer == null || magnetometer == null) {
            Timber.e("❌ Required compass sensors not available")
            trySend(OrientationState.Initializing)
            awaitClose()
            return@callbackFlow
        }
        
        // Register both sensors for proper compass functionality
        val accelerometerRegistered = sensorManager.registerListener(
            sensorEventListener, 
            accelerometer, 
            SensorManager.SENSOR_DELAY_UI
        )
        val magnetometerRegistered = sensorManager.registerListener(
            sensorEventListener, 
            magnetometer, 
            SensorManager.SENSOR_DELAY_UI
        )
        
        Timber.d("📱 Compass sensor registration - Accelerometer: $accelerometerRegistered, Magnetometer: $magnetometerRegistered")
        
        if (!accelerometerRegistered || !magnetometerRegistered) {
            Timber.e("❌ Failed to register compass sensors")
            trySend(OrientationState.Initializing)
            awaitClose()
            return@callbackFlow
        }

        // Detect device orientation for proper coordinate mapping
        detectDeviceOrientation()

        // Send initial state
        trySend(OrientationState.Available(0f, CompassStatus.OK, 0f))

        awaitClose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    private fun updateOrientation(flow: kotlinx.coroutines.channels.SendChannel<OrientationState>) {
        if (!hasAccelerometerData || !hasMagnetometerData) {
            Timber.d("⚠️ Waiting for both accelerometer and magnetometer data")
            return
        }
        
        // Detect device orientation changes
        detectDeviceOrientation()
        
        // Check if phone is flat for accurate compass readings
        checkPhoneOrientation()
        
        try {
            Timber.d("🔄 Processing compass orientation...")
            
            // Calculate rotation matrix from accelerometer and magnetometer
            val success = SensorManager.getRotationMatrix(
                rotationMatrix, 
                null, 
                accelerometerReading, 
                magnetometerReading
            )
            
            if (!success) {
                Timber.d("⚠️ Failed to calculate rotation matrix")
                return
            }
            
            Timber.d("📐 Rotation matrix calculated successfully")
            
            // Remap coordinate system for compass applications based on device orientation
            // For compass, we need to align the device's forward direction with world coordinates
            val remapSuccess = when (deviceOrientation) {
                90 -> {
                    // Landscape mode - device Y-axis (forward) maps to world X-axis
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remappedRotationMatrix
                    )
                }
                270 -> {
                    // Reverse landscape mode - device Y-axis (forward) maps to world -X-axis
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_Y,
                        SensorManager.AXIS_X,
                        remappedRotationMatrix
                    )
                }
                else -> {
                    // Portrait mode (0 or 180) - device Y-axis is forward direction
                    // For compass applications, we need to align device Y-axis with world coordinates
                    // Try using the original rotation matrix without remapping
                    System.arraycopy(rotationMatrix, 0, remappedRotationMatrix, 0, 9)
                    true
                }
            }
            
            if (!remapSuccess) {
                Timber.d("⚠️ Failed to remap coordinate system")
                return
            }
            
            Timber.d("🔄 Coordinate system remapped for compass")
            
            // Get orientation angles (azimuth, pitch, roll)
            SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)
            Timber.d("📊 Orientation angles - Azimuth: ${orientationAngles[0]}°, Pitch: ${orientationAngles[1]}°, Roll: ${orientationAngles[2]}°")
            Timber.d("📊 Device orientation: $deviceOrientation°, Remap success: $remapSuccess")
            
            // Convert azimuth to degrees and normalize
            // For compass applications, azimuth represents the angle from magnetic north
            // The azimuth from getOrientation() is in radians and represents the angle from north
            val magneticAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val magneticHeading = (magneticAzimuth + 360) % 360
            Timber.d("🧭 Magnetic azimuth: $magneticAzimuth°, Normalized heading: $magneticHeading°")
            
            // Apply magnetic declination correction
            val declination = getMagneticDeclination()
            val trueHeading = (magneticHeading + declination + 360) % 360
            Timber.d("🌍 Magnetic declination: $declination°, True heading: $trueHeading°")
            
            // Improved low-pass filter for smoother movement
            val alpha = 0.15f
            val smoothedHeading = lastHeading + alpha * (trueHeading - lastHeading)
            lastHeading = smoothedHeading
            Timber.d("🎯 Smoothed heading: $smoothedHeading° (last: $lastHeading°)")
            
            // Apply calibration offset
            currentCalibratedHeading = (smoothedHeading + calibrationOffset.toFloat() + 360) % 360
            Timber.d("🎯 Final calibrated heading: $currentCalibratedHeading°")
            
            val currentStatus = (_orientationState.value as? OrientationState.Available)?.compassStatus ?: CompassStatus.OK
            val currentProgress = (_orientationState.value as? OrientationState.Available)?.calibrationProgress ?: 0f
            
            // Update calibration progress if calibrating
            val updatedProgress = if (isCalibrating) {
                updateCalibrationProgress()
            } else {
                currentProgress
            }
            
            val newState = OrientationState.Available(
                trueHeading = currentCalibratedHeading, 
                compassStatus = currentStatus,
                calibrationProgress = updatedProgress,
                isPhoneFlat = isPhoneFlat,
                isPhoneVertical = isPhoneVertical,
                phoneTiltAngle = phoneTiltAngle
            )
            
            _orientationState.value = newState
            
            // Emit the updated state to the Flow
            flow.trySend(newState)
            
            Timber.d("✅ Orientation state updated and emitted: $currentCalibratedHeading°, Status: $currentStatus, Phone Flat: $isPhoneFlat, Phone Vertical: $isPhoneVertical")
            
        } catch (e: Exception) {
            // Handle any errors in sensor processing
            Timber.e(e, "❌ Error processing sensor data")
        }
    }

    private fun detectDeviceOrientation() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        
        val newOrientation = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        
        // Only update if orientation changed
        if (deviceOrientation != newOrientation) {
            deviceOrientation = newOrientation
            Timber.d("📱 Device orientation changed to: $deviceOrientation°")
            
            // Adjust coordinate remapping based on device orientation
            when (deviceOrientation) {
                90 -> {
                    // Landscape mode - adjust coordinate system
                    Timber.d("🔄 Adjusting for landscape mode")
                }
                270 -> {
                    // Reverse landscape mode - adjust coordinate system
                    Timber.d("🔄 Adjusting for reverse landscape mode")
                }
                else -> {
                    // Portrait mode - default coordinate system
                    Timber.d("🔄 Using default portrait coordinate system")
                }
            }
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
     * Starts the calibration process
     */
    fun startCalibration() {
        isCalibrating = true
        calibrationStartTime = System.currentTimeMillis()
        calibrationMoves = 0
        lastCalibrationMove = 0
        lastCalibrationHeading = currentCalibratedHeading
        
        // Reset figure-8 tracking
        movementHistory.clear()
        totalRotation = 0f
        directionChanges = 0
        lastMovementDirection = 0f
        
        Timber.d("🎯 Starting compass calibration with figure-8 movement detection")
    }
    
    /**
     * Stops the calibration process
     */
    fun stopCalibration() {
        isCalibrating = false
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
    
    /**
     * Updates calibration progress based on figure-8 movement detection
     */
    private fun updateCalibrationProgress(): Float {
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - calibrationStartTime
        
        // Track movement history (keep last 20 readings)
        if (movementHistory.size >= 20) {
            movementHistory.removeAt(0)
        }
        movementHistory.add(currentCalibratedHeading)
        
        // Calculate movement direction and detect direction changes
        if (movementHistory.size >= 2) {
            val currentDirection = currentCalibratedHeading - lastCalibrationHeading
            val normalizedDirection = when {
                currentDirection > 180f -> currentDirection - 360f
                currentDirection < -180f -> currentDirection + 360f
                else -> currentDirection
            }
            
            // Detect direction changes (figure-8 pattern)
            if (lastMovementDirection != 0f) {
                val directionChange = normalizedDirection * lastMovementDirection
                if (directionChange < 0f) { // Direction changed
                    directionChanges++
                    Timber.d("🎯 Direction change detected: $directionChanges changes")
                }
            }
            
            lastMovementDirection = normalizedDirection
            totalRotation += kotlin.math.abs(normalizedDirection)
        }
        
        // Detect significant movements for figure-8 pattern
        val headingChange = kotlin.math.abs(currentCalibratedHeading - lastCalibrationHeading)
        if (headingChange > 10f && currentTime - lastCalibrationMove > 500) {
            calibrationMoves++
            lastCalibrationMove = currentTime
            lastCalibrationHeading = currentCalibratedHeading
            Timber.d("🎯 Calibration move detected: $calibrationMoves moves, heading change: $headingChange°")
        }
        
        // Calculate progress based on figure-8 movement criteria
        val rotationProgress = (totalRotation / 720f).coerceAtMost(1f) // At least 2 full rotations (720°)
        val directionChangeProgress = (directionChanges / 8f).coerceAtMost(1f) // At least 8 direction changes
        val moveProgress = (calibrationMoves / 20f).coerceAtMost(1f) // At least 20 significant movements
        val timeProgress = (timeElapsed / 30000f).coerceAtMost(1f) // 30 seconds max
        
        // Weight the progress: 40% rotation, 30% direction changes, 20% moves, 10% time
        val progress = (rotationProgress * 0.4f + directionChangeProgress * 0.3f + moveProgress * 0.2f + timeProgress * 0.1f).coerceAtMost(1f)
        
        Timber.d("🎯 Calibration progress: ${(progress * 100).toInt()}% (rotation: ${(rotationProgress * 100).toInt()}%, direction changes: ${(directionChangeProgress * 100).toInt()}%, moves: ${(moveProgress * 100).toInt()}%, time: ${(timeProgress * 100).toInt()}%)")
        
        return progress
    }
}