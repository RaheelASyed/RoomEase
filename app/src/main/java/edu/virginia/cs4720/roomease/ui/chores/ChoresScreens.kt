package edu.virginia.cs4720.roomease.ui.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.model.Chore
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.ui.components.Avatar
import edu.virginia.cs4720.roomease.ui.components.EmptyState
import edu.virginia.cs4720.roomease.ui.components.ErrorState
import edu.virginia.cs4720.roomease.ui.components.LoadingState
import edu.virginia.cs4720.roomease.ui.components.PendingSyncLabel
import edu.virginia.cs4720.roomease.ui.components.PrimaryButton
import edu.virginia.cs4720.roomease.ui.components.ScreenHeader
import edu.virginia.cs4720.roomease.ui.components.SectionCard
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

// ---------------- Chores tab ----------------

@Composable
fun ChoresRoute(session: SessionState.Ready, onNewChore: () -> Unit) {
    val viewModel: ChoresViewModel = viewModel(factory = ChoresViewModel.factory(session.householdId, session.uid))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChoresScreen(
        state = state,
        myUid = session.uid,
        myName = session.myName,
        nameOf = session::nameOf,
        onFilterChange = viewModel::setFilter,
        onToggle = viewModel::setDone,
        onNewChore = onNewChore,
        onRetry = viewModel::retry
    )
}

@Composable
fun ChoresScreen(
    state: ChoresUiState,
    myUid: String,
    myName: String,
    nameOf: (String) -> String,
    onFilterChange: (ChoreView) -> Unit,
    onToggle: (Chore, Boolean) -> Unit,
    onNewChore: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
            ScreenHeader("Chores") { Avatar(myName, size = 36.dp, highlight = true) }
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
            when {
                state.isLoading -> LoadingState("Loading chores…")
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                state.chores.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = if (state.filter == ChoreView.MINE) "Nothing assigned to you" else "No chores yet",
                    body = if (state.filter == ChoreView.MINE) "Switch to All chores, or add one for yourself."
                    else "Add the first chore and assign it to a roommate.",
                    action = { OutlinedButton(onClick = onNewChore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add a chore") } }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { ProgressCard(state.doneCount, state.totalCount) }
                    // Lecture 6: LazyColumn composes only what's on screen.
                    items(state.chores, key = { it.id }) { chore ->
                        ChoreRow(
                            chore = chore,
                            canComplete = chore.assigneeId == myUid,
                            assigneeLabel = nameOf(chore.assigneeId).let { if (it == "Former roommate") chore.assigneeName else it },
                            onToggle = { onToggle(chore, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$done of $total chores done", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChoreRow(chore: Chore, canComplete: Boolean, assigneeLabel: String, onToggle: (Boolean) -> Unit) {
    val due = chore.dueDate?.toLocalDate()
    val overdue = !chore.done && due != null && due.isBefore(LocalDate.now())
    SectionCard {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox guarantees a 48dp touch target. Only the assignee can tick it.
            Checkbox(
                checked = chore.done,
                onCheckedChange = if (canComplete) onToggle else null,
                enabled = canComplete,
                modifier = Modifier.semantics {
                    contentDescription = "Mark ${chore.title} done"
                    if (!canComplete) stateDescription = "Only $assigneeLabel can complete this"
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    chore.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (chore.done) TextDecoration.LineThrough else null
                )
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
                if (chore.pendingSync) {
                    Spacer(Modifier.size(4.dp))
                    PendingSyncLabel()
                }
            }
            Avatar(assigneeLabel, size = 36.dp, highlight = canComplete)
        }
    }
}

// ---------------- Add chore ----------------

@Composable
fun AddChoreRoute(session: SessionState.Ready, onDone: () -> Unit) {
    val me = session.members.firstOrNull { it.uid == session.uid } ?: Member(session.uid, session.myName)
    val viewModel: AddChoreViewModel = viewModel(factory = AddChoreViewModel.factory(session.householdId, me, session.members))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    AddChoreScreen(
        state = state,
        members = session.members,
        myUid = session.uid,
        onTitleChange = viewModel::onTitleChange,
        onAssigneeChange = viewModel::onAssigneeChange,
        onDueDateChange = viewModel::onDueDateChange,
        onSave = viewModel::save,
        onBack = onDone
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoreScreen(
    state: AddChoreUiState,
    members: List<Member>,
    myUid: String,
    onTitleChange: (String) -> Unit,
    onAssigneeChange: (String) -> Unit,
    onDueDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("New chore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
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

            Text("Assign to", style = MaterialTheme.typography.titleSmall)
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
            PrimaryButton("Save chore", onClick = onSave, enabled = state.canSave, loading = state.isSaving)
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

fun Timestamp.toLocalDate(): LocalDate = toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    today.minusDays(1) -> "yesterday"
    else -> if (date.isAfter(today) && date.isBefore(today.plusDays(7))) date.format(DateTimeFormatter.ofPattern("EEEE"))
    else date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun previewTimestamp(daysFromToday: Long): Timestamp =
    Timestamp(Date.from(LocalDate.now().plusDays(daysFromToday).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()))

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, fontScale = 1.5f)
@Composable
private fun ChoresPreview() {
    RoomEaseTheme {
        ChoresScreen(
            state = ChoresUiState(
                isLoading = false,
                filter = ChoreView.ALL,
                chores = listOf(
                    Chore("1", "Mopping", "me", "Raheel", dueDate = previewTimestamp(0)),
                    Chore("2", "Take out trash", "a", "Arjun", dueDate = previewTimestamp(1), pendingSync = true),
                    Chore("3", "Clean bathrooms", "me", "Raheel", dueDate = previewTimestamp(-1), done = true)
                ),
                doneCount = 1,
                totalCount = 3
            ),
            myUid = "me",
            myName = "Raheel Syed",
            nameOf = { if (it == "me") "You" else "Arjun Rao" },
            onFilterChange = {}, onToggle = { _, _ -> }, onNewChore = {}, onRetry = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Composable
private fun AddChorePreview() {
    RoomEaseTheme {
        AddChoreScreen(
            state = AddChoreUiState(title = "Vacuum living room", assigneeId = "a"),
            members = listOf(Member("me", "Raheel Syed", Member.ROLE_OWNER), Member("a", "Arjun Rao")),
            myUid = "me",
            onTitleChange = {}, onAssigneeChange = {}, onDueDateChange = {}, onSave = {}, onBack = {}
        )
    }
}
