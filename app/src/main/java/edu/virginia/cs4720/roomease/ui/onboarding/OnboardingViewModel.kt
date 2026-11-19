package edu.virginia.cs4720.roomease.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.data.HouseholdRepository
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.JoinRequest
import edu.virginia.cs4720.roomease.data.model.UserProfile
import edu.virginia.cs4720.roomease.domain.InviteCodes
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingTab { CREATE, JOIN }

/** Status of this account's join request, when it has one. */
sealed interface JoinStatus {
    data object None : JoinStatus
    data object Waiting : JoinStatus
    data object Joining : JoinStatus
    data object Declined : JoinStatus
    data object Missing : JoinStatus
}

data class OnboardingUiState(
    val tab: OnboardingTab = OnboardingTab.CREATE,
    val householdName: String = "",
    val inviteCode: String = "",
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val joinStatus: JoinStatus = JoinStatus.None,
    val pendingHouseholdName: String = "",
    /** This account was removed from its previous household. */
    val wasRemoved: Boolean = false
) {
    val canCreate: Boolean get() = Validation.isValidHouseholdName(householdName) && !isWorking
    val canJoin: Boolean get() = InviteCodes.isWellFormed(inviteCode) && !isWorking
}

private data class FormState(
    val tab: OnboardingTab,
    val householdName: String,
    val inviteCode: String,
    val isWorking: Boolean,
    val errorMessage: String?,
    val wasRemoved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val repository: HouseholdRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val profile = MutableStateFlow<UserProfile?>(null)
    private val form = MutableStateFlow(
        FormState(
            tab = savedState.get<String>("tab")?.let { OnboardingTab.valueOf(it) } ?: OnboardingTab.CREATE,
            householdName = savedState["householdName"] ?: "",
            inviteCode = savedState["inviteCode"] ?: "",
            isWorking = false,
            errorMessage = null,
            wasRemoved = savedState["wasRemoved"] ?: false
        )
    )
    private var staleCheckDone = false
    private var joinAttemptedFor: String? = null

    /** Watches joinRequests/{my uid} in the household I asked to join. */
    private val joinStatus = profile
        .map { it?.let { p -> p.pendingHouseholdId?.let { hid -> p to hid } } }
        .distinctUntilChanged()
        .flatMapLatest { pair ->
            if (pair == null) flowOf<JoinStatus>(JoinStatus.None)
            else repository.observeMyJoinRequest(pair.second, pair.first.uid).map { request ->
                when (request?.status) {
                    null -> JoinStatus.Missing
                    JoinRequest.STATUS_APPROVED -> JoinStatus.Joining
                    JoinRequest.STATUS_DECLINED -> JoinStatus.Declined
                    else -> JoinStatus.Waiting
                }
            }.catch { emit(JoinStatus.Missing) }
        }

    val uiState: StateFlow<OnboardingUiState> = combine(form, joinStatus) { f, status ->
        OnboardingUiState(
            tab = f.tab,
            householdName = f.householdName,
            inviteCode = f.inviteCode,
            isWorking = f.isWorking,
            errorMessage = f.errorMessage,
            joinStatus = status,
            pendingHouseholdName = savedState["pendingHouseholdName"] ?: "",
            wasRemoved = f.wasRemoved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    init {
        // Owner approved -> finish joining automatically (writes my member doc; rules check the approval).
        joinStatus.onEach { status ->
            val p = profile.value ?: return@onEach
            val hid = p.pendingHouseholdId ?: return@onEach
            if (status == JoinStatus.Joining && joinAttemptedFor != hid) {
                joinAttemptedFor = hid
                completeJoin(p, hid)
            }
        }.launchIn(viewModelScope)
    }

    fun onProfile(value: UserProfile) {
        profile.value = value
        // Still pointing at a household we're not a member of: we were removed. Clear the pointer.
        if (value.householdId != null && !staleCheckDone) {
            staleCheckDone = true
            viewModelScope.launch {
                try {
                    if (repository.clearStaleHousehold(value)) {
                        savedState["wasRemoved"] = true
                        form.update { it.copy(wasRemoved = true) }
                    }
                } catch (_: Exception) {
                    staleCheckDone = false // offline: try again on the next profile update
                }
            }
        }
    }

    fun onTabChange(tab: OnboardingTab) {
        savedState["tab"] = tab.name
        form.update { it.copy(tab = tab, errorMessage = null) }
    }

    fun onHouseholdNameChange(value: String) {
        savedState["householdName"] = value
        form.update { it.copy(householdName = value, errorMessage = null) }
    }

    fun onInviteCodeChange(value: String) {
        val code = InviteCodes.normalize(value).take(InviteCodes.LENGTH)
        savedState["inviteCode"] = code
        form.update { it.copy(inviteCode = code, errorMessage = null) }
    }

    fun createHousehold() {
        val p = profile.value ?: return
        val f = form.value
        if (f.isWorking || !Validation.isValidHouseholdName(f.householdName)) return
        form.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.createHousehold(p, f.householdName)
                // Session now sees membership and shows the main app.
            } catch (e: Exception) {
                form.update { it.copy(isWorking = false, errorMessage = e.friendlyMessage()) }
            }
        }
    }

    fun requestToJoin() {
        val p = profile.value ?: return
        val f = form.value
        if (f.isWorking || !InviteCodes.isWellFormed(f.inviteCode)) return
        form.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val invite = repository.lookupInvite(f.inviteCode)
                if (invite == null) {
                    form.update { it.copy(isWorking = false, errorMessage = "No household uses that code. Check it with your roommate.") }
                    return@launch
                }
                savedState["pendingHouseholdName"] = invite.householdName
                repository.requestToJoin(p, invite)
                form.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                form.update { it.copy(isWorking = false, errorMessage = e.friendlyMessage()) }
            }
        }
    }

    fun cancelRequest() {
        val p = profile.value ?: return
        val hid = p.pendingHouseholdId ?: return
        form.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.cancelJoinRequest(hid, p.uid)
                joinAttemptedFor = null
                form.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                form.update { it.copy(isWorking = false, errorMessage = e.friendlyMessage()) }
            }
        }
    }

    fun retryJoin() {
        val p = profile.value ?: return
        val hid = p.pendingHouseholdId ?: return
        completeJoin(p, hid)
    }

    private fun completeJoin(p: UserProfile, hid: String) {
        form.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.completeJoin(p, hid)
            } catch (e: Exception) {
                form.update { it.copy(isWorking = false, errorMessage = e.friendlyMessage()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OnboardingViewModel(container().householdRepository, createSavedStateHandle()) }
        }
    }
}
