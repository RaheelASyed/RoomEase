package edu.virginia.cs4720.roomease.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.virginia.cs4720.roomease.data.model.Message
import edu.virginia.cs4720.roomease.ui.components.Avatar
import edu.virginia.cs4720.roomease.ui.components.EmptyState
import edu.virginia.cs4720.roomease.ui.components.ErrorState
import edu.virginia.cs4720.roomease.ui.components.LoadingState
import edu.virginia.cs4720.roomease.ui.components.ScreenHeader
import edu.virginia.cs4720.roomease.ui.components.hasPermission
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatRoute(session: SessionState.Ready) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(session.householdId, session.uid, session.myName))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speech = rememberSpeechToText()
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    val startListening = {
        notice = null
        speech.start(onResult = viewModel::appendTranscript, onError = { notice = it })
    }
    // Ask for the microphone at the moment the user taps the mic (Lecture 10/17), never at launch.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        else notice = "Microphone permission denied. You can keep typing, or allow it in Settings to dictate."
    }

    ChatScreen(
        state = state,
        householdName = session.household.name,
        myUid = session.uid,
        nameOf = session::nameOf,
        isListening = speech.isListening,
        partialText = speech.partialText,
        notice = notice,
        onDraftChange = {
            notice = null
            viewModel.onDraftChange(it)
        },
        onSend = viewModel::send,
        onMicClick = {
            when {
                speech.isListening -> speech.stop()
                context.hasPermission(Manifest.permission.RECORD_AUDIO) -> startListening()
                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onDismissNotice = { notice = null },
        onRetry = viewModel::retry
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    householdName: String,
    myUid: String,
    nameOf: (String) -> String,
    isListening: Boolean,
    partialText: String,
    notice: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onDismissNotice: () -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    // Keep the newest message in view as messages arrive.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Chat") {
            Text(householdName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> LoadingState("Loading messages…")
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                state.messages.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "No messages yet",
                    body = "Say hi to your household. Everyone in $householdName sees messages here instantly."
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            mine = message.senderId == myUid,
                            senderLabel = nameOf(message.senderId).let { if (it == "Former roommate") message.senderName else it }
                        )
                    }
                }
            }
        }
        Composer(
            draft = state.draft,
            canSend = state.canSend,
            isListening = isListening,
            partialText = partialText,
            notice = notice,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onMicClick = onMicClick,
            onDismissNotice = onDismissNotice
        )
    }
}

@Composable
private fun MessageBubble(message: Message, mine: Boolean, senderLabel: String) {
    val time = message.createdAt?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("h:mm a"))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Avatar(senderLabel, size = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            if (!mine) {
                Text(senderLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${if (mine) "You" else senderLabel}: ${message.text}" +
                        if (message.pendingSync) ", sending" else ""
                }
            ) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (message.pendingSync) {
                    // Written to the local cache but not yet confirmed by the server.
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Sending…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (time != null) {
                    Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    isListening: Boolean,
    partialText: String,
    notice: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onDismissNotice: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (isListening) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (partialText.isBlank()) "Listening… tap stop when you're done" else partialText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(6.dp))
            }
            notice?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissNotice) { Text("OK") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Message your household") },
                    maxLines = 5,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMicClick) {
                    Icon(
                        if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening) "Stop dictation" else "Dictate a message",
                        tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, fontScale = 1.5f)
@Composable
private fun ChatPreview() {
    RoomEaseTheme {
        ChatScreen(
            state = ChatUiState(
                isLoading = false,
                messages = listOf(
                    Message("1", "Let's get lunch. How about pizza?", "me", "Raheel"),
                    Message("2", "Let's do it! I'm in a meeting until noon.", "r", "Rayyan Alam"),
                    Message("3", "Perfect, see you then", "me", "Raheel", pendingSync = true)
                ),
                draft = "Bringing napkins"
            ),
            householdName = "Maple Street House",
            myUid = "me",
            nameOf = { if (it == "me") "You" else "Rayyan Alam" },
            isListening = true, partialText = "bringing nap", notice = null,
            onDraftChange = {}, onSend = {}, onMicClick = {}, onDismissNotice = {}, onRetry = {}
        )
    }
}
