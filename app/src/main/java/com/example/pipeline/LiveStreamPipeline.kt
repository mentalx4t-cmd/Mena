package com.example.pipeline

import android.content.Context
import android.graphics.PointF
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// --- DATA CLASSES ---

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val avatarColor: Int = (0xFF000000 or (Math.random() * 0xFFFFFF).toLong()).toInt()
)

data class MediaOverlay(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val isVideo: Boolean = false,
    val isVisible: Boolean = true,
    var scale: Float = 1.0f,
    var offset: PointF = PointF(0.5f, 0.5f)
)

data class BroadcastDestination(
    val id: String = UUID.randomUUID().toString(),
    val platform: String, // e.g. "YouTube", "Twitch", "TikTok", "Custom RTMP"
    val streamKey: String,
    val streamUrl: String,
    val isEnabled: Boolean = false,
    val currentBitrateKbps: Int = 2500,
    val connectionStability: Float = 0.95f, // 0.0 to 1.0
    val latencyMs: Int = 180
)

data class VTuberAvatar(
    val id: String,
    val name: String,
    val is3D: Boolean = false, // false: PNG/GIF, true: VRM models
    val assetPath: String,
    val thumbnail: String
)

sealed class StreamingMode {
    object Camera : StreamingMode()
    object Screencast : StreamingMode()
    object VTuber : StreamingMode()
}

class LiveStreamPipeline(private val context: Context) {

    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tag = "LiveStreamPipeline"

    // --- PIPELINE RUNNING STATE ---
    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> get() = _isBroadcasting

    private val _streamingMode = MutableStateFlow<StreamingMode>(StreamingMode.Camera)
    val streamingMode: StateFlow<StreamingMode> get() = _streamingMode

    // --- CAMERA PIPELINE CONTROLS ---
    val targetResolution = MutableStateFlow("1080p 60fps") // options: 1080p 60fps, 1080p 30fps, 720p 60fps
    val exposureCompensation = MutableStateFlow(0.0f) // -3.0f to 3.0f
    val manualIso = MutableStateFlow(400) // 100 to 3200
    val isFocusLocked = MutableStateFlow(false)
    val selectedLens = MutableStateFlow("Wide Lens (26mm)") // options: Wide Lens (26mm), Ultra-Wide Lens (13mm), Telephoto (70mm)

    // --- VTUBER METRIC SELECTIONS ---
    val availableAvatars = listOf(
        VTuberAvatar("p1", "Chibi Cat Neko", is3D = false, "", ""),
        VTuberAvatar("p2", "Cyber Synth Gumi", is3D = false, "", ""),
        VTuberAvatar("v1", "Elysia VRM Custom", is3D = true, "", ""),
        VTuberAvatar("v2", "Retro Mech Robot", is3D = true, "", "")
    )
    val activeAvatar = MutableStateFlow(availableAvatars.first())
    val vtuberMouthAperture = MutableStateFlow(0.0f) // 0.0 (closed) to 1.0 (fully open), reactive to audio
    val vtuberHeadRotationX = MutableStateFlow(0.0f) // -30 to +30 deg
    val vtuberHeadRotationY = MutableStateFlow(0.0f) // -30 to +30 deg

    // --- "MY STUDIO" MULTIMEDIA OVERLAYS ---
    private val _overlaysList = MutableStateFlow<List<MediaOverlay>>(
        listOf(
            MediaOverlay("o1", "Branding Watermark", "logo_overlay.png", isVideo = false),
            MediaOverlay("o2", "Live Goal Counter", "donation_progress.png", isVideo = false)
        )
    )
    val overlaysList: StateFlow<List<MediaOverlay>> get() = _overlaysList

    // --- LOW-LATENCY AUDIO MIXER ---
    val audioMicGain = MutableStateFlow(0.8f) // 0.0 to 1.5
    val audioInternalGain = MutableStateFlow(0.6f) // 0.0 to 1.5
    val audioBgmGain = MutableStateFlow(0.4f) // 0.0 to 1.5
    val bgmTrackName = MutableStateFlow("Epic Synth Background Loop")
    val isAudioLimiterEnabled = MutableStateFlow(true) // Dynamic clip prevention

