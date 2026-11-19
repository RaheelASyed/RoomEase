package edu.virginia.cs4720.roomease.ui.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import edu.virginia.cs4720.roomease.domain.ChoreBoard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.domain.ChoreRepeat
import edu.virginia.cs4720.roomease.domain.CompletedChore
import edu.virginia.cs4720.roomease.domain.toLocalDate
import edu.virginia.cs4720.roomease.domain.toNoonTimestamp
import edu.virginia.cs4720.roomease.ui.components.Avatar
import edu.virginia.cs4720.roomease.ui.components.EmptyState
import edu.virginia.cs4720.roomease.ui.components.ErrorState
import edu.virginia.cs4720.roomease.ui.components.LoadingState
import edu.virginia.cs4720.roomease.ui.components.PendingSyncLabel
import edu.virginia.cs4720.roomease.ui.components.PrimaryButton
import edu.virginia.cs4720.roomease.ui.components.ProfileAvatarButton
import edu.virginia.cs4720.roomease.ui.components.ScreenHeader
import edu.virginia.cs4720.roomease.ui.components.SectionCard
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------------- Chores tab ----------------

@Composable
fun ChoresRoute(
    session: SessionState.Ready,
    onNewChore: () -> Unit,
    onEditChore: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: ChoresViewModel = viewModel(factory = ChoresViewModel.factory(session.householdId, session.me))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Lecture 8 "User control": completing a repeating chore can be undone from a snackbar.
    LaunchedEffect(viewModel) {
        viewModel.undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(event.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
        }
    }
    ChoresScreen(
        state = state,
        myUid = session.uid,
        myName = session.myName,
        snackbarHostState = snackbarHostState,
        labelFor = { uid, fallback -> if (uid == session.uid) "You" else session.realNameOf(uid) ?: fallback },
        avatarNameFor = { chore -> session.realNameOf(chore.assigneeId) ?: chore.assigneeName },
        avatarNameForUid = { uid -> session.realNameOf(uid) ?: "?" },
        onFilterChange = viewModel::setFilter,
        onToggle = { chore -> viewModel.toggle(chore, session.members) },
        onEdit = { chore -> onEditChore(chore.id) },
        onNewChore = onNewChore,
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::retry
    )
}

