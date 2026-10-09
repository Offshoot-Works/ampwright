package io.github.offshootworks.ampwright

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.offshootworks.ampwright.ui.AmpWrightApp
import io.github.offshootworks.ampwright.ui.theme.AmpWrightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmpWrightTheme {
                AmpWrightApp()
            }
        }
    }
}