    // Decibel meters (simulated active streams db: -60 to 0dB)
    val micDbLevel = MutableStateFlow(-60f)
    val internalDbLevel = MutableStateFlow(-60f)
    val bgmDbLevel = MutableStateFlow(-60f)
    val compositedMixDbLevel = MutableStateFlow(-60f)

    // --- WEB WIDGET & TEXT LAYOUTS ---
    val overlayWebUrl = MutableStateFlow("https://prism-theme.widget/alerts")
    val overlayWebTitle = MutableStateFlow("PRISM Alerts Overlay v2")
    val tickerText = MutableStateFlow("🔥 Welcome to the livestream! Thanks for joining. Don't forget to follow and support! 🔥")
    val tickerSpeedValue = MutableStateFlow(10f)

    // --- INTEGRATED CHAT CHANNELS ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(sender = "Viewer_49", message = "This 1080p feed looks absolutely pristine!"),
            ChatMessage(sender = "StreamingGuru", message = "Are you streaming via RTMP or WebSocket?"),
            ChatMessage(sender = "GeminiAI_Bot", message = "[AI Transcribing] Listening to broad audio cue...")
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> get() = _chatMessages

    // --- GRAPHICS EFFECT FIELDS (OPENGL PIPELINE SHADERS) ---
    val faceSmoothingValue = MutableStateFlow(0.5f) // 0.0 to 1.0 skin smoothing strength
    val chinAdjustmentValue = MutableStateFlow(0.2f) // Custom chin retoucher
    val eyeSizeValue = MutableStateFlow(0.3f) // Anime eye size adjustment

    // Chroma key settings
    val isChromaKeyEnabled = MutableStateFlow(false)
    val chromaTargetColorHex = MutableStateFlow("#00FF00") // pure green screen target
    val chromaThreshold = MutableStateFlow(0.4f) // Tolerance threshold
    val chromaSmoothing = MutableStateFlow(0.2f)

    // On-screen brush drawings lists
    private val _drawingLinesPoints = MutableStateFlow<List<List<PointF>>>(emptyList())
    val drawingLinesPoints: StateFlow<List<List<PointF>>> get() = _drawingLinesPoints
    val activeDrawingColorHex = MutableStateFlow("#FF0055") // bright pink neon brush
    val activeDrawingStrokeWidth = MutableStateFlow(8f)

    // Screen protection blackout toggle
    val isScreenProtectionActive = MutableStateFlow(false) // Safe standby overlay screen toggle

    // --- MULTI-CHANNEL STREAM DESTINATIONS (Max 6) ---
    private val _broadcastDestinations = MutableStateFlow<List<BroadcastDestination>>(
        listOf(
            BroadcastDestination(platform = "YouTube Live", streamKey = "yt-xxxx-yyyy", streamUrl = "rtmp://a.rtmp.youtube.com/live2", isEnabled = true),
            BroadcastDestination(platform = "Twitch Multi", streamKey = "live_zzzzzzzz", streamUrl = "rtmp://lax.contribute.live-video.net/app", isEnabled = true),
            BroadcastDestination(platform = "PRISM Virtual Stage", streamKey = "ps-091a", streamUrl = "srt://cast.prism.studio:1935", isEnabled = false),
            BroadcastDestination(platform = "Custom RTMP", streamKey = "custom-key", streamUrl = "rtmp://my.stream.server/feed", isEnabled = false)
        )
    )
    val broadcastDestinations: StateFlow<List<BroadcastDestination>> get() = _broadcastDestinations

    // --- GOOGLE LOGIN & YOUTUBE INTEGRATION ---
    val googleAuthManager = GoogleAuthManager(context, pipelineScope)
    val googleUserState: StateFlow<GoogleUserState> = googleAuthManager.userState

