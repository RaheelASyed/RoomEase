package edu.virginia.cs4720.roomease.ui.chores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.data.ChoreDetails
import edu.virginia.cs4720.roomease.data.ChoreRepository
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.domain.ChoreBoard
import edu.virginia.cs4720.roomease.domain.ChoreRepeat
import edu.virginia.cs4720.roomease.domain.ChoreSchedule
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.domain.toLocalDate
import edu.virginia.cs4720.roomease.domain.toNoonTimestamp
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import java.time.format.DateTimeFormatter

// ---------------- Chores list ----------------

/** Loading / error / success in one immutable snapshot (Lecture 6 + Lecture 10's async UI state). */
data class ChoresUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: ChoreView = ChoreView.MINE,
    val board: ChoreBoard? = null
)

/** "Sweep moved to Ali · due Dec 30" with an Undo action. */
data class ChoreUndo(val message: String, val before: Chore)

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
    private val me: Member,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val _undoEvents = MutableSharedFlow<ChoreUndo>(extraBufferCapacity = 1)
    val undoEvents: SharedFlow<ChoreUndo> = _undoEvents.asSharedFlow()

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
            is ChoreLoad.Loaded -> ChoresUiState(
                isLoading = false,
                filter = filter,
                board = ChoreBoard.build(load.chores, onlyFor = if (filter == ChoreView.MINE) me.uid else null, today = LocalDate.now())
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChoresUiState())

    fun setFilter(view: ChoreView) {
        savedState["filter"] = view.name
    }

    /**
     * The checkbox on your own chore. Only the assignee can (the rules enforce it too).
     * - One-off chore: tick / untick.
     * - Repeating chore: it isn't greyed out; it moves on to its next due date (and the next
     *   roommate if it rotates), and we offer Undo.
     */
    fun toggle(chore: Chore, members: List<Member>) {
        if (chore.assigneeId != me.uid) return
        val repeat = ChoreRepeat.from(chore.repeat)
        if (repeat == ChoreRepeat.NONE || chore.done) {
            repository.setDone(householdId, chore.id, !chore.done)
            return
        }
        val today = LocalDate.now()
        val nextDue = ChoreSchedule.nextDueDate(repeat, chore.dueDate?.toLocalDate() ?: today, today)
        val nextId = ChoreSchedule.nextAssignee(chore.assigneeId, members.map { it.uid }, chore.rotate)
        val nextName = members.firstOrNull { it.uid == nextId }?.displayName ?: chore.assigneeName
        repository.advanceRepeating(householdId, chore.id, nextId, nextName, nextDue.toNoonTimestamp(), me.uid)
        val due = nextDue.format(DateTimeFormatter.ofPattern("MMM d"))
        val message = if (nextId == me.uid) "${chore.title} done · next due $due" else "${chore.title} done · now $nextName's, due $due"
        _undoEvents.tryEmit(ChoreUndo(message, chore))
    }

    fun undo(undo: ChoreUndo) = repository.undoAdvance(householdId, undo.before)

    fun retry() {
        retry.value++
    }

    companion object {
        fun factory(householdId: String, me: Member) = viewModelFactory {
            initializer {
                val c = container()
                ChoresViewModel(c.choreRepository, c.settingsRepository, householdId, me, createSavedStateHandle())
            }
        }
    }
}

// ---------------- Create / edit chore ----------------

data class ChoreFormUiState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val title: String = "",
    val assigneeId: String = "",
    val dueDate: LocalDate = LocalDate.now().plusDays(1),
    val repeat: ChoreRepeat = ChoreRepeat.NONE,
    val rotate: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
    /** Edit mode: the creator and the household owner may delete (matches the rules). */
    val canDelete: Boolean = false
) {
    /** Lecture 8: disable Save until valid. */
    val canSave: Boolean
        get() = Validation.isValidChoreTitle(title) && assigneeId.isNotEmpty() && !isSaving && !isLoading
}

