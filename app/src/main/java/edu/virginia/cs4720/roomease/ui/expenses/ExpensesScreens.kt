package edu.virginia.cs4720.roomease.ui.expenses

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.virginia.cs4720.roomease.data.model.Expense
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.domain.ExpenseSplit
import edu.virginia.cs4720.roomease.domain.Money
import edu.virginia.cs4720.roomease.ui.chores.relativeDay
import edu.virginia.cs4720.roomease.ui.chores.toLocalDate
import edu.virginia.cs4720.roomease.ui.components.Avatar
import edu.virginia.cs4720.roomease.ui.components.EmptyState
import edu.virginia.cs4720.roomease.ui.components.ErrorState
import edu.virginia.cs4720.roomease.ui.components.LoadingState
import edu.virginia.cs4720.roomease.ui.components.PendingSyncLabel
import edu.virginia.cs4720.roomease.ui.components.PermissionExplainer
import edu.virginia.cs4720.roomease.ui.components.PrimaryButton
import edu.virginia.cs4720.roomease.ui.components.ScreenHeader
import edu.virginia.cs4720.roomease.ui.components.SectionCard
import edu.virginia.cs4720.roomease.ui.components.rememberPermissionGranted
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.theme.RoomEaseTheme
import java.io.File

// ---------------- Expenses tab ----------------

@Composable
fun ExpensesRoute(session: SessionState.Ready, onNewExpense: () -> Unit) {
    val viewModel: ExpensesViewModel = viewModel(factory = ExpensesViewModel.factory(session.householdId, session.uid))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpensesScreen(
        state = state,
        myUid = session.uid,
        myName = session.myName,
        members = session.members,
        nameOf = session::nameOf,
        receiptFile = viewModel::receiptFile,
        onToggleExpanded = viewModel::toggleExpanded,
        onNewExpense = onNewExpense,
        onRetry = viewModel::retry
    )
}

