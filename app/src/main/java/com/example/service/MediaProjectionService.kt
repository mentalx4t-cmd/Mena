package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MediaProjectionService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaProjection: MediaProjection? = null
    private val _isProjecting = MutableStateFlow(false)
    val isProjecting: StateFlow<Boolean> get() = _isProjecting

    private val _capturedFramesCount = MutableStateFlow(0)
    val capturedFramesCount: StateFlow<Int> get() = _capturedFramesCount

    companion object {
        private const val TAG = "MediaProjectionService"
        private const val NOTIFICATION_ID = 1010
        private const val CHANNEL_ID = "ScreencastChannel"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind triggered")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand triggered with action: ${intent?.action}")

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val dataIntent = intent?.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)

        if (resultCode != -1 && dataIntent != null) {
            startForegroundNotification()
            initializeScreenCapture(resultCode, dataIntent)
        } else {
            Log.w(TAG, "No valid media projection tokens supplied in start command")
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PRISM Live Screen Recorder"
            val descriptionText = "Captures low-latency system screen and internal audio"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screencast Mode Active")
            .setContentText("PRISM Live is streaming your system screen and system audio...")
            .setSmallIcon(android.R.drawable.presence_video_busy)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initializeScreenCapture(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            // In a production app, the mediaProjection instance is used to configure a VirtualDisplay 
            // and feed into MediaCodec or OpenGL input surfaces.
            _isProjecting.value = true
            
            // Simulate the low-latency system screencast frame emission
            serviceScope.launch {
                while (_isProjecting.value) {
                    delay(33) // ~30 fps low-latency display rate
                    _capturedFramesCount.value += 1
                }
            }
            Log.d(TAG, "MediaProjection successfully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaProjection hardware capture pipeline", e)
            stopCapture()
        }
    }

    fun stopCapture() {
        Log.d(TAG, "Stopping screen capture and releasing media projection")
        _isProjecting.value = false
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
