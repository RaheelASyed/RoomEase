package edu.virginia.cs4720.roomease.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.virginia.cs4720.roomease.data.AppSettings
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.SplitDefault
import edu.virginia.cs4720.roomease.data.ThemeMode
import edu.virginia.cs4720.roomease.data.model.Household
import edu.virginia.cs4720.roomease.data.model.JoinRequest
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.data.model.UserProfile
import edu.virginia.cs4720.roomease.ui.components.Avatar
import edu.virginia.cs4720.roomease.ui.components.ScreenHeader
import edu.virginia.cs4720.roomease.ui.components.SectionCard
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme

@Composable
fun SettingsRoute(session: SessionState.Ready, onSignOut: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(session.householdId, session.uid, session.isOwner))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreen(
        state = state,
        profile = session.profile,
        household = session.household,
        members = session.members,
        isOwner = session.isOwner,
        onThemeChange = { viewModel.setThemeMode(it) },
        onChoreViewChange = { viewModel.setChoreView(it) },
        onSplitChange = { viewModel.setSplitDefault(it) },
        onDecide = { request, approve -> viewModel.decide(request, approve, session.members.size) },
        onRemoveMember = viewModel::removeMember,
        onCopyCode = { copyToClipboard(context, session.household.inviteCode) },
        onShareCode = { shareInvite(context, session.household) },
        onDismissMessage = viewModel::clearMessage,
        onSignOut = onSignOut
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    profile: UserProfile,
    household: Household,
    members: List<Member>,
    isOwner: Boolean,
    onThemeChange: (ThemeMode) -> Unit,
    onChoreViewChange: (ChoreView) -> Unit,
    onSplitChange: (SplitDefault) -> Unit,
    onDecide: (JoinRequest, Boolean) -> Unit,
    onRemoveMember: (Member) -> Unit,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onDismissMessage: () -> Unit,
    onSignOut: () -> Unit
) {
    // Which roommate the "Are you sure?" dialog is about (saved, so it survives rotation).
    var confirmRemoveUid by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRemoval = members.firstOrNull { it.uid == confirmRemoveUid }
    if (pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoveUid = null },
            title = { Text("Remove ${pendingRemoval.displayName}?") },
            text = {
                Text(
                    "Are you sure? ${pendingRemoval.displayName} will immediately lose access to ${household.name}'s " +
                        "chores, expenses and chat. Their past chores and expenses stay in the history. " +
                        "They can ask to join again with the invite code."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(pendingRemoval)
                        confirmRemoveUid = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveUid = null }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
    ) {
        ScreenHeader("Settings")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.message?.let {
                SectionCard {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK") }
                    }
                }
            }

            // ---- profile ----
            SectionCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile.displayName, size = 48.dp, highlight = true)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(profile.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- household ----
            Heading("Household")
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(household.name, style = MaterialTheme.typography.titleLarge)
                    Text("Invite code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            household.inviteCode.chunked(1).joinToString(" "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onCopyCode) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy invite code") }
                        IconButton(onClick = onShareCode) { Icon(Icons.Outlined.Share, contentDescription = "Share invite code") }
                    }
                    Text(
                        if (isOwner) "Roommates enter this code to ask to join. You approve each request below, so a code alone never lets anyone in."
                        else "Roommates enter this code to ask to join. The household owner approves each request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isOwner) {
                Heading("Join requests")
                SectionCard {
                    Column {
                        if (state.joinRequests.isEmpty()) {
                            Text(
                                "No pending requests.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.joinRequests.forEachIndexed { index, request ->
                            if (index > 0) HorizontalDivider()
                            val busy = request.uid in state.decidingIds
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(request.displayName)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(request.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text(request.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onDecide(request, true) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Approve") }
                                    OutlinedButton(onClick = { onDecide(request, false) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Decline") }
                                }
                            }
                        }
                    }
                }
            }

            Heading("Members (${members.size})")
            SectionCard {
                Column {
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(member.displayName, size = 36.dp, highlight = member.uid == profile.uid)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (member.uid == profile.uid) "${member.displayName} (you)" else member.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (member.isOwner) {
                                Text("Owner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            } else if (isOwner) {
                                // Only the owner sees this; the rules also reject anyone else.
                                IconButton(
                                    onClick = { confirmRemoveUid = member.uid },
                                    enabled = member.uid !in state.removingIds
                                ) {
                                    Icon(
                                        Icons.Outlined.PersonRemove,
                                        contentDescription = "Remove ${member.displayName}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- preferences (Preferences DataStore) ----
            Heading("Preferences")
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ChoiceSetting(
                        title = "Theme",
                        caption = "Applies across the whole app right away.",
                        options = ThemeMode.entries,
                        selected = state.settings.themeMode,
                        label = { when (it) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
                        onSelect = onThemeChange
                    )
                    ChoiceSetting(
                        title = "Default chore view",
                        caption = "What the Chores tab shows when you open it.",
                        options = ChoreView.entries,
                        selected = state.settings.choreView,
                        label = { if (it == ChoreView.MINE) "My chores" else "All chores" },
                        onSelect = onChoreViewChange
                    )
                    ChoiceSetting(
                        title = "Default expense split",
                        caption = "Who's selected when you start a new expense.",
                        options = SplitDefault.entries,
                        selected = state.settings.splitDefault,
                        label = { if (it == SplitDefault.ALL_ROOMMATES) "All roommates" else "Choose manually" },
                        onSelect = onSplitChange
                    )
                }
            }

            // Destructive action at the bottom, out of the easy thumb zone (Lecture 8).
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Sign out") }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() }
    )
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    caption: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(label(option), maxLines = 1) }
            }
        }
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("RoomEase invite code", code))
}

/** Lecture 7 implicit intent: name the verb (SEND), let the user pick the app. */
private fun shareInvite(context: Context, household: Household) {
    val send = Intent(Intent.ACTION_SEND)
        .putExtra(Intent.EXTRA_TEXT, "Join ${household.name} on RoomEase with invite code ${household.inviteCode}")
        .setType("text/plain")
    context.startActivity(Intent.createChooser(send, "Share invite code"))
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, heightDp = 1400)
@Composable
private fun SettingsPreview() {
    RoomEaseTheme {
        SettingsScreen(
            state = SettingsUiState(
                settings = AppSettings(themeMode = ThemeMode.SYSTEM, choreView = ChoreView.ALL),
                joinRequests = listOf(JoinRequest("x", "Arjun Rao", "arjun@virginia.edu"))
            ),
            profile = UserProfile("me", "Raheel Syed", "raheel@virginia.edu"),
            household = Household("h", "Maple Street House", "me", "4J5B9D"),
            members = listOf(Member("me", "Raheel Syed", Member.ROLE_OWNER), Member("r", "Rayyan Alam")),
            isOwner = true,
            onThemeChange = {}, onChoreViewChange = {}, onSplitChange = {}, onDecide = { _, _ -> }, onRemoveMember = {},
            onCopyCode = {}, onShareCode = {}, onDismissMessage = {}, onSignOut = {}
        )
    }
}
