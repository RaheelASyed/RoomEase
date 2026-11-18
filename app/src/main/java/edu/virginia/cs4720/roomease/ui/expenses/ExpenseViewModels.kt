package edu.virginia.cs4720.roomease.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.data.ExpenseRepository
import edu.virginia.cs4720.roomease.data.HouseholdRepository
import edu.virginia.cs4720.roomease.data.ReceiptImageStore
import edu.virginia.cs4720.roomease.data.ReceiptTextRecognizer
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.SplitDefault
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.Expense
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.domain.ExpenseSplit
import edu.virginia.cs4720.roomease.domain.Money
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- Expenses tab: ledger ----------------

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val expenses: List<Expense> = emptyList(),
    /** uid -> cents; positive = they owe me, negative = I owe them. */
    val balances: Map<String, Long> = emptyMap(),
    /** Household ledger: uid -> paid minus share. */
    val householdNet: Map<String, Long> = emptyMap(),
    val receiptsOnDevice: Set<String> = emptySet(),
    val expandedId: String? = null
) {
    val youOwe: Long get() = balances.values.filter { it < 0 }.sumOf { -it }
    val youAreOwed: Long get() = balances.values.filter { it > 0 }.sum()
}

private sealed interface ExpenseLoad {
    data object Loading : ExpenseLoad
    data class Loaded(val expenses: List<Expense>, val receipts: Set<String>) : ExpenseLoad
    data class Failed(val message: String) : ExpenseLoad
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val repository: ExpenseRepository,
    private val receiptStore: ReceiptImageStore,
    private val householdId: String,
    private val myUid: String,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    private val load = retry.flatMapLatest {
        repository.observeExpenses(householdId)
            .map<List<Expense>, ExpenseLoad> { list ->
                ExpenseLoad.Loaded(list, list.filter { receiptStore.hasSavedReceipt(it.id) }.map { it.id }.toSet())
            }
            .flowOn(Dispatchers.IO) // file checks off the main thread
            .onStart { emit(ExpenseLoad.Loading) }
            .catch { emit(ExpenseLoad.Failed(it.friendlyMessage())) }
    }