    fun selectYouTubeBroadcast(broadcastId: String) {
        googleAuthManager.fetchStreamKeyForBroadcast(broadcastId) { key, url ->
            val updated = _broadcastDestinations.value.map { dest ->
                if (dest.platform == "YouTube Live") {
                    dest.copy(streamKey = key, streamUrl = url)
                } else dest
            }
            _broadcastDestinations.value = updated
            addLog("YOUTUBE AUTH :: Stream bound to broadcast '$broadcastId'. Key: ***${key.takeLast(4)}")
        }
    }

    // --- PC COMPANION CONNECT MODE ---
    val companionQrPairingCode = MutableStateFlow("PRISM-CONNECT::PAIR_CODE_WIFI_${UUID.randomUUID().toString().take(6).uppercase()}")
    val companionConnectionState = MutableStateFlow("DISCONNECTED") // DISCONNECTED, PAIRING, CONNECTED
    val isPcWebcamModeActive = MutableStateFlow(false) // Turns phone into dynamic HD Webcam camera output to desktop stream
    val pcMacroPadTriggerLogs = MutableStateFlow(listOf("System initialized. Ready for companion."))

    // --- ON-DEVICE SPEECH AI TRANSCRIBER ---
    private val _liveTranscriptionText = MutableStateFlow("Pipeline initialized. Start speaking to trigger real-time AI captions...")
    val liveTranscriptionText: StateFlow<String> get() = _liveTranscriptionText

    // --- LOG PIPE FOR CONSOLE STREAM ---
    private val _pipelineConsoleLogs = MutableStateFlow<List<String>>(
        listOf("READY :: PRISM stream pipeline engine v1.0.0 is operational.")
    )
    val pipelineConsoleLogs: StateFlow<List<String>> get() = _pipelineConsoleLogs

    // --- GEMINI MULTIMODAL STREAM WEBSOCKET SIMULATION QUEUE ---
    private val _geminiLiveSocketState = MutableStateFlow("CONNECTED_REPLICATING") // CONNECTED_REPLICATING, CLOSED, CONNECTING
    val geminiLiveSocketState: StateFlow<String> get() = _geminiLiveSocketState

    init {
        startSimulators()
    }

    private fun addLog(message: String) {
        val currentLogs = _pipelineConsoleLogs.value.toMutableList()
        currentLogs.add(0, "[LOG] $message")
        if (currentLogs.size > 25) {
            currentLogs.removeLast()
        }
        _pipelineConsoleLogs.value = currentLogs
        Log.i(tag, message)
    }