@Composable
fun ExpensesScreen(
    state: ExpensesUiState,
    myUid: String,
    myName: String,
    members: List<Member>,
    nameOf: (String) -> String,
    receiptFile: (String) -> File,
    onToggleExpanded: (String) -> Unit,
    onNewExpense: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewExpense,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add expense") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader("Expenses") { Avatar(myName, size = 36.dp, highlight = true) }
            when {
                state.isLoading -> LoadingState("Loading expenses…")
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SummaryCard(state.youOwe, state.youAreOwed) }
                    item { SectionTitle("Balances") }
                    item { BalancesCard(members.filter { it.uid != myUid }, state.balances) }
                    item { SectionTitle("Household ledger") }
                    if (state.expenses.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                                title = "No shared expenses yet",
                                body = "Add a bill or scan a receipt, pick who shares it, and RoomEase splits it to the cent.",
                                action = { OutlinedButton(onClick = onNewExpense, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add an expense") } }
                            )
                        }
                    } else {
                        items(state.expenses, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                expanded = state.expandedId == expense.id,
                                receiptOnDevice = expense.id in state.receiptsOnDevice,
                                receiptFile = receiptFile,
                                nameOf = nameOf,
                                onClick = { onToggleExpanded(expense.id) }
                            )
                        }
                        item { HouseholdNetCard(members, state.householdNet) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

/** Figma Money: "You owe | You're owed" summary. */
@Composable
private fun SummaryCard(youOwe: Long, youAreOwed: Long) {
    SectionCard {
        Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f)) {
                Text("You owe", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(youOwe), style = MaterialTheme.typography.headlineMedium)
            }
            VerticalDivider(Modifier.padding(horizontal = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("You're owed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(youAreOwed), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BalancesCard(others: List<Member>, balances: Map<String, Long>) {
    SectionCard {
        Column {
            if (others.isEmpty()) {
                Text(
                    "Invite roommates from Settings to start splitting.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            others.forEach { member ->
                val net = balances[member.uid] ?: 0L
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(member.displayName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                net < 0 -> "You owe ${Money.formatAbs(net)}"
                                net > 0 -> "Owes you ${Money.formatAbs(net)}"
                                else -> "Settled up"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (net > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    expanded: Boolean,
    receiptOnDevice: Boolean,
    receiptFile: (String) -> File,
    nameOf: (String) -> String,
    onClick: () -> Unit
) {
    SectionCard {
        Column(
            Modifier
                .clickable(onClickLabel = if (expanded) "Hide split" else "Show split", onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(expense.description, style = MaterialTheme.typography.titleMedium)
                    val payer = nameOf(expense.paidBy).let { if (it == "Former roommate") expense.paidByName else it }
                    val date = expense.createdAt?.toLocalDate()?.let { " · ${relativeDay(it)}" }.orEmpty()
                    Text(
                        "Paid by $payer$date · ${expense.participantIds.size} ${if (expense.participantIds.size == 1) "person" else "people"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (receiptOnDevice) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = "Receipt photo saved on this device", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                Text(Money.format(expense.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (expense.pendingSync) {
                Spacer(Modifier.size(6.dp))
                PendingSyncLabel()
            }
            if (expanded) {
                Spacer(Modifier.size(12.dp))
                HorizontalDivider()
                Spacer(Modifier.size(8.dp))
                if (expense.merchant.isNotBlank()) {
                    Text("Merchant: ${expense.merchant}", style = MaterialTheme.typography.bodyMedium)
                }
                ExpenseSplit.split(expense.totalCents, expense.participantIds).forEach { (uid, share) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(nameOf(uid), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(share), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (receiptOnDevice) {
                    Spacer(Modifier.size(8.dp))
                    ReceiptPhoto(
                        file = receiptFile(expense.id), version = 0, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium)
                    )
                    DeviceOnlyNote()
                } else if (expense.hasReceipt) {
                    Text(
                        "Receipt photo is on the phone that scanned it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HouseholdNetCard(members: List<Member>, net: Map<String, Long>) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Totals (paid − share)", style = MaterialTheme.typography.titleSmall)
            members.forEach { m ->
                val value = net[m.uid] ?: 0L
                Row(Modifier.fillMaxWidth()) {
                    Text(m.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (if (value > 0) "+" else if (value < 0) "−" else "") + Money.formatAbs(value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceOnlyNote() {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            "Photo stays on this device. Only the merchant and total are shared with your household.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------- Add expense ----------------

@Composable
fun AddExpenseRoute(
    session: SessionState.Ready,
    viewModelStoreOwner: ViewModelStoreOwner,
    onScanReceipt: () -> Unit,
    onDone: () -> Unit
) {
    val viewModel = addExpenseViewModel(session, viewModelStoreOwner)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    AddExpenseScreen(
        state = state,
        members = session.members,
        myUid = session.uid,
        receiptFile = viewModel.captureFile,
        onDescriptionChange = viewModel::onDescriptionChange,
        onMerchantChange = viewModel::onMerchantChange,
        onAmountChange = viewModel::onAmountChange,
        onToggleParticipant = viewModel::toggleParticipant,
        onSelectAll = viewModel::selectAll,
        onScanReceipt = onScanReceipt,
        onRemoveReceipt = viewModel::removeReceipt,
        onSave = viewModel::save,
        onBack = onDone
    )
}

@Composable
private fun addExpenseViewModel(session: SessionState.Ready, owner: ViewModelStoreOwner): AddExpenseViewModel {
    val me = session.members.firstOrNull { it.uid == session.uid } ?: Member(session.uid, session.myName)
    return viewModel(
        viewModelStoreOwner = owner,
        factory = AddExpenseViewModel.factory(session.householdId, me, session.members)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    state: AddExpenseUiState,
    members: List<Member>,
    myUid: String,
    receiptFile: File,
    onDescriptionChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onToggleParticipant: (String) -> Unit,
    onSelectAll: () -> Unit,
    onScanReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("New expense") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- receipt ----
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.receiptAttached && state.captured) {
                        ReceiptPhoto(
                            file = receiptFile, version = state.captureVersion, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium)
                        )
                        DeviceOnlyNote()
                        when (val scan = state.scanStatus) {
                            ScanStatus.Reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Reading receipt…", style = MaterialTheme.typography.bodyMedium)
                            }
                            is ScanStatus.Suggested -> Text(
                                scan.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            is ScanStatus.Unreadable -> Text(
                                scan.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            ScanStatus.None -> Unit
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRemoveReceipt(); onScanReceipt() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retake") }
                            TextButton(onClick = onRemoveReceipt, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove photo") }
                        }
                    } else {
                        OutlinedButton(onClick = onScanReceipt, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan receipt")
                        }
                        Text(
                            "Optional. RoomEase reads the merchant and total on your phone; the photo stays on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- details (always editable; manual entry works without a scan) ----
            OutlinedTextField(
                value = state.description, onValueChange = onDescriptionChange,
                label = { Text("What was it for?") }, placeholder = { Text("Groceries") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.merchant, onValueChange = onMerchantChange,
                label = { Text("Merchant (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.amountText, onValueChange = onAmountChange,
                label = { Text("Total") }, prefix = { Text("$") }, singleLine = true,
                isError = state.amountError != null,
                supportingText = { Text(state.amountError ?: "You paid this. Your roommates' shares are owed to you.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- split ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split between", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll, modifier = Modifier.heightIn(min = 48.dp)) { Text("All roommates") }
            }
            SectionCard {
                Column {
                    members.forEach { member ->
                        val selected = member.uid in state.selectedIds
                        val share = state.split[member.uid]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggleParticipant(member.uid) })
                                .padding(horizontal = 12.dp)
                                .semantics { stateDescription = if (selected) "Included" else "Not included" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (member.uid == myUid) "${member.displayName} (you)" else member.displayName,
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge
                            )
                            if (share != null) Text(Money.format(share), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (state.selectedIds.isEmpty()) {
                Text("Pick at least one person.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else if (state.totalCents != null && state.totalCents!! % state.selectedIds.size != 0L) {
                Text(
                    "Leftover cents go one each to people in a fixed order, so everyone's phone shows the same split.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save expense", onClick = onSave, enabled = state.canSave, loading = state.isSaving)
        }
    }
}

// ---------------- Receipt scan (CameraX) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanRoute(session: SessionState.Ready, viewModelStoreOwner: ViewModelStoreOwner, onFinished: () -> Unit) {
    val viewModel = addExpenseViewModel(session, viewModelStoreOwner)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val (granted, setGranted) = rememberPermissionGranted(Manifest.permission.CAMERA)
    var denied by rememberSaveable { mutableStateOf(false) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        setGranted(ok)
        denied = !ok
    }

    // Reading finished (with or without a suggestion): back to the editable form.
    LaunchedEffect(state.scanStatus) {
        if (state.scanStatus is ScanStatus.Suggested || state.scanStatus is ScanStatus.Unreadable) onFinished()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(if (state.captured) "Review receipt" else "Scan receipt") },
                navigationIcon = { IconButton(onClick = onFinished) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to expense") } },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.captured -> Column(Modifier.fillMaxSize()) {
                    ReceiptPhoto(
                        file = viewModel.captureFile,
                        version = state.captureVersion,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                    )
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeviceOnlyNote()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = viewModel::retake,
                                enabled = state.scanStatus != ScanStatus.Reading,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                            ) { Text("Retake") }
                            PrimaryButton(
                                "Use photo",
                                onClick = viewModel::usePhoto,
                                loading = state.scanStatus == ScanStatus.Reading,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                !granted -> PermissionExplainer(
                    title = "Camera access",
                    reason = "RoomEase uses the camera only to photograph receipts. The photo is read and kept on this phone.",
                    denied = denied,
                    onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                    fallbackLabel = "Enter the expense by hand",
                    onFallback = onFinished,
                    modifier = Modifier.padding(16.dp)
                )
                else -> {
                    CameraCapture(
                        outputFile = viewModel.captureFile,
                        onCaptured = viewModel::onPhotoCaptured,
                        onError = { cameraError = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    cameraError?.let {
                        SectionCard(Modifier.align(Alignment.TopCenter).padding(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onFinished, modifier = Modifier.heightIn(min = 48.dp)) { Text("Enter by hand") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7, fontScale = 1.5f)
@Composable
private fun ExpensesPreview() {
    val members = listOf(Member("me", "Raheel Syed", Member.ROLE_OWNER), Member("r", "Rayyan Alam"), Member("a", "Arjun Rao"))
    val expenses = listOf(
        Expense("1", "Groceries", "Kroger", 6450, "r", "Rayyan Alam", listOf("me", "r", "a"), pendingSync = true),
        Expense("2", "Internet", "", 9000, "me", "Raheel Syed", listOf("me", "r", "a"))
    )
    RoomEaseTheme {
        ExpensesScreen(
            state = ExpensesUiState(
                isLoading = false, expenses = expenses,
                balances = ExpenseSplit.balancesFor("me", expenses),
                householdNet = ExpenseSplit.householdNet(expenses),
                expandedId = "1"
            ),
            myUid = "me", myName = "Raheel Syed", members = members,
            nameOf = { uid -> if (uid == "me") "You" else members.first { it.uid == uid }.displayName },
            receiptFile = { File("none") }, onToggleExpanded = {}, onNewExpense = {}, onRetry = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4F5F7)
@Composable
private fun AddExpensePreview() {
    RoomEaseTheme {
        AddExpenseScreen(
            state = AddExpenseUiState(description = "Groceries", amountText = "10.00", selectedIds = setOf("me", "r", "a")),
            members = listOf(Member("me", "Raheel Syed"), Member("r", "Rayyan Alam"), Member("a", "Arjun Rao")),
            myUid = "me", receiptFile = File("none"),
            onDescriptionChange = {}, onMerchantChange = {}, onAmountChange = {}, onToggleParticipant = {}, onSelectAll = {},
            onScanReceipt = {}, onRemoveReceipt = {}, onSave = {}, onBack = {}
        )
    }
}