/** One ViewModel for "New chore" and "Edit chore" ([editChoreId] != null). All fields live in saved state. */
class ChoreFormViewModel(
    private val repository: ChoreRepository,
    private val householdId: String,
    private val me: Member,
    private val members: List<Member>,
    private val editChoreId: String?,
    private val isOwner: Boolean,
    private val savedState: SavedStateHandle
) : ViewModel() {

    // New chores: the id is generated once and kept in saved state, so a retry or rotation
    // reuses it and the chore can never be created twice. Edits are naturally idempotent.
    private val choreId: String = editChoreId
        ?: savedState["choreId"] ?: repository.newChoreId(householdId).also { savedState["choreId"] = it }

    private val _uiState = MutableStateFlow(readSavedState())
    val uiState: StateFlow<ChoreFormUiState> = _uiState.asStateFlow()

    init {
        if (editChoreId != null && savedState.get<Boolean>("loaded") != true) loadForEdit(editChoreId)
    }

    private fun readSavedState() = ChoreFormUiState(
        isEdit = editChoreId != null,
        isLoading = editChoreId != null && savedState.get<Boolean>("loaded") != true,
        title = savedState["title"] ?: "",
        assigneeId = savedState["assigneeId"] ?: me.uid,
        dueDate = savedState.get<Long>("dueEpochDay")?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusDays(1),
        repeat = ChoreRepeat.from(savedState["repeat"]),
        rotate = savedState["rotate"] ?: false,
        saved = savedState["saved"] ?: false,
        canDelete = editChoreId != null && (isOwner || savedState.get<String>("createdBy") == me.uid)
    )

    private fun loadForEdit(id: String) {
        viewModelScope.launch {
            try {
                val chore = repository.getChore(householdId, id)
                if (chore == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "This chore no longer exists.") }
                    return@launch
                }
                savedState["title"] = chore.title
                savedState["assigneeId"] = chore.assigneeId
                chore.dueDate?.let { savedState["dueEpochDay"] = it.toLocalDate().toEpochDay() }
                savedState["repeat"] = chore.repeat
                savedState["rotate"] = chore.rotate
                savedState["createdBy"] = chore.createdBy
                savedState["loaded"] = true
                _uiState.value = readSavedState()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.friendlyMessage()) }
            }
        }
    }

    fun onTitleChange(value: String) {
        val trimmed = value.take(80)
        savedState["title"] = trimmed
        _uiState.update { it.copy(title = trimmed, errorMessage = null) }
    }

    fun onAssigneeChange(uid: String) {
        savedState["assigneeId"] = uid
        _uiState.update { it.copy(assigneeId = uid, errorMessage = null) }
    }

    fun onDueDateChange(date: LocalDate) {
        savedState["dueEpochDay"] = date.toEpochDay()
        _uiState.update { it.copy(dueDate = date) }
    }

    fun onRepeatChange(repeat: ChoreRepeat) {
        savedState["repeat"] = repeat.value
        // Rotation only makes sense for repeating chores.
        if (repeat == ChoreRepeat.NONE) savedState["rotate"] = false
        _uiState.update { it.copy(repeat = repeat, rotate = if (repeat == ChoreRepeat.NONE) false else it.rotate) }
    }

    fun onRotateChange(rotate: Boolean) {
        savedState["rotate"] = rotate
        _uiState.update { it.copy(rotate = rotate) }
    }

    /** Called after the "Are you sure?" dialog. */
    fun delete() {
        val state = _uiState.value
        if (editChoreId == null || !state.canDelete || state.saved) return
        repository.deleteChore(householdId, editChoreId)
        savedState["saved"] = true
        _uiState.update { it.copy(saved = true) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || state.saved) return
        val assignee = members.firstOrNull { it.uid == state.assigneeId } ?: run {
            _uiState.update { it.copy(errorMessage = "Pick a roommate who's still in the household.") }
            return
        }
        val details = ChoreDetails(
            title = state.title,
            assigneeId = assignee.uid,
            assigneeName = assignee.displayName,
            dueDate = state.dueDate.toNoonTimestamp(),
            repeat = state.repeat.value,
            rotate = state.repeat != ChoreRepeat.NONE && state.rotate
        )
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (editChoreId != null) {
                repository.updateChore(householdId, editChoreId, details)
            } else if (!repository.alreadySaved(householdId, choreId)) {
                repository.createChore(householdId, choreId, details, me.uid, me.displayName)
            }
            savedState["saved"] = true
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        fun factory(householdId: String, me: Member, members: List<Member>, editChoreId: String?, isOwner: Boolean) = viewModelFactory {
            initializer {
                ChoreFormViewModel(container().choreRepository, householdId, me, members, editChoreId, isOwner, createSavedStateHandle())
            }
        }
    }
}