    val uiState: StateFlow<ExpensesUiState> = combine(load, savedState.getStateFlow<String?>("expandedId", null)) { load, expanded ->
        when (load) {
            ExpenseLoad.Loading -> ExpensesUiState(isLoading = true)
            is ExpenseLoad.Failed -> ExpensesUiState(isLoading = false, errorMessage = load.message)
            is ExpenseLoad.Loaded -> ExpensesUiState(
                isLoading = false,
                expenses = load.expenses,
                balances = ExpenseSplit.balancesFor(myUid, load.expenses),
                householdNet = ExpenseSplit.householdNet(load.expenses),
                receiptsOnDevice = load.receipts,
                expandedId = expanded
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    /** Selected record survives rotation and process death. */
    fun toggleExpanded(id: String) {
        savedState["expandedId"] = if (savedState.get<String?>("expandedId") == id) null else id
    }

    fun receiptFile(expenseId: String): File = receiptStore.savedFile(expenseId)

    fun retry() {
        retry.value++
    }

    companion object {
        fun factory(householdId: String, uid: String) = viewModelFactory {
            initializer {
                val c = container()
                ExpensesViewModel(c.expenseRepository, c.receiptImageStore, householdId, uid, createSavedStateHandle())
            }
        }
    }
}

// ---------------- Add expense + receipt scan ----------------

sealed interface ScanStatus {
    data object None : ScanStatus
    data object Reading : ScanStatus
    data class Suggested(val message: String) : ScanStatus
    data class Unreadable(val message: String) : ScanStatus
}

data class AddExpenseUiState(
    val description: String = "",
    val merchant: String = "",
    val amountText: String = "",
    val selectedIds: Set<String> = emptySet(),
    val captured: Boolean = false,
    val captureVersion: Int = 0,
    val receiptAttached: Boolean = false,
    val scanStatus: ScanStatus = ScanStatus.None,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
) {
    val totalCents: Long? get() = Money.parseCents(amountText)
    val amountError: String? get() = if (amountText.isNotBlank() && totalCents == null) "Enter an amount like 12.50" else null
    val split: Map<String, Long> get() = totalCents?.let { ExpenseSplit.split(it, selectedIds) } ?: emptyMap()
    val canSave: Boolean
        get() = Validation.isValidExpenseDescription(description) && totalCents != null &&
            selectedIds.isNotEmpty() && !isSaving && scanStatus != ScanStatus.Reading
}

class AddExpenseViewModel(
    private val repository: ExpenseRepository,
    private val receiptStore: ReceiptImageStore,
    private val recognizer: ReceiptTextRecognizer,
    private val settingsRepository: SettingsRepository,
    private val householdId: String,
    private val me: Member,
    private val members: List<Member>,
    private val savedState: SavedStateHandle
) : ViewModel() {

    /** Same id across retries/rotation/process death -> the expense is written at most once. */
    val expenseId: String = savedState["expenseId"] ?: repository.newExpenseId(householdId).also { savedState["expenseId"] = it }

    /** The captured photo lives in app-private storage so the preview survives recreation. */
    val captureFile: File get() = receiptStore.pendingFile(expenseId)

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(
            description = savedState["description"] ?: "",
            merchant = savedState["merchant"] ?: "",
            amountText = savedState["amountText"] ?: "",
            selectedIds = savedState.get<ArrayList<String>>("selectedIds")?.toSet() ?: emptySet(),
            captured = savedState["captured"] ?: false,
            receiptAttached = savedState["receiptAttached"] ?: false,
            scanStatus = savedState.get<String>("scanMessage")?.let { msg ->
                if (savedState.get<Boolean>("scanOk") == true) ScanStatus.Suggested(msg) else ScanStatus.Unreadable(msg)
            } ?: ScanStatus.None,
            saved = savedState["saved"] ?: false
        )
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        // First open only: start from the "default split" setting (DataStore).
        if (!savedState.contains("selectedIds")) {
            viewModelScope.launch {
                val ids = when (settingsRepository.current().splitDefault) {
                    SplitDefault.ALL_ROOMMATES -> members.map { it.uid }
                    SplitDefault.MANUAL -> listOf(me.uid)
                }
                setSelected(ids.toSet())
            }
        }
        // Coming back after process death with a photo on disk: make sure it's still there.
        if (_uiState.value.captured && !captureFile.exists()) {
            savedState["captured"] = false
            savedState["receiptAttached"] = false
            _uiState.update { it.copy(captured = false, receiptAttached = false) }
        }
    }

    fun onDescriptionChange(value: String) {
        val v = value.take(80)
        savedState["description"] = v
        _uiState.update { it.copy(description = v, errorMessage = null) }
    }

    fun onMerchantChange(value: String) {
        val v = value.take(80)
        savedState["merchant"] = v
        _uiState.update { it.copy(merchant = v) }
    }

    fun onAmountChange(value: String) {
        val v = value.filter { it.isDigit() || it == '.' || it == ',' || it == '$' }.take(12)
        savedState["amountText"] = v
        _uiState.update { it.copy(amountText = v, errorMessage = null) }
    }

    fun toggleParticipant(uid: String) {
        val current = _uiState.value.selectedIds
        setSelected(if (uid in current) current - uid else current + uid)
    }

    fun selectAll() = setSelected(members.map { it.uid }.toSet())

    private fun setSelected(ids: Set<String>) {
        savedState["selectedIds"] = ArrayList(ids)
        _uiState.update { it.copy(selectedIds = ids) }
    }

    // ---- camera ----

    fun onPhotoCaptured() {
        savedState["captured"] = true
        _uiState.update { it.copy(captured = true, captureVersion = it.captureVersion + 1) }
    }

    fun retake() {
        receiptStore.discardPending(expenseId)
        savedState["captured"] = false
        savedState["receiptAttached"] = false
        savedState.remove<String>("scanMessage")
        _uiState.update { it.copy(captured = false, receiptAttached = false, scanStatus = ScanStatus.None) }
    }

    /** "Use photo": read it on-device, then pre-fill (but never lock) the form fields. */
    fun usePhoto() {
        if (_uiState.value.scanStatus == ScanStatus.Reading) return
        savedState["receiptAttached"] = true
        _uiState.update { it.copy(receiptAttached = true, scanStatus = ScanStatus.Reading) }
        viewModelScope.launch {
            val suggestion = try {
                withContext(Dispatchers.Default) { recognizer.suggest(captureFile) }
            } catch (_: Exception) {
                null
            }
            val (ok, message) = when {
                suggestion == null || suggestion.isEmpty ->
                    false to "We couldn't read this receipt. Enter the details below."
                suggestion.totalCents == null ->
                    false to "Found \"${suggestion.merchant}\" but no total. Enter the amount below."
                else -> true to "Filled in from your receipt. Check the details before saving."
            }
            suggestion?.merchant?.let { merchant ->
                onMerchantChange(merchant)
                if (_uiState.value.description.isBlank()) onDescriptionChange(merchant)
            }
            suggestion?.totalCents?.let { onAmountChange(Money.toInput(it)) }
            savedState["scanMessage"] = message
            savedState["scanOk"] = ok
            // The scan screen watches scanStatus and returns to the form when reading finishes.
            _uiState.update { it.copy(scanStatus = if (ok) ScanStatus.Suggested(message) else ScanStatus.Unreadable(message)) }
        }
    }

    fun removeReceipt() = retake()

    // ---- save ----

    fun save() {
        val state = _uiState.value
        val total = state.totalCents
        if (!state.canSave || state.saved || total == null) return
        if (state.selectedIds.size > HouseholdRepository.MAX_MEMBERS) {
            _uiState.update { it.copy(errorMessage = "Split between at most 8 roommates.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (!repository.alreadySaved(householdId, expenseId)) {
                repository.addExpense(
                    householdId = householdId,
                    expenseId = expenseId,
                    description = state.description,
                    merchant = state.merchant,
                    totalCents = total,
                    paidBy = me.uid,
                    paidByName = me.displayName,
                    participantIds = state.selectedIds.toList(),
                    hasReceipt = state.receiptAttached
                )
            }
            if (state.receiptAttached) withContext(Dispatchers.IO) { receiptStore.commit(expenseId) }
            savedState["saved"] = true
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    override fun onCleared() {
        // Leaving the form without saving: don't leave an orphaned photo behind.
        if (savedState.get<Boolean>("saved") != true) receiptStore.discardPending(expenseId)
    }

    companion object {
        fun factory(householdId: String, me: Member, members: List<Member>) = viewModelFactory {
            initializer {
                val c = container()
                AddExpenseViewModel(
                    c.expenseRepository, c.receiptImageStore, c.receiptTextRecognizer, c.settingsRepository,
                    householdId, me, members, createSavedStateHandle()
                )
            }
        }
    }
}
