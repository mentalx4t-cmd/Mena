package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.example.pipeline.LiveStreamPipeline
import com.example.ui.StudioDashboard
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var liveStreamPipeline: LiveStreamPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Instantiate the centralized media streaming and base64 compositor pipeline
        liveStreamPipeline = LiveStreamPipeline(applicationContext)
        
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.DarkBackground
                ) {
                    StudioDashboard(pipeline = liveStreamPipeline)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    liveStreamPipeline.destroy()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::liveStreamPipeline.isInitialized) {
            liveStreamPipeline.destroy()
        }
    }
}

