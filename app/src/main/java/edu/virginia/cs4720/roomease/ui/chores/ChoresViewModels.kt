package edu.virginia.cs4720.roomease.ui.chores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Timestamp
import edu.virginia.cs4720.roomease.data.ChoreRepository
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

// ---------------- Chores list ----------------

/** Loading / error / success in one immutable snapshot (Lecture 6 + Lecture 10's async UI state). */
data class ChoresUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: ChoreView = ChoreView.MINE,
    val chores: List<Chore> = emptyList(),
    val doneCount: Int = 0,
    val totalCount: Int = 0
)

private sealed interface ChoreLoad {
    data object Loading : ChoreLoad
    data class Loaded(val chores: List<Chore>) : ChoreLoad
    data class Failed(val message: String) : ChoreLoad
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChoresViewModel(
    private val repository: ChoreRepository,
    settingsRepository: SettingsRepository,
    private val householdId: String,
    private val myUid: String,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val retry = MutableStateFlow(0)

    private val load = retry.flatMapLatest {
        repository.observeChores(householdId)
            .map<List<Chore>, ChoreLoad> { ChoreLoad.Loaded(it) }
            .onStart { emit(ChoreLoad.Loading) }
            .catch { emit(ChoreLoad.Failed(it.friendlyMessage())) }
    }

    /** The filter the user picked on this screen (saved state) wins; otherwise the DataStore default. */
    private val filter = combine(
        savedState.getStateFlow<String?>("filter", null),
        settingsRepository.settings
    ) { picked, settings -> picked?.let { ChoreView.valueOf(it) } ?: settings.choreView }

    val uiState: StateFlow<ChoresUiState> = combine(load, filter) { load, filter ->
        when (load) {
            ChoreLoad.Loading -> ChoresUiState(isLoading = true, filter = filter)
            is ChoreLoad.Failed -> ChoresUiState(isLoading = false, errorMessage = load.message, filter = filter)
            is ChoreLoad.Loaded -> {
                val visible = load.chores
                    .filter { filter == ChoreView.ALL || it.assigneeId == myUid }
                    // Lecture 3: one sortedWith call — open chores first, then soonest due.
                    .sortedWith(compareBy<Chore>({ it.done }, { it.dueDate?.seconds ?: Long.MAX_VALUE }))
                ChoresUiState(
                    isLoading = false,
                    filter = filter,
                    chores = visible,
                    doneCount = visible.count { it.done },
                    totalCount = visible.size
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState())

    fun setFilter(view: ChoreView) {
        savedState["filter"] = view.name
    }

    /** Only the assignee may complete a chore; the rules enforce this on the server too. */
    fun setDone(chore: Chore, done: Boolean) {
        if (chore.assigneeId != myUid) return
        repository.setDone(householdId, chore.id, done)
    }

    fun retry() {
        retry.value++
    }

    companion object {
        fun factory(householdId: String, uid: String) = viewModelFactory {
            initializer {
                val c = container()
                ChoresViewModel(c.choreRepository, c.settingsRepository, householdId, uid, createSavedStateHandle())
            }
        }
    }
}

// ---------------- Add chore ----------------

data class AddChoreUiState(
    val title: String = "",
    val assigneeId: String = "",
    val dueDate: LocalDate = LocalDate.now().plusDays(1),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
) {
    /** Lecture 8: disable Save until valid. */
    val canSave: Boolean get() = Validation.isValidChoreTitle(title) && assigneeId.isNotEmpty() && !isSaving
}

class AddChoreViewModel(
    private val repository: ChoreRepository,
    private val householdId: String,
    private val me: Member,
    private val members: List<Member>,
    private val savedState: SavedStateHandle
) : ViewModel() {

    // Generated once and kept in saved state: a retry, rotation or process restart reuses the same
    // document id, so the chore can never be created twice.
    private val choreId: String = savedState["choreId"] ?: repository.newChoreId(householdId).also { savedState["choreId"] = it }

    private val _uiState = MutableStateFlow(
        AddChoreUiState(
            title = savedState["title"] ?: "",
            assigneeId = savedState["assigneeId"] ?: me.uid,
            dueDate = savedState.get<Long>("dueEpochDay")?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(1),
            saved = savedState["saved"] ?: false
        )
    )
    val uiState: StateFlow<AddChoreUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) {
        val trimmed = value.take(80)
        savedState["title"] = trimmed
        _uiState.update { it.copy(title = trimmed, errorMessage = null) }
    }

    fun onAssigneeChange(uid: String) {
        savedState["assigneeId"] = uid
        _uiState.update { it.copy(assigneeId = uid) }
    }

    fun onDueDateChange(date: LocalDate) {
        savedState["dueEpochDay"] = date.toEpochDay()
        _uiState.update { it.copy(dueDate = date) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || state.saved) return
        val assignee = members.firstOrNull { it.uid == state.assigneeId } ?: run {
            _uiState.update { it.copy(errorMessage = "Pick a roommate who's still in the household.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (!repository.alreadySaved(householdId, choreId)) {
                // Noon local time so the date never shifts across time zones.
                val due = Timestamp(Date.from(state.dueDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()))
                repository.createChore(
                    householdId = householdId,
                    choreId = choreId,
                    title = state.title,
                    assigneeId = assignee.uid,
                    assigneeName = assignee.displayName,
                    createdBy = me.uid,
                    createdByName = me.displayName,
                    dueDate = due
                )
            }
            savedState["saved"] = true
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        fun factory(householdId: String, me: Member, members: List<Member>) = viewModelFactory {
            initializer {
                AddChoreViewModel(container().choreRepository, householdId, me, members, createSavedStateHandle())
            }
        }
    }
}