    private fun startSimulators() {
        pipelineScope.launch {
            // Simulated active feed elements
            val topicsRandom = listOf(
                "Let's see if the chroma key is on!",
                "Wow, that VTuber head tilt is smooth.",
                "How is the latency on SRT stream 2?",
                "Are we hitting 1080p 60fps stable?",
                "That AI transcript is spot-on!",
                "Try pairing the companion remote macro pad.",
                "Simulcasting to 3 screens at 4.2 MBps total!"
            )
            val sendersRandom = listOf("SpectatorX", "L33tStreamer", "TechEnthusiast", "PrismSupporter", "Mod_Alpha")

            var tickCount = 0
            while (true) {
                delay(3000)
                tickCount++

                if (_isBroadcasting.value) {
                    // Update VU decibels meters dynamically with clipping limits applied
                    val micGainVal = audioMicGain.value
                    val internalGainVal = audioInternalGain.value
                    val bgmGainVal = audioBgmGain.value

                    // Simulated live input decibels
                    val realMicDb = (-20.0f + (Math.random() * 15.0f).toFloat()) * micGainVal
                    val realInternalDb = (-15.0f + (Math.random() * 10.0f).toFloat()) * internalGainVal
                    val realBgmDb = (-25.0f + (Math.random() * 5.0f).toFloat()) * bgmGainVal

                    micDbLevel.value = if (realMicDb > 0) 0f else realMicDb
                    internalDbLevel.value = if (realInternalDb > 0) 0f else realInternalDb
                    bgmDbLevel.value = if (realBgmDb > 0) 0f else realBgmDb

                    // Composition Mix combined with clipping prevention limiter
                    var rawMixed = Math.max(realMicDb, Math.max(realInternalDb, realBgmDb)) + 3.0f
                    if (isAudioLimiterEnabled.value && rawMixed > -1.5f) {
                        // Hard limiter compressor ceiling
                        rawMixed = -1.5f
                    }
                    compositedMixDbLevel.value = if (rawMixed > 0f) 0f else rawMixed

                    // VTuber Dynamic Face blendshape simulation
                    if (_streamingMode.value is StreamingMode.VTuber) {
                        // Open mouth proportionally to mic signal density
                        val voiceAmplitude = (realMicDb + 60f) / 60f
                        vtuberMouthAperture.value = (voiceAmplitude * 0.82f + 0.08f).coerceIn(0.0f, 1.0f)

                        // Head tilt simulation
                        vtuberHeadRotationX.value = (-15f + (Math.random() * 30f).toFloat())
                        vtuberHeadRotationY.value = (-20f + (Math.random() * 40f).toFloat())
                    }

                    // ABR adaptive bitrates fluctuate slightly with network jitter
                    val destinations = _broadcastDestinations.value.map { dest ->
                        if (dest.isEnabled) {
                            val noise = (-150 + (Math.random() * 300).toInt())
                            val currentStable = 0.85f + (Math.random() * 0.15f).toFloat()
                            val latNoise = (-15 + (Math.random() * 30).toInt())
                            dest.copy(
                                currentBitrateKbps = (2800 + noise).coerceIn(1200, 4800),
                                connectionStability = currentStable.coerceIn(0.0f, 1.0f),
                                latencyMs = (160 + latNoise).coerceIn(80, 450)
                            )
                        } else dest
                    }
                    _broadcastDestinations.value = destinations

                    // Ingest Chunks Base64 Pipeline Simulator -> Ingesting into Gemini Multimodal Live Socket!
                    val encodedFrameBytes = "STREAM_FRAME_MOCK_H264_FRAME_${UUID.randomUUID().toString().take(8)}".toByteArray()
                    val encFrameB64 = Base64.encodeToString(encodedFrameBytes, Base64.NO_WRAP)
                    
                    val encodedAudioSegment = "PCM_SAMPLE_AAC_AUDIO_${tickCount}_LEN_1024".toByteArray()
                    val encAudioB64 = Base64.encodeToString(encodedAudioSegment, Base64.NO_WRAP)

                    addLog("PIPELINE :: Feeding H264 Frame Base64 chunk to Gemini websocket ($_geminiLiveSocketState). Bytes: ${encodedFrameBytes.size}, Frame: #$tickCount")
                    addLog("PIPELINE :: Sending Audio Segment to multi-destinations RTMP: B64 chunk of ${encodedAudioSegment.size} bytes.")

                    // Remote transcription simulator
                    if (Math.random() > 0.4) {
                        val phrases = listOf(
                            "Thank you guys for joining the stream today!",
                            "We are actively tweaking the Chroma Key smoothing pipeline.",
                            "This system shows our custom EGL Render flattening overlays onto a unified surface.",
                            "Gemini is listening in and generating interactive live overlays.",
                            "Let me demonstrate on-screen sketching on this live mobile canvas."
                        )
                        val index = (Math.random() * phrases.size).toInt()
                        _liveTranscriptionText.value = phrases[index]
                    }

                    // Simulated live incoming chat messages in pipeline
                    if (Math.random() > 0.5) {
                        val rSender = sendersRandom.random()
                        val rMsg = topicsRandom.random()
                        val chatMutable = _chatMessages.value.toMutableList()
                        chatMutable.add(ChatMessage(sender = rSender, message = rMsg))
                        if (chatMutable.size > 20) {
                            chatMutable.removeAt(0)
                        }
                        _chatMessages.value = chatMutable
                    }
                } else {
                    // Turn everything down when offline
                    micDbLevel.value = -60f
                    internalDbLevel.value = -60f
                    bgmDbLevel.value = -60f
                    compositedMixDbLevel.value = -60f
                    vtuberMouthAperture.value = 0.0f
                }
            }
        }
    }

