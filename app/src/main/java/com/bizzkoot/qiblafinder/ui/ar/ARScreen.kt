package com.bizzkoot.qiblafinder.ui.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bizzkoot.qiblafinder.model.NOT_FLAT_TILT_MAX_DEGREES
import com.bizzkoot.qiblafinder.model.NOT_FLAT_TILT_MIN_DEGREES
import com.bizzkoot.qiblafinder.ui.ar.ARErrorScreen
import com.bizzkoot.qiblafinder.ui.ar.ARErrorType
import com.bizzkoot.qiblafinder.ui.compass.WaterLevelIndicator
import com.google.ar.sceneform.ArSceneView
import timber.log.Timber

@Composable
fun ARScreen(
    viewModel: ARViewModel,
    onNavigateBack: () -> Unit,
    onCalibrateClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val arCoreAvailability by viewModel.arCoreAvailability.collectAsState()
    val session by viewModel.session.collectAsState()
    val error by viewModel.error.collectAsState()

    // W3/W5: lifecycle gate mirroring CompassScreen — stop the sensor + location
    // collections whenever this route is not RESUMED, and release the GPS callback
    // when the app is fully backgrounded. Seeded from the current lifecycle state so
    // a restored entry that is already covered starts with sensors gated off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenVisible(false)
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onScreenVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.createSession()
        }
    }
    
    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            context.checkSelfPermission(Manifest.permission.CAMERA) -> {
                viewModel.createSession()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            arCoreAvailability == com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED && session != null -> {
                ARView(
                    session = session!!,
                    onNavigateBack = onNavigateBack,
                    viewModel = viewModel
                )
            }
            error != null -> {
                // Show error screen with appropriate error type
                val errorType = when {
                    error!!.contains("NOT_SUPPORTED") -> ARErrorType.NOT_SUPPORTED
                    error!!.contains("PERMISSION_DENIED") -> ARErrorType.PERMISSION_DENIED
                    else -> ARErrorType.SESSION_FAILED
                }
                
                ARErrorScreen(
                    errorType = errorType,
                    onRetry = { viewModel.createSession() },
                    onNavigateBack = onNavigateBack,
                    onCalibrateClicked = onCalibrateClicked
                )
            }
            else -> {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Initializing AR...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ARView(
    session: com.google.ar.core.Session,
    onNavigateBack: () -> Unit,
    viewModel: ARViewModel
) {
    var sceneView by remember { mutableStateOf<ArSceneView?>(null) }
    val qiblaDirection by viewModel.qiblaDirection.collectAsState()
    val isAligned by viewModel.isAligned.collectAsState()
    val phoneTiltAngle by viewModel.phoneTiltAngle.collectAsState()
    val isPhoneUpright by viewModel.isPhoneUpright.collectAsState()
    val levelX by viewModel.levelX.collectAsState()
    val levelY by viewModel.levelY.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview Background
        CameraPreview()
        
        // AR Scene View (transparent overlay)
        AndroidView(
            factory = { context ->
                ArSceneView(context).also { view ->
                    val arSession = session
                    // Set up the AR scene view
                    sceneView = view
                    
                    // Configure AR session
                    val arConfig = com.google.ar.core.Config(arSession).apply {
                        lightEstimationMode = com.google.ar.core.Config.LightEstimationMode.AMBIENT_INTENSITY
                        planeFindingMode = com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL
                        focusMode = com.google.ar.core.Config.FocusMode.AUTO
                        updateMode = com.google.ar.core.Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                    arSession.configure(arConfig)
                    
                    Timber.d("ARScreenKt\$ARView: AR Scene initialized successfully")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Qibla Direction Overlay
        QiblaDirectionOverlay(
            qiblaDirection = qiblaDirection,
            isAligned = isAligned,
            phoneTiltAngle = phoneTiltAngle,
            isPhoneUpright = isPhoneUpright,
            levelX = levelX,
            levelY = levelY,
            onNavigateBack = onNavigateBack
        )
        
        // Floating Action Button for placing Qibla marker
        FloatingActionButton(
            onClick = {
                // Place Qibla direction marker
                placeQiblaMarker(sceneView, qiblaDirection)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Place Qibla Marker",
                tint = Color.White
            )
        }
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    Timber.d("Camera preview started successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to bind camera preview")
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun QiblaDirectionOverlay(
    qiblaDirection: Float,
    isAligned: Boolean,
    phoneTiltAngle: Float,
    isPhoneUpright: Boolean,
    levelX: Float,
    levelY: Float,
    onNavigateBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Top Bar with Back Button and Status
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isAligned) "✅ Qibla Found!" else "🔍 Finding Qibla...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAligned) Color.Green else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Face this direction to pray",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isAligned) Color.Green 
                            else Color(0xFFFF9800) // Orange color
                        )
                )
            }
        }
        
        // "Phone not flat" warning: the AR compass reading is only accurate with the
        // phone lying flat. Uses the same 65..115° tilt band as the compass red alert
        // (NOT_FLAT_TILT_MIN/MAX_DEGREES, shared from SensorRepository) so the check
        // is self-documenting and cannot drift from the detector.
        if (phoneTiltAngle >= NOT_FLAT_TILT_MIN_DEGREES && phoneTiltAngle <= NOT_FLAT_TILT_MAX_DEGREES) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 96.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB3261E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Phone not flat warning",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Phone not flat — lay the phone flat for accurate reading (tilt ${phoneTiltAngle.toInt()}°)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Center Direction Indicator
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Large Qibla arrow
            Card(
                modifier = Modifier.size(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 🕋 Kaaba icon at top
                        Text(
                            text = "🕋",
                            fontSize = 32.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Direction arrow (rotated based on compass data)
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Qibla Direction",
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer(rotationZ = qiblaDirection),
                            tint = if (isAligned) Color.Green else Color.Red
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Additional direction indicator
                        Text(
                            text = if (isAligned) "✅ FACE THIS DIRECTION" else "➡️ TURN THIS WAY",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAligned) Color.Green else Color.Red
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Direction text (simplified)
                        Text(
                            text = "Turn this way",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Compact level cue below the AR direction card. It uses the same
        // sensor-backed flatness model as the compass screen.
        WaterLevelIndicator(
            levelX = levelX,
            levelY = levelY,
            modifier = Modifier.align(Alignment.Center).offset(y = 126.dp)
        )

        // Bottom Instructions
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📱 AR Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "1. Lay phone FLAT (see warning if tilted)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "2. Rotate phone until arrow points UP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "3. When arrow turns GREEN, you're aligned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "4. Face the direction shown to pray",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        

    }
}

private fun placeQiblaMarker(sceneView: ArSceneView?, qiblaDirection: Float) {
    // Implementation for placing AR marker
    // This would create a 3D arrow or marker in AR space
    Timber.d("📍 Placing Qibla marker at direction: ${qiblaDirection}°")
}