@Composable
fun ChoresScreen(
    state: ChoresUiState,
    myUid: String,
    myName: String,
    snackbarHostState: SnackbarHostState,
    labelFor: (uid: String, fallback: String) -> String,
    avatarNameFor: (Chore) -> String,
    avatarNameForUid: (uid: String) -> String,
    onFilterChange: (ChoreView) -> Unit,
    onToggle: (Chore) -> Unit,
    onEdit: (Chore) -> Unit,
    onNewChore: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Primary action in the thumb zone (Lecture 8), styled like the mockup's "+ New chore".
            ExtendedFloatingActionButton(
                onClick = onNewChore,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New chore") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader("Chores") { ProfileAvatarButton(myName, onClick = onOpenSettings) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ChoreView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.filter == view,
                        onClick = { onFilterChange(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, ChoreView.entries.size),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(if (view == ChoreView.MINE) "My chores" else "All chores") }
                }
            }
            val board = state.board
            when {
                state.isLoading || (board == null && state.errorMessage == null) -> LoadingState("Loading chores…")
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                board != null && board.isEmpty && board.weekDone == 0 -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = if (state.filter == ChoreView.MINE) "Nothing assigned to you" else "No chores yet",
                    body = if (state.filter == ChoreView.MINE) "Switch to All chores, or add one for yourself."
                    else "Add the first chore and assign it to a roommate.",
                    action = { OutlinedButton(onClick = onNewChore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add a chore") } }
                )
                board != null -> {
                    @Composable
                    fun ChoreItem(chore: Chore, modifier: Modifier) = ChoreRow(
                        chore = chore,
                        isMine = chore.assigneeId == myUid,
                        assigneeLabel = labelFor(chore.assigneeId, chore.assigneeName),
                        assigneeAvatarName = avatarNameFor(chore),
                        lastDoneLabel = chore.lastCompletedBy?.let { labelFor(it, "someone") },
                        onToggle = { onToggle(chore) },
                        onEdit = { onEdit(chore) },
                        modifier = modifier
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "progress") { WeekProgressCard(board) }
                        if (board.thisWeek.isEmpty()) {
                            item(key = "week-empty") {
                                Text(
                                    "Nothing left this week 🎉",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp).animateItem()
                                )
                            }
                        }
                        // Stable keys + animateItem(): a chore that changes section slides there
                        // instead of jumping (Lecture 8 Doherty threshold: immediate, visible feedback).
                        items(board.thisWeek, key = { it.id }) { ChoreItem(it, Modifier.animateItem()) }
                        if (board.upcoming.isNotEmpty()) {
                            item(key = "header-upcoming") { SectionHeader("Upcoming", Modifier.animateItem()) }
                            items(board.upcoming, key = { it.id }) { ChoreItem(it, Modifier.animateItem()) }
                        }
                        if (board.completed.isNotEmpty()) {
                            item(key = "header-completed") { SectionHeader("Completed", Modifier.animateItem()) }
                            items(board.completed, key = { it.key }) { entry ->
                                if (entry.repeating) {
                                    RepeatCompletedRow(
                                        entry = entry,
                                        completedByMe = entry.completedBy == myUid,
                                        completedByLabel = labelFor(entry.completedBy, "Someone"),
                                        completedByAvatarName = avatarNameForUid(entry.completedBy),
                                        nextAssigneeLabel = labelFor(entry.chore.assigneeId, entry.chore.assigneeName),
                                        onEdit = { onEdit(entry.chore) },
                                        modifier = Modifier.animateItem()
                                    )
                                } else {
                                    ChoreItem(entry.chore, Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() }
    )
}

/** Sunday–Saturday progress, like the mockup's "This week · 2 of 5 chores done". */
@Composable
private fun WeekProgressCard(board: ChoreBoard) {
    val range = "${board.weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${board.weekEnd.format(DateTimeFormatter.ofPattern("MMM d"))}"
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week · $range", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${board.weekDone} of ${board.weekTotal} chores done", style = MaterialTheme.typography.titleLarge)
            // No gap or end "stop" dot: one continuous bar.
            LinearProgressIndicator(
                progress = { if (board.weekTotal == 0) 0f else board.weekDone / board.weekTotal.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

/**
 * Your chores have a checkbox (only the checkbox completes it). Someone else's show their avatar
 * in the same 48dp slot instead and can't be completed by you. Completed one-off chores are greyed.
 */
@Composable
private fun ChoreRow(
    chore: Chore,
    isMine: Boolean,
    assigneeLabel: String,
    assigneeAvatarName: String,
    lastDoneLabel: String?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val due = chore.dueDate?.toLocalDate()
    val overdue = !chore.done && due != null && due.isBefore(LocalDate.now())
    val repeat = ChoreRepeat.from(chore.repeat)
    SectionCard(modifier.alpha(if (chore.done) 0.55f else 1f)) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isMine) {
                    Checkbox(
                        checked = chore.done,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.semantics {
                            contentDescription = if (chore.done) "Mark ${chore.title} not done" else "Mark ${chore.title} done"
                        }
                    )
                } else {
                    Avatar(assigneeAvatarName, size = 36.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(chore.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(assigneeLabel)
                        append(" · ")
                        append(
                            when {
                                chore.done -> "Completed" + (chore.completedAt?.toLocalDate()?.let { " ${relativeDay(it)}" } ?: "")
                                due == null -> "No due date"
                                overdue -> "Overdue · ${relativeDay(due)}"
                                else -> "Due ${relativeDay(due)}"
                            }
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (repeat != ChoreRepeat.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                append(repeat.label)
                                if (chore.rotate) append(" · Rotates")
                                val last = chore.lastCompletedAt?.toLocalDate()
                                if (lastDoneLabel != null && last != null) append(" · Last done by $lastDoneLabel ${relativeDay(last)}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (chore.pendingSync) {
                    Spacer(Modifier.size(4.dp))
                    PendingSyncLabel()
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${chore.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A repeating chore completed this week. The chore itself has already moved on to its next due
 * date (it's listed above too), so this row is a greyed record of who did it and when.
 * It can't be unticked here; the Undo snackbar right after completing is the way back.
 */
@Composable
private fun RepeatCompletedRow(
    entry: CompletedChore,
    completedByMe: Boolean,
    completedByLabel: String,
    completedByAvatarName: String,
    nextAssigneeLabel: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chore = entry.chore
    val repeat = ChoreRepeat.from(chore.repeat)
    val nextDue = chore.dueDate?.toLocalDate()
    SectionCard(modifier.alpha(0.55f)) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (completedByMe) {
                    Icon(Icons.Filled.CheckBox, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Avatar(completedByAvatarName, size = 36.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(chore.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$completedByLabel · Completed" + (entry.completedAt?.toLocalDate()?.let { " ${relativeDay(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        buildString {
                            append(repeat.label)
                            append(" · Next: ")
                            append(nextAssigneeLabel)
                            if (nextDue != null) append(", ${relativeDay(nextDue)}")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${chore.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- Create / edit chore ----------------

@Composable
fun ChoreFormRoute(session: SessionState.Ready, editChoreId: String?, onDone: () -> Unit) {
    val viewModel: ChoreFormViewModel = viewModel(
        factory = ChoreFormViewModel.factory(session.householdId, session.me, session.members, editChoreId, session.isOwner)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    ChoreFormScreen(
        state = state,
        members = session.members,
        myUid = session.uid,
        onTitleChange = viewModel::onTitleChange,
        onAssigneeChange = viewModel::onAssigneeChange,
        onDueDateChange = viewModel::onDueDateChange,
        onRepeatChange = viewModel::onRepeatChange,
        onRotateChange = viewModel::onRotateChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoreFormScreen(
    state: ChoreFormUiState,
    members: List<Member>,
    myUid: String,
    onTitleChange: (String) -> Unit,
    onAssigneeChange: (String) -> Unit,
    onDueDateChange: (LocalDate) -> Unit,
    onRepeatChange: (ChoreRepeat) -> Unit,
    onRotateChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${state.title}\"?") },
            text = { Text("Are you sure? It will be removed for everyone in the household. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit chore" else "New chore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingState("Loading chore…", Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Chore") },
                placeholder = { Text("Take out trash") },
                singleLine = true,
                supportingText = { Text("${state.title.length}/80") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Due date", style = MaterialTheme.typography.titleSmall)
            // Lecture 8 "Recognition over recall": a date PICKER, not a text field demanding a format.
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(state.dueDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
            }

            Text("Repeat", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoreRepeat.entries.forEach { option ->
                    FilterChip(
                        selected = state.repeat == option,
                        onClick = { onRepeatChange(option) },
                        label = { Text(option.label) },
                        leadingIcon = if (option != ChoreRepeat.NONE && state.repeat == option) {
                            { Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                        // Chips keep their 32dp look; Material already gives them a 48dp touch target.
                    )
                }
            }
            if (state.repeat != ChoreRepeat.NONE) {
                SectionCard {
                    Row(
                        Modifier
                            .toggleable(
                                value = state.rotate,
                                enabled = members.size > 1,
                                role = Role.Switch,
                                onValueChange = onRotateChange
                            )
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Rotate between roommates", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (members.size > 1) "When it's completed, the next roommate gets it with the next due date."
                                else "Invite roommates to rotate this chore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = state.rotate, onCheckedChange = null, enabled = members.size > 1)
                    }
                }
            }

            Text(if (state.rotate) "Assign first to" else "Assign to", style = MaterialTheme.typography.titleSmall)
            SectionCard {
                Column(Modifier.selectableGroup()) {
                    members.forEach { member ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(
                                    selected = state.assigneeId == member.uid,
                                    onClick = { onAssigneeChange(member.uid) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(member.displayName, size = 32.dp, highlight = member.uid == myUid)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                if (member.uid == myUid) "${member.displayName} (you)" else member.displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            RadioButton(selected = state.assigneeId == member.uid, onClick = null)
                        }
                    }
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                if (state.isEdit) "Save changes" else "Save chore",
                onClick = onSave,
                enabled = state.canSave,
                loading = state.isSaving
            )
            if (state.isEdit) {
                // Destructive action last, away from Save, behind a confirmation (Lecture 8).
                if (state.canDelete) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete chore")
                    }
                } else {
                    Text(
                        "Only the person who created this chore or the household owner can delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val today = remember { LocalDate.now() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDueDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

// ---------------- helpers ----------------

fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    today.minusDays(1) -> "yesterday"
    else -> if (date.isAfter(today) && date.isBefore(today.plusDays(7))) date.format(DateTimeFormatter.ofPattern("EEEE"))
    else date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun previewTimestamp(daysFromToday: Long): Timestamp = LocalDate.now().plusDays(daysFromToday).toNoonTimestamp()

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, fontScale = 1.5f)
@Composable
private fun ChoresPreview() {
    RoomEaseTheme {
        ChoresScreen(
            state = ChoresUiState(
                isLoading = false,
                filter = ChoreView.ALL,
                board = ChoreBoard.build(
                    listOf(
                        Chore("1", "Sweep", "me", "Raheel Syed", dueDate = previewTimestamp(1), repeat = "weekly", rotate = true,
                            lastCompletedBy = "a", lastCompletedAt = previewTimestamp(0)),
                        Chore("2", "Take out trash", "a", "Ali Khokhar", dueDate = previewTimestamp(2), pendingSync = true),
                        Chore("4", "Deep clean fridge", "a", "Ali Khokhar", dueDate = previewTimestamp(20)),
                        Chore("3", "Clean bathroom", "me", "Raheel Syed", dueDate = previewTimestamp(-1), done = true, completedAt = previewTimestamp(0))
                    ),
                    onlyFor = null,
                    today = LocalDate.now()
                )
            ),
            myUid = "me",
            myName = "Raheel Syed",
            snackbarHostState = remember { SnackbarHostState() },
            labelFor = { uid, fallback -> if (uid == "me") "You" else if (uid == "a") "Ali Khokhar" else fallback },
            avatarNameFor = { it.assigneeName },
            avatarNameForUid = { if (it == "me") "Raheel Syed" else "Ali Khokhar" },
            onFilterChange = {}, onToggle = {}, onEdit = {}, onNewChore = {}, onOpenSettings = {}, onRetry = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, heightDp = 1100)
@Composable
private fun ChoreFormPreview() {
    RoomEaseTheme {
        ChoreFormScreen(
            state = ChoreFormUiState(title = "Vacuum living room", assigneeId = "a", repeat = ChoreRepeat.WEEKLY, rotate = true),
            members = listOf(Member("me", "Raheel Syed", Member.ROLE_OWNER), Member("a", "Ali Khokhar")),
            myUid = "me",
            onTitleChange = {}, onAssigneeChange = {}, onDueDateChange = {}, onRepeatChange = {}, onRotateChange = {},
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}

