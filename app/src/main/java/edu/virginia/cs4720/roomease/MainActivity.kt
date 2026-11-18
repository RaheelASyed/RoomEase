package edu.virginia.cs4720.roomease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.virginia.cs4720.roomease.data.AppSettings
import edu.virginia.cs4720.roomease.data.ThemeMode
import edu.virginia.cs4720.roomease.ui.RoomEaseRoot
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme

/** Single Activity (Lecture 7, Model B): screens are composables swapped by Navigation Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RoomEaseApp).container
        setContent {
            // Theme mode comes from DataStore; null until the first read so we don't flash the wrong theme.
            val settings: AppSettings? by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            RoomEaseTheme(themeMode = current?.themeMode ?: ThemeMode.SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (current != null) RoomEaseRoot()
                }
            }
        }
    }
}