    // --- PIPELINE PUBLIC CONTROLS ---

    fun startBroadcast() {
        if (_isBroadcasting.value) return
        _isBroadcasting.value = true
        addLog("BROADCAST COMMAND :: Initiated multi-channel simulcast. Routing single encoded feed to YouTube and Twitch.")
        addLog("GEMINI SOCKET :: Established low-latency multimodal connection to WebSocket endpoint.")
    }

    fun stopBroadcast() {
        if (!_isBroadcasting.value) return
        _isBroadcasting.value = false
        addLog("BROADCAST COMMAND :: Terminated all RTMP/SRT sessions safely. Released network pipelines.")
    }

    fun setStreamingMode(mode: StreamingMode) {
        _streamingMode.value = mode
        addLog("STREAM MODE CHANGED :: Switched to ${mode::class.simpleName} pipeline feed.")
    }

    fun addOverlay(name: String, isVideo: Boolean) {
        val current = _overlaysList.value.toMutableList()
        current.add(MediaOverlay(name = name, path = "${name.lowercase().replace(" ", "_")}.png", isVideo = isVideo))
        _overlaysList.value = current
        addLog("MY STUDIO OVERLAY :: Added custom overlay asset '$name'")
    }

    fun removeOverlay(overlay: MediaOverlay) {
        val current = _overlaysList.value.toMutableList()
        current.removeAll { it.id == overlay.id }
        _overlaysList.value = current
        addLog("MY STUDIO OVERLAY :: Removed overlay asset '${overlay.name}'")
    }

    fun toggleOverlayVisibility(overlay: MediaOverlay) {
        val current = _overlaysList.value.map {
            if (it.id == overlay.id) it.copy(isVisible = !it.isVisible) else it
        }
        _overlaysList.value = current
        val item = current.firstOrNull { it.id == overlay.id }
        addLog("MY STUDIO OVERLAY :: Toggled visibility of '${overlay.name}' to ${item?.isVisible}")
    }

    // --- DRAWING MODE API ---
    fun addDrawingLine(line: List<PointF>) {
        val current = _drawingLinesPoints.value.toMutableList()
        current.add(line)
        _drawingLinesPoints.value = current
        addLog("CANVAS CANVAS DRAWING :: Sketched a neon path of ${line.size} brush points.")
    }

    fun clearDrawingCanvas() {
        _drawingLinesPoints.value = emptyList()
        addLog("CANVAS DRAWING :: Reset and cleared on-stream sketch vectors overlay.")
    }

    // --- COMPANION ACTION RECEIVER ---
    fun executeCompanionMacro(macroName: String) {
        val list = pcMacroPadTriggerLogs.value.toMutableList()
        list.add(0, "[PC Macro Pad] Triggered Macro: '$macroName' at ${System.currentTimeMillis() % 100000}")
        if (list.size > 8) {
            list.removeLast()
        }
        pcMacroPadTriggerLogs.value = list
        addLog("COMPANION REMOTE :: Received local WebSocket action macro: '$macroName'")
        
        // Execute dynamic features based on macro keys
        when (macroName) {
            "Blackout Toggle" -> { isScreenProtectionActive.value = !isScreenProtectionActive.value }
            "Mute Mic" -> { audioMicGain.value = if (audioMicGain.value > 0f) 0f else 0.8f }
            "Switch Camera Blend" -> {
                selectedLens.value = if (selectedLens.value.startsWith("Wide")) "Telephoto (70mm)" else "Wide Lens (26mm)"
            }
            "Trigger Alert Sound" -> {
                addLog("PC Macro triggered local alert sound overlay!")
            }
        }
    }

    fun pairCompanionDevice() {
        companionConnectionState.value = "CONNECTED"
        addLog("COMPANION WEB SOCKET :: Paired desktop workstation controller via QR pairing.")
    }

    fun disconnectCompanionDevice() {
        companionConnectionState.value = "DISCONNECTED"
        addLog("COMPANION SOCKET :: Dropped pc pairing session.")
    }

    fun destroy() {
        pipelineScope.cancel()
    }
}
