package edu.virginia.cs4720.roomease.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.data.ChatRepository
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.Message
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = ""
) {
    val canSend: Boolean get() = draft.isNotBlank() && draft.trim().length <= MAX_LENGTH

    companion object {
        const val MAX_LENGTH = 1000
    }
}

private sealed interface ChatLoad {
    data object Loading : ChatLoad
    data class Loaded(val messages: List<Message>) : ChatLoad
    data class Failed(val message: String) : ChatLoad
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val householdId: String,
    private val myUid: String,
    private val myName: String,
    private val savedState: SavedStateHandle
) : ViewModel() {
    // Checked before getStateFlow() below, which would otherwise create the "draft" key itself.
    private val restoredFromSavedState = savedState.contains("draft")
    private val retry = MutableStateFlow(0)
    private var persistJob: Job? = null

    private val load = retry.flatMapLatest {
        repository.observeMessages(householdId)
            .map<List<Message>, ChatLoad> { ChatLoad.Loaded(it) }
            .onStart { emit(ChatLoad.Loading) }
            .catch { emit(ChatLoad.Failed(it.friendlyMessage())) }
    }

    /** The draft lives in saved state (rotation/process death) and DataStore (full app restart). */
    private val draft = savedState.getStateFlow("draft", "")

    val uiState: StateFlow<ChatUiState> = combine(load, draft) { load, draft ->
        when (load) {
            ChatLoad.Loading -> ChatUiState(isLoading = true, draft = draft)
            is ChatLoad.Failed -> ChatUiState(isLoading = false, errorMessage = load.message, draft = draft)
            is ChatLoad.Loaded -> ChatUiState(isLoading = false, messages = load.messages, draft = draft)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(draft = draft.value))

    init {
        if (!restoredFromSavedState) {
            viewModelScope.launch {
                val stored = settingsRepository.chatDraft(householdId)
                if (savedState.get<String>("draft").isNullOrEmpty()) savedState["draft"] = stored
            }
        }
    }

    fun onDraftChange(value: String) {
        val text = value.take(ChatUiState.MAX_LENGTH)
        savedState["draft"] = text
        persistDraft(text)
    }

    /** Speech-to-text result goes into the composer for review; it is never sent automatically. */
    fun appendTranscript(text: String) {
        val current = draft.value.trimEnd()
        onDraftChange(if (current.isEmpty()) text.trim() else "$current ${text.trim()}")
    }

    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty() || text.length > ChatUiState.MAX_LENGTH) return
        // Clear first so a double tap (or a tap during rotation) can't send twice.
        savedState["draft"] = ""
        persistDraft("", immediate = true)
        repository.send(householdId, text, myUid, myName)
    }

    private fun persistDraft(text: String, immediate: Boolean = false) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!immediate) delay(400) // don't write to disk on every keystroke
            settingsRepository.setChatDraft(householdId, text)
        }
    }

    fun retry() {
        retry.value++
    }

    companion object {
        fun factory(householdId: String, uid: String, name: String) = viewModelFactory {
            initializer {
                val c = container()
                ChatViewModel(c.chatRepository, c.settingsRepository, householdId, uid, name, createSavedStateHandle())
            }
        }
    }
}
