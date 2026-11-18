package edu.virginia.cs4720.roomease.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.virginia.cs4720.roomease.data.model.UserProfile
import edu.virginia.cs4720.roomease.ui.components.PrimaryButton
import edu.virginia.cs4720.roomease.ui.components.RoomEaseLogo
import edu.virginia.cs4720.roomease.ui.components.SectionCard
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme

@Composable
fun OnboardingScreen(
    profile: UserProfile,
    onSignOut: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
) {
    LaunchedEffect(profile) { viewModel.onProfile(profile) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingContent(
        name = profile.displayName,
        state = state,
        onTabChange = viewModel::onTabChange,
        onHouseholdNameChange = viewModel::onHouseholdNameChange,
        onInviteCodeChange = viewModel::onInviteCodeChange,
        onCreate = viewModel::createHousehold,
        onJoin = viewModel::requestToJoin,
        onCancelRequest = viewModel::cancelRequest,
        onRetryJoin = viewModel::retryJoin,
        onSignOut = onSignOut
    )
}

/** Figma "Create Household" / "Join Household": one screen with a segmented switch. */
@Composable
fun OnboardingContent(
    name: String,
    state: OnboardingUiState,
    onTabChange: (OnboardingTab) -> Unit,
    onHouseholdNameChange: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onCancelRequest: () -> Unit,
    onRetryJoin: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoomEaseLogo(Modifier.weight(1f))
            TextButton(onClick = onSignOut, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sign out") }
        }
        Text("Set up your household", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Hi $name! Create a new household or join your roommates with an invite code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.joinStatus != JoinStatus.None) {
            JoinRequestCard(state, onCancelRequest, onRetryJoin)
            return@Column
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            OnboardingTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = state.tab == tab,
                    onClick = { onTabChange(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index, OnboardingTab.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(if (tab == OnboardingTab.CREATE) "Create household" else "Join household") }
            }
        }

        when (state.tab) {
            OnboardingTab.CREATE -> {
                OutlinedTextField(
                    value = state.householdName,
                    onValueChange = onHouseholdNameChange,
                    label = { Text("Household name") },
                    placeholder = { Text("Maple Street House") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "You'll be the owner. You get an invite code to share, and you approve each roommate who asks to join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OnboardingTab.JOIN -> {
                OutlinedTextField(
                    value = state.inviteCode,
                    onValueChange = onInviteCodeChange,
                    label = { Text("Invite code") },
                    supportingText = { Text("Ask a roommate for the 6-character code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        state.errorMessage?.let {
            Text(
                it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.size(8.dp))
        if (state.tab == OnboardingTab.CREATE) {
            PrimaryButton("Create household", onClick = onCreate, enabled = state.canCreate, loading = state.isWorking)
        } else {
            PrimaryButton("Ask to join", onClick = onJoin, enabled = state.canJoin, loading = state.isWorking)
        }
    }
}

@Composable
private fun JoinRequestCard(state: OnboardingUiState, onCancel: () -> Unit, onRetryJoin: () -> Unit) {
    val household = state.pendingHouseholdName.ifBlank { "the household" }
    SectionCard {
        Column(
            Modifier.padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.joinStatus) {
                JoinStatus.Waiting -> {
                    Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Waiting for approval", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your request to join $household was sent. The owner approves it from Settings → Household. " +
                            "This screen updates on its own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onCancel, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel request")
                    }
                }
                JoinStatus.Joining -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.errorMessage == null) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(12.dp))
                        }
                        Text("Approved! Joining $household…", style = MaterialTheme.typography.titleMedium)
                    }
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onRetryJoin, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    }
                }
                JoinStatus.Declined, JoinStatus.Missing -> {
                    Text(
                        if (state.joinStatus == JoinStatus.Declined) "Request declined" else "Request not found",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (state.joinStatus == JoinStatus.Declined) "The owner of $household declined your request."
                        else "We couldn't find your request. It may have been removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton("Use a different code", onClick = onCancel, loading = state.isWorking)
                }
                JoinStatus.None -> Unit
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Composable
private fun OnboardingCreatePreview() {
    RoomEaseTheme {
        OnboardingContent(
            name = "Raheel", state = OnboardingUiState(householdName = "Maple Street House"),
            onTabChange = {}, onHouseholdNameChange = {}, onInviteCodeChange = {}, onCreate = {}, onJoin = {},
            onCancelRequest = {}, onRetryJoin = {}, onSignOut = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Composable
private fun OnboardingWaitingPreview() {
    RoomEaseTheme {
        OnboardingContent(
            name = "Rayyan", state = OnboardingUiState(joinStatus = JoinStatus.Waiting, pendingHouseholdName = "Maple Street House"),
            onTabChange = {}, onHouseholdNameChange = {}, onInviteCodeChange = {}, onCreate = {}, onJoin = {},
            onCancelRequest = {}, onRetryJoin = {}, onSignOut = {}
        )
    }
}
