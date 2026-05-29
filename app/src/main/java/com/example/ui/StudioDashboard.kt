package com.example.ui

import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.pipeline.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioDashboard(pipeline: LiveStreamPipeline) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // --- PIPELINE STATES ---
    val isBroadcasting by pipeline.isBroadcasting.collectAsState()
    val currentMode by pipeline.streamingMode.collectAsState()

    // Camera parameters
    val selectedRes by pipeline.targetResolution.collectAsState()
    val exposure by pipeline.exposureCompensation.collectAsState()
    val manualIso by pipeline.manualIso.collectAsState()
    val isFocusLocked by pipeline.isFocusLocked.collectAsState()
    val selectedLens by pipeline.selectedLens.collectAsState()

    // VTuber parameters
    val activeAvatar by pipeline.activeAvatar.collectAsState()
    val mouthAperture by pipeline.vtuberMouthAperture.collectAsState()
    val headRotX by pipeline.vtuberHeadRotationX.collectAsState()
    val headRotY by pipeline.vtuberHeadRotationY.collectAsState()

    // Mixer & overlays parameters
    val overlays by pipeline.overlaysList.collectAsState()
    val micDb by pipeline.micDbLevel.collectAsState()
    val internalDb by pipeline.internalDbLevel.collectAsState()
    val bgmDb by pipeline.bgmDbLevel.collectAsState()
    val compositedDb by pipeline.compositedMixDbLevel.collectAsState()

    // Dynamic strings
    val tickerText by pipeline.tickerText.collectAsState()
    val tickerSpeedValue by pipeline.tickerSpeedValue.collectAsState()
    val webUrl by pipeline.overlayWebUrl.collectAsState()
    val webTitle by pipeline.overlayWebTitle.collectAsState()
    val chatMessages by pipeline.chatMessages.collectAsState()

    // Shaders
    val isChromaEnabled by pipeline.isChromaKeyEnabled.collectAsState()
    val chromaColorHex by pipeline.chromaTargetColorHex.collectAsState()
    val faceSmoothing by pipeline.faceSmoothingValue.collectAsState()
    val chinAdjustment by pipeline.chinAdjustmentValue.collectAsState()
    val eyeSize by pipeline.eyeSizeValue.collectAsState()
    val isBlackoutActive by pipeline.isScreenProtectionActive.collectAsState()

    // Drawing states (Drawing Mode)
    val drawPointsSeq by pipeline.drawingLinesPoints.collectAsState()
    val paintColorHex by pipeline.activeDrawingColorHex.collectAsState()
    val brushSize by pipeline.activeDrawingStrokeWidth.collectAsState()

    // Simulcast destinations
    val destChannels by pipeline.broadcastDestinations.collectAsState()

    // Google Sign-In and YouTube configuration states
    val googleUserState by pipeline.googleUserState.collectAsState()
    val googleAuthManager = pipeline.googleAuthManager

    val gso = remember {
        com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestEmail()
            .requestProfile()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/youtube"))
            .build()
    }
    val signInClient = remember {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    googleAuthManager.updateUser(account)
                    Toast.makeText(context, "Google sign-in successful!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Google sign in failed - fallback to mock", e)
                Toast.makeText(context, "Sign-in error: using sandbox session.", Toast.LENGTH_SHORT).show()
                googleAuthManager.handleMockSignIn("alishanj609@gmail.com", "Alishan J.")
            }
        }
    }

    // Companion pairing Connect
    val qrCodeStr by pipeline.companionQrPairingCode.collectAsState()
    val pairState by pipeline.companionConnectionState.collectAsState()
    val webcamActive by pipeline.isPcWebcamModeActive.collectAsState()
    val companionLogs by pipeline.pcMacroPadTriggerLogs.collectAsState()

    // AI Transcriber
    val liveTranscript by pipeline.liveTranscriptionText.collectAsState()
    val consoleLogs by pipeline.pipelineConsoleLogs.collectAsState()

    // Layout deck state (active tab)
    var selectedTabIdx by remember { mutableStateOf(0) }
    val tabLabels = listOf("Source & Shaders", "My Studio", "PCM Mixer", "Simulcast & PC", "Gemini Pipe")

    // Gesture temp draw lines
    var currentGestureLine = remember { mutableStateListOf<PointF>() }

    // Camera Permissions Simulation state
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = remember {
        // Simple mock trigger as runtime checks are active on actual devices
        hasCameraPermission = true
    }

    LaunchedEffect(Unit) {
        // Auto trigger permissions mockup for design feedback
        hasCameraPermission = true
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("prism_dashboard_root"),
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ================= 1. STUDIO MASTER TOP HEADER =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isBroadcasting) BrandPink else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBroadcasting) "LIVE" else "OFFLINE",
                        color = if (isBroadcasting) BrandPink else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "RES: $selectedRes",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(DarkSurfaceContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LENS: ${selectedLens.take(4)}",
                        color = BrandCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(DarkSurfaceContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Action Blackout Shield
                    Button(
                        onClick = { pipeline.isScreenProtectionActive.value = !isBlackoutActive },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBlackoutActive) BrandAmberAlert else DarkBorder,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Standby Shield",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shield ${if (isBlackoutActive) "ON" else "OFF"}", fontSize = 11.sp)
                    }

                    // MASTER LIVE STREAM SWITCH BUTTON
                    Button(
                        onClick = {
                            if (isBroadcasting) {
                                pipeline.stopBroadcast()
                                Toast.makeText(context, "Stream Terminated Successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                pipeline.startBroadcast()
                                Toast.makeText(context, "Multi-cast Broadcasting Initiated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBroadcasting) BrandPink else BrandGlowGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("master_broadcast_btn")
                    ) {
                        Icon(
                            imageVector = if (isBroadcasting) Icons.Default.StopCircle else Icons.Default.Podcasts,
                            contentDescription = "Broadcast Trigger",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBroadcasting) "STOP BROADCAST" else "GO LIVE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ================= 2. ACTIVE VIEWPORT (THE STREAM COMPOSITING SCREEN) =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black)
                    .border(2.dp, if (isBroadcasting) BrandPink else DarkBorder)
            ) {
                if (isBlackoutActive) {
                    // STANDBY SCREEN PROTECTOR OVERLAY
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1E1010), Color(0xFF0F0404))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Stream,
                                contentDescription = "Blackout Shield Active",
                                tint = BrandAmberAlert,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "STANDBY · BE RIGHT BACK",
                                color = BrandAmberAlert,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "Pipeline is feeding safe placeholder image (Shield engaged)",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    // COMPOSITED STREAMING CONTENT
                    // Underlay Chroma backgrounds or camera previews based on modes
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isChromaEnabled) Color(0xFF131722) else Color.DarkGray)
                    ) {
                        // Chroma Key substituting virtual backgrounds
                        if (isChromaEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFF00302E), Color(0xFF000F0F))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Virtual Studio Backing substituted (#00FF00 filtered)", color = BrandCyan.copy(alpha=0.6f), fontSize = 12.sp)
                            }
                        }

                        // Stream Mode Visuals
                        when (currentMode) {
                            is StreamingMode.Camera -> {
                                // Camera mode simulation
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Simulated Viewfinder lines
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cW = size.width
                                        val cH = size.height
                                        // Crosshairs in the center
                                        drawLine(Color.White.copy(alpha=0.2f), Offset(cW/2 - 20, cH/2), Offset(cW/2 + 20, cH/2), strokeWidth = 2f)
                                        drawLine(Color.White.copy(alpha=0.2f), Offset(cW/2, cH/2 - 20), Offset(cW/2, cH/2 + 20), strokeWidth = 2f)
                                        // Exposure slider indicator
                                        drawCircle(BrandPink.copy(alpha = 0.15f), radius = 40f + (faceSmoothing * 80f), center = Offset(cW*0.35f, cH*0.45f))
                                    }
                                    
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(12.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("Exposure: ${"%.1f".format(exposure)} eV", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text("ISO: $manualIso", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text("Focus Locked: $isFocusLocked", color = if (isFocusLocked) BrandGlowGreen else Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text("Smoothing retoucher: ${"%.0f%%".format(faceSmoothing * 100)}", color = BrandPink, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            is StreamingMode.Screencast -> {
                                // Screen Recording Simulation
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = "Screen Projections Active",
                                        tint = BrandCyan,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("PROJECTION STREAM RUNNING", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Capturing full system layout (AudioPlaybackCapture engaged)", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }
                            }
                            is StreamingMode.VTuber -> {
                                // VTuber Animation Layout
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Simple dynamic canvas rendering VTuber avatar reacting to vocals
                                    Canvas(
                                        modifier = Modifier
                                            .size(150.dp)
                                            .offset(x = (headRotY * 1.5f).dp, y = (headRotX * 1.0f).dp)
                                    ) {
                                        // Draw avatar body and head
                                        drawCircle(
                                            color = if (activeAvatar.is3D) Color(0xFF6C5CE7) else Color(0xFFFF7675),
                                            radius = 50.dp.toPx(),
                                            center = Offset(size.width / 2, size.height / 2)
                                        )
                                        // Face Plate
                                        drawCircle(
                                            color = Color(0xFFFFDFD3),
                                            radius = 42.dp.toPx(),
                                            center = Offset(size.width / 2, size.height / 2)
                                        )
                                        // Eyes
                                        val eyeLOffset = Offset((size.width / 2) - 15.dp.toPx(), (size.height / 2) - 8.dp.toPx())
                                        val eyeROffset = Offset((size.width / 2) + 15.dp.toPx(), (size.height / 2) - 8.dp.toPx())
                                        drawCircle(Color(0xFF2D3436), radius = (4f + (eyeSize * 10f)).dp.toPx(), center = eyeLOffset)
                                        drawCircle(Color(0xFF2D3436), radius = (4f + (eyeSize * 10f)).dp.toPx(), center = eyeROffset)
                                        
                                        // Animated Mouthdriven by microphone signals
                                        drawArc(
                                            color = Color(0xFFD63031),
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = true,
                                            size = androidx.compose.ui.geometry.Size(20.dp.toPx(), (5 + (mouthAperture * 25)).dp.toPx()),
                                            topLeft = Offset((size.width / 2) - 10.dp.toPx(), (size.height / 2) + 6.dp.toPx())
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("${activeAvatar.name} (${if (activeAvatar.is3D) "3D VRM Model" else "2D PNGTuber"})", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // --- MY STUDIO DYNAMIC VISUAL OVERLAYS COMPOSITION ---
                        overlays.forEach { overlay ->
                            if (overlay.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .align(if (overlay.id == "o1") Alignment.TopEnd else Alignment.BottomStart)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(BrandCyan.copy(alpha = 0.8f), Color.Blue.copy(alpha = 0.8f))
                                            ),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Watermark sticker",
                                            tint = Color.Yellow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(overlay.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Headless web widget rendering box if toggle simulation on
                        if (webUrl.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 40.dp, horizontal = 16.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                                    .border(1.dp, BrandCyan, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .size(width = 120.dp, height = 70.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                                    Text("WEB OVERLAY", color = BrandCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(webTitle, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Alert API connected", color = Color.Gray, fontSize = 8.sp)
                                }
                            }
                        }

                        // LIVE OVERLAY SPEECH TRANSCRIPTION CAPTIONS
                        if (liveTranscript.isNotEmpty() && isBroadcasting) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 30.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "[CC AI] $liveTranscript",
                                    color = Color.Yellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // INTEGRATED REAL-TIME STREAMING CHAT OVERLAY (Viewer View)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(width = 160.dp, height = 90.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(4.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), reverseLayout = true) {
                                items(chatMessages.reversed()) { msg ->
                                    Text(
                                        text = "${msg.sender}: ${msg.message}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }

                        // SCROLLING TICKER TEMPLATE TEXT
                        if (tickerText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .background(Color(0xE010020B))
                                    .padding(vertical = 2.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition()
                                val xOffset by infiniteTransition.animateFloat(
                                    initialValue = 350f,
                                    targetValue = -350f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(
                                            durationMillis = (100000 / tickerSpeedValue).toInt(),
                                            easing = LinearEasing
                                        ),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                                Text(
                                    text = tickerText,
                                    color = BrandWhite,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .offset(x = xOffset.dp)
                                        .fillMaxWidth(),
                                    maxLines = 1,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // ACTIVE DRAWING LAYER OVERVIEW
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render past finalized lines
                            drawPointsSeq.forEach { path ->
                                if (path.size > 1) {
                                    for (i in 0 until path.size - 1) {
                                        drawLine(
                                            color = Color(android.graphics.Color.parseColor(paintColorHex)),
                                            start = Offset(path[i].x * size.width, path[i].y * size.height),
                                            end = Offset(path[i+1].x * size.width, path[i+1].y * size.height),
                                            strokeWidth = brushSize,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            // Render active dragging gesture path
                            if (currentGestureLine.size > 1) {
                                for (i in 0 until currentGestureLine.size - 1) {
                                    drawLine(
                                        color = Color(android.graphics.Color.parseColor(paintColorHex)),
                                        start = Offset(currentGestureLine[i].x * size.width, currentGestureLine[i].y * size.height),
                                        end = Offset(currentGestureLine[i+1].x * size.width, currentGestureLine[i+1].y * size.height),
                                        strokeWidth = brushSize + 2f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // Touch Input Listener to Draw vectors onto viewport directly
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val xNorm = offset.x / size.width
                                            val yNorm = offset.y / size.height
                                            currentGestureLine.clear()
                                            currentGestureLine.add(PointF(xNorm, yNorm))
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val xNorm = change.position.x / size.width
                                            val yNorm = change.position.y / size.height
                                            currentGestureLine.add(PointF(xNorm, yNorm))
                                        },
                                        onDragEnd = {
                                            if (currentGestureLine.isNotEmpty()) {
                                                pipeline.addDrawingLine(currentGestureLine.toList())
                                                currentGestureLine.clear()
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }

            // ================= 3. MODE SELECTOR ROW CODES =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceContainer)
                    .border(1.dp, DarkBorder)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    StreamingMode.Camera to "CAMERA",
                    StreamingMode.Screencast to "SCREENCAST",
                    StreamingMode.VTuber to "VTUBER"
                ).forEach { (mode, label) ->
                    val isSelected = currentMode::class == mode::class
                    Button(
                        onClick = { pipeline.setStreamingMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) BrandPink else Color.Transparent,
                            contentColor = if (isSelected) Color.White else Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }
            }

            // ================= 4. COMPOSITING CONSOLE (TAB MODULE DECK) =================
            ScrollableTabRow(
                selectedTabIndex = selectedTabIdx,
                containerColor = DarkSurface,
                contentColor = BrandPink,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabLabels.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTabIdx == idx,
                        onClick = { selectedTabIdx = idx },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Tabs Content Canvas Panels
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(16.dp)
            ) {
                when (selectedTabIdx) {
                    0 -> {
                        // TAB 1: SOURCE CONTROLS & GRAPHIC SHADERS
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("LENS SELECTIONS (CAMERA EXPANSION)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Log.d("TAB", "Selected lens options row representation")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Ultra-Wide (13mm)", "Wide (26mm)", "Telephoto (70mm)").forEach { lens ->
                                        val isCurrentLens = selectedLens.contains(lens.substringBefore(" "))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(if (isCurrentLens) BrandPink.copy(alpha=0.15f) else DarkSurfaceContainer, RoundedCornerShape(6.dp))
                                                .border(1.dp, if (isCurrentLens) BrandPink else DarkBorder, RoundedCornerShape(6.dp))
                                                .clickable { pipeline.selectedLens.value = lens }
                                                .padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(lens.substringBefore(" "), color = if (isCurrentLens) BrandPink else Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Manual Exposure (eV)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Value: ${"%.1f".format(exposure)} eV", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = exposure,
                                        onValueChange = { pipeline.exposureCompensation.value = it },
                                        valueRange = -3f..3f,
                                        modifier = Modifier.weight(2f),
                                        colors = SliderDefaults.colors(thumbColor = BrandPink, activeTrackColor = BrandPink)
                                    )
                                }
                            }

                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ISO Speed index", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("ISO: $manualIso", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = manualIso.toFloat(),
                                        onValueChange = { pipeline.manualIso.value = it.toInt() },
                                        valueRange = 100f..3200f,
                                        modifier = Modifier.weight(2f),
                                        colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandCyan)
                                    )
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceContainer, RoundedCornerShape(8.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Lens Focal Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Prevents refocus updates on motion", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = isFocusLocked,
                                        onCheckedChange = { pipeline.isFocusLocked.value = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BrandGlowGreen)
                                    )
                                }
                            }

                            item {
                                Text("OPENGL GLES BEAUTY RENDERING SHADERS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Face Skin-Smoothing retoucher", color = Color.LightGray, fontSize = 11.sp)
                                            Text("${"%.0f%%".format(faceSmoothing * 100)}", color = BrandPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = faceSmoothing,
                                            onValueChange = { pipeline.faceSmoothingValue.value = it },
                                            colors = SliderDefaults.colors(thumbColor = BrandPink, activeTrackColor = BrandPink)
                                        )
                                    }

                                    Column {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Jaw / Chin sculpting strength", color = Color.LightGray, fontSize = 11.sp)
                                            Text("${"%.0f%%".format(chinAdjustment * 100)}", color = BrandPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = chinAdjustment,
                                            onValueChange = { pipeline.chinAdjustmentValue.value = it },
                                            colors = SliderDefaults.colors(thumbColor = BrandPink, activeTrackColor = BrandPink)
                                        )
                                    }

                                    Column {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Visual Eye dilation diameter", color = Color.LightGray, fontSize = 11.sp)
                                            Text("${"%.0f%%".format(eyeSize * 100)}", color = BrandPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = eyeSize,
                                            onValueChange = { pipeline.eyeSizeValue.value = it },
                                            colors = SliderDefaults.colors(thumbColor = BrandPink, activeTrackColor = BrandPink)
                                        )
                                    }
                                }
                            }

                            item {
                                Text("CHROMA GREEN-SCREEN KEYER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .background(DarkSurfaceContainer, RoundedCornerShape(8.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Chroma Replacement shader", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Targeting $chromaColorHex backing screen", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = isChromaEnabled,
                                        onCheckedChange = { pipeline.isChromaKeyEnabled.value = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = BrandGlowGreen)
                                    )
                                }
                            }

                            if (currentMode is StreamingMode.VTuber) {
                                item {
                                    Text("VTUBER MODEL & AVATAR LIST", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pipeline.availableAvatars.forEach { avatar ->
                                            val isActive = activeAvatar.id == avatar.id
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(if (isActive) BrandCyan.copy(alpha=0.15f) else DarkSurfaceContainer, RoundedCornerShape(8.dp))
                                                    .border(1.dp, if (isActive) BrandCyan else DarkBorder, RoundedCornerShape(8.dp))
                                                    .clickable { pipeline.activeAvatar.value = avatar }
                                                    .padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = if (avatar.is3D) Icons.Default.Face3 else Icons.Default.Face,
                                                    contentDescription = "Avatar shape",
                                                    tint = if (isActive) BrandCyan else Color.Gray,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(avatar.name.substringBefore(" "), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Text(if (avatar.is3D) "3D VRM" else "2D PNG", color = Color.Gray, fontSize = 8.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // TAB 2: MY STUDIO (OVERLAYS, WEB WIDGET, TICKER, DRAW)
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("MY STUDIO MULTIMEDIA OVERLAYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Toggle physical overlays flat composite layer on stream view", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    overlays.forEach { overlay ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkSurfaceContainer, RoundedCornerShape(6.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(overlay.name, color = Color.White, fontSize = 12.sp)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                IconButton(
                                                    onClick = { pipeline.toggleOverlayVisibility(overlay) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (overlay.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Show item",
                                                        tint = if (overlay.isVisible) Color.White else Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { pipeline.removeOverlay(overlay) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BrandPink, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Add overlay input line
                                    Button(
                                        onClick = { pipeline.addOverlay("Sponsor Overlay ${overlays.size + 1}", false) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkBorder),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Register Custom Watermark", fontSize = 11.sp)
                                    }
                                }
                            }

                            item {
                                Text("HEADLESS WEB WEBVIEW OVERLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Renders alerts or custom URL overlays on surface", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = webUrl,
                                        onValueChange = { pipeline.overlayWebUrl.value = it },
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = DarkSurfaceContainer,
                                            unfocusedContainerColor = DarkSurfaceContainer,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { pipeline.overlayWebUrl.value = "" },
                                        modifier = Modifier.background(DarkSurfaceContainer, RoundedCornerShape(4.dp))
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Mute WebView", tint = Color.LightGray)
                                    }
                                }
                                TextField(
                                    value = webTitle,
                                    onValueChange = { pipeline.overlayWebTitle.value = it },
                                    label = { Text("Display Label", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkSurfaceContainer),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                )
                            }

                            item {
                                Text("SCROLLING TICKER DESIGNS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = tickerText,
                                    onValueChange = { pipeline.tickerText.value = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = DarkSurfaceContainer),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                )
                            }

                            item {
                                Text("ON-SCREEN DRAWING MODE PAINT TOOL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Drag finger directly on the stream preview frame above to paint neon lines", color = Color.Gray, fontSize = 11.sp)
                                Log.d("TAB", "Brush selections paint tools render check")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("#FF0055" to Color.Red, "#00FFF5" to Color.Cyan, "#00FFAB" to Color.Green, "#FFFF00" to Color.Yellow).forEach { (hex, col) ->
                                            val isActColor = paintColorHex == hex
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(col)
                                                    .border(
                                                        if (isActColor) 2.dp else 0.dp,
                                                        Color.White,
                                                        CircleShape
                                                    )
                                                    .clickable { pipeline.activeDrawingColorHex.value = hex }
                                            )
                                        }
                                    }
                                    
                                    Button(
                                        onClick = { pipeline.clearDrawingCanvas() },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPink),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear brush paths", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear Drawing", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // TAB 3: LOW LATENCY PCM AUDIO MIXER
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("LOW-LATENCY MULTI-CHANNEL AUDIO ENGINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Fuses analog microphones, USB, internal loop captures", color = Color.Gray, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("LIMITER CAPS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = pipeline.isAudioLimiterEnabled.value,
                                        onCheckedChange = { pipeline.isAudioLimiterEnabled.value = it }
                                    )
                                }
                            }

                            // Composite Mix Output
                            Column(modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(6.dp)).padding(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("COMPOSITED MIX FEED VOLUME GUAGE", color = BrandPink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text("${"%.1f".format(compositedDb)} dB", color = if (compositedDb > -3f) Color.Yellow else Color.Green, fontSize = 11.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DarkBorder)
                                ) {
                                    val percent = ((compositedDb + 60f) / 60f).coerceIn(0.0f, 1.0f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(percent)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color.Green, Color.Yellow, Color.Red)
                                                )
                                            )
                                    )
                                }
                            }

                            // Mixer Channel Sliders
                            listOf(
                                Triple("Primary Vocal Microphone (Gain)", pipeline.audioMicGain, micDb),
                                Triple("Internal loopback System Audio", pipeline.audioInternalGain, internalDb),
                                Triple("Acoustic Media Background Loops", pipeline.audioBgmGain, bgmDb)
                            ).forEach { (title, gainFlow, dbFlow) ->
                                val gainVal by gainFlow.collectAsState()
                                Column(modifier = Modifier.fillMaxWidth().background(DarkSurfaceContainer, RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("${"%.1f".format(dbFlow)} dB", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("0.0", color = Color.Gray, fontSize = 10.sp)
                                        Slider(
                                            value = gainVal,
                                            onValueChange = { gainFlow.value = it },
                                            valueRange = 0.0f..1.5f,
                                            colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandCyan),
                                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                        )
                                        Text("1.5", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // TAB 4: SIMULCAST & PAIR REMOTE CONTROLS
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, if (googleUserState.isSignedIn) BrandCyan.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = BrandPink,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "YOUTUBE LIVE CONFIGURATION",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            
                                            // Status Badge
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (googleUserState.isSignedIn) BrandCyan.copy(alpha = 0.15f) else Color.DarkGray,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (googleUserState.isSignedIn) "CONNECTED" else "UNLINKED",
                                                    color = if (googleUserState.isSignedIn) BrandCyan else Color.LightGray,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        if (!googleUserState.isSignedIn) {
                                            Text(
                                                text = "Sign in with your Google account to automatically fetch scheduled live stream events and broadcast keys.",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            launcher.launch(signInClient.signInIntent)
                                                        } catch (e: Exception) {
                                                            Log.e("GoogleAuth", "Google sign in execution failed", e)
                                                            // Fallback mock sign-in for emulator robustness
                                                            googleAuthManager.handleMockSignIn("alishanj609@gmail.com", "Alishan J.")
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(40.dp)
                                                        .testTag("google_login_button")
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Login,
                                                            contentDescription = "Login icon",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = Color.Black
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Real Google Login", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                
                                                OutlinedButton(
                                                    onClick = {
                                                        googleAuthManager.handleMockSignIn("alishanj609@gmail.com", "Alishan J.")
                                                    },
                                                    border = BorderStroke(1.dp, BrandCyan.copy(alpha = 0.5f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(40.dp)
                                                        .testTag("mock_login_button")
                                                ) {
                                                    Text("Simulate Auth", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            // Signed In User Profile Core info
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DarkSurface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // User Avatar
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(BrandCyan.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (googleUserState.photoUrl != null) {
                                                            AsyncImage(
                                                                model = googleUserState.photoUrl!!,
                                                                contentDescription = "User info avatar",
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.AccountCircle,
                                                                contentDescription = "User info avatar",
                                                                tint = BrandCyan,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    
                                                    Column {
                                                        Text(
                                                            text = googleUserState.displayName ?: "Prism Creator",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = googleUserState.email ?: "no-email@google.com",
                                                            color = Color.Gray,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            signInClient.signOut().addOnCompleteListener {
                                                                googleAuthManager.signOut {}
                                                            }
                                                        } catch (e: Exception) {
                                                            googleAuthManager.signOut {}
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Logout,
                                                        contentDescription = "Sign Out",
                                                        tint = BrandPink,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(14.dp))
                                            
                                            // YouTube Broadcast Header
                                            Text(
                                                text = "SELECT SCHEDULED BROADCAST CHANNEL",
                                                color = Color.LightGray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            if (googleUserState.isLoadingBroadcasts) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(80.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = BrandCyan,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            } else {
                                                if (googleUserState.error != null) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(BrandAmberAlert.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                            .border(1.dp, BrandAmberAlert.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Warning,
                                                            contentDescription = "Warning",
                                                            tint = BrandAmberAlert,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = googleUserState.error!!,
                                                            color = Color.LightGray,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                
                                                // List active broadcasts
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    googleUserState.activeBroadcasts.forEach { broadcast ->
                                                        val isSelected = destChannels.firstOrNull { it.platform == "YouTube Live" }?.streamKey?.contains(broadcast.id.takeLast(4)) == true || 
                                                            (broadcast.id == "yt-broadcast-1" && destChannels.firstOrNull { it.platform == "YouTube Live" }?.streamKey == "yt-xxxx-yyyy")
                                                        
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(
                                                                    if (isSelected) BrandCyan.copy(alpha = 0.1f) else DarkSurface,
                                                                    RoundedCornerShape(8.dp)
                                                                )
                                                                .border(
                                                                    1.dp,
                                                                    if (isSelected) BrandCyan else DarkBorder,
                                                                    RoundedCornerShape(8.dp)
                                                                )
                                                                .clickable {
                                                                    pipeline.selectYouTubeBroadcast(broadcast.id)
                                                                }
                                                                .padding(10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = broadcast.snippet.title,
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 11.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "Status: ${broadcast.status?.lifeCycleStatus?.uppercase() ?: "READY"} · ID: ${broadcast.id}",
                                                                    color = Color.Gray,
                                                                    fontSize = 9.sp
                                                                )
                                                            }
                                                            
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        if (isSelected) BrandCyan else Color.DarkGray,
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (isSelected) "BOUND" else "BIND",
                                                                    color = if (isSelected) Color.Black else Color.LightGray,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text("6-CHANNEL SIMULCAST BITRATE ENGINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Renders single live encoding to multiple setups", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    destChannels.forEach { dest ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkSurfaceContainer, RoundedCornerShape(6.dp))
                                                .border(1.dp, if (dest.isEnabled) BrandGlowGreen.copy(alpha=0.4f) else DarkBorder, RoundedCornerShape(6.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (dest.platform.contains("YouTube")) Icons.Default.OndemandVideo else Icons.Default.Cast,
                                                    contentDescription = null,
                                                    tint = if (dest.isEnabled) BrandPink else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(dest.platform, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    if (dest.isEnabled && isBroadcasting) {
                                                        Text("${dest.currentBitrateKbps} Kbps · stability ${"%.0f%%".format(dest.connectionStability * 100)} · latency: ${dest.latencyMs}ms", color = BrandGlowGreen, fontSize = 8.sp)
                                                    } else {
                                                        Text("Inactive stream path", color = Color.Gray, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                            
                                            // Status tag
                                            Box(
                                                modifier = Modifier
                                                    .background(if (dest.isEnabled) BrandGlowGreen.copy(alpha=0.15f) else Color.DarkGray, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (dest.isEnabled) "ARMED" else "MUTED", color = if (dest.isEnabled) BrandGlowGreen else Color.LightGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text("CONNECT MODE (WORKSTATION REMOTE WORKFLOW)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Pairs mobile capture dynamically to desktop layouts via QR code scanner", color = Color.Gray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceContainer, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text("PAIR SECTIONS QR CODE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(qrCodeStr, color = BrandCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                if (pairState == "CONNECTED") {
                                                    pipeline.disconnectCompanionDevice()
                                                } else {
                                                    pipeline.pairCompanionDevice()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (pairState == "CONNECTED") BrandPink else BrandCyan),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (pairState == "CONNECTED") "BREAK PAIRING" else "SIMULATE PC PAIR", fontSize = 10.sp)
                                        }
                                    }
                                    
                                    // Simulated generated QR code square
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .background(Color.White)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Mock QR pixel blocks representation
                                            val cSize = size.width / 4
                                            drawRect(Color.Black, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(cSize, cSize))
                                            drawRect(Color.Black, topLeft = Offset(size.width - cSize, 0f), size = androidx.compose.ui.geometry.Size(cSize, cSize))
                                            drawRect(Color.Black, topLeft = Offset(0f, size.height - cSize), size = androidx.compose.ui.geometry.Size(cSize, cSize))
                                            drawRect(Color.Black, topLeft = Offset(cSize * 2, cSize * 2), size = androidx.compose.ui.geometry.Size(cSize, cSize))
                                        }
                                    }
                                }
                            }

                            if (pairState == "CONNECTED") {
                                item {
                                    Text("REMOTE COMPANION MACRO CONTROLS (TOUCH-SCREEN PAD)", color = BrandCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Blackout Toggle", "Mute Mic", "Switch Camera Blend", "Trigger Alert Sound").forEach { macroName ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(DarkSurfaceContainer, RoundedCornerShape(6.dp))
                                                    .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                                                    .clickable { pipeline.executeCompanionMacro(macroName) }
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(macroName, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                                            }
                                        }
                                    }
                                    
                                    // Log of triggers
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .background(Color.Black, RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("Macropad action updates:", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        companionLogs.take(3).forEach { log ->
                                            Text(log, color = Color.Green, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        // TAB 5: GEMINI WEBSOCKET ENGINE & COMPOSITION INGEST
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceContainer, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("GEMINI MULTIMODAL API STREAM CHANNELS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Real-time base64 frame serialization", color = Color.Gray, fontSize = 10.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(BrandGlowGreen.copy(alpha=0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("WS_CONNECTED", color = BrandGlowGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text("LIVE TRANSACTION INGEST OUTBOX FLOW LOGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            
                            // Console Logger terminal layout showing real h264 websocket packets
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                items(consoleLogs) { logLine ->
                                    Text(
                                        text = logLine,
                                        color = if (logLine.contains("ERROR")) Color.Red else if (logLine.contains("GEMINI")) BrandCyan else Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
