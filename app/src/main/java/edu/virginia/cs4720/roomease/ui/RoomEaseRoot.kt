package edu.virginia.cs4720.roomease.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.virginia.cs4720.roomease.ui.auth.AuthNavHost
import edu.virginia.cs4720.roomease.ui.components.ErrorState
import edu.virginia.cs4720.roomease.ui.components.RoomEaseLogo
import edu.virginia.cs4720.roomease.ui.main.MainScreen
import edu.virginia.cs4720.roomease.ui.onboarding.OnboardingScreen
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.session.SessionViewModel
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme

/**
 * Lecture 11: "if (user == null) LoginScreen() else HomeScreen()" — extended with onboarding.
 * A fresh launch with no session lands on Login; a restored session skips straight to the app.
 */
@Composable
fun RoomEaseRoot(sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    when (val state = session) {
        SessionState.Loading -> SplashScreen()
        SessionState.SignedOut -> AuthNavHost()
        is SessionState.NeedsHousehold -> OnboardingScreen(
            profile = state.profile,
            onSignOut = sessionViewModel::signOut
        )
        is SessionState.Ready -> MainScreen(session = state, onSignOut = sessionViewModel::signOut)
        is SessionState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorState(message = state.message, onRetry = sessionViewModel::retry)
                TextButton(onClick = sessionViewModel::signOut) { Text("Sign out") }
            }
        }
    }
}

/** Figma "Splash": logo + "Getting your household ready" while the session restores. */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        RoomEaseLogo(large = true, modifier = Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(Modifier.width(120.dp))
            Text(
                "Getting your household ready",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp).fillMaxWidth())
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashPreview() {
    RoomEaseTheme { SplashScreen() }
}
