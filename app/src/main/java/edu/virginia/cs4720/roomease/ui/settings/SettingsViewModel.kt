package edu.virginia.cs4720.roomease.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.data.AppSettings
import edu.virginia.cs4720.roomease.data.ChoreView
import edu.virginia.cs4720.roomease.data.HouseholdRepository
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.SplitDefault
import edu.virginia.cs4720.roomease.data.ThemeMode
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.JoinRequest
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val joinRequests: List<JoinRequest> = emptyList(),
    val decidingIds: Set<String> = emptySet(),
    val removingIds: Set<String> = emptySet(),
    val message: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val householdRepository: HouseholdRepository,
    private val householdId: String,
    private val myUid: String,
    isOwner: Boolean
) : ViewModel() {
    private val deciding = MutableStateFlow<Set<String>>(emptySet())
    private val removing = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    // Only the owner can list join requests (enforced by rules); members don't even ask.
    private val requests = if (isOwner) {
        householdRepository.observePendingRequests(householdId).catch {
            message.value = it.friendlyMessage()
            emit(emptyList())
        }
    } else flowOf(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, requests, deciding, removing, message) { s, r, d, rm, m ->
        SettingsUiState(settings = s, joinRequests = r, decidingIds = d, removingIds = rm, message = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setChoreView(view: ChoreView) = viewModelScope.launch { settingsRepository.setChoreView(view) }
    fun setSplitDefault(split: SplitDefault) = viewModelScope.launch { settingsRepository.setSplitDefault(split) }

    fun decide(request: JoinRequest, approve: Boolean, memberCount: Int) {
        if (request.uid in deciding.value) return
        if (approve && memberCount >= HouseholdRepository.MAX_MEMBERS) {
            message.value = "Households can have up to ${HouseholdRepository.MAX_MEMBERS} members."
            return
        }
        deciding.update { it + request.uid }
        viewModelScope.launch {
            try {
                householdRepository.decideJoinRequest(householdId, request.uid, myUid, approve)
                message.value = if (approve) "${request.displayName} can now join." else "Request declined."
            } catch (e: Exception) {
                message.value = e.friendlyMessage()
            } finally {
                deciding.update { it - request.uid }
            }
        }
    }

    /** Owner removes a roommate (after the confirmation dialog). Rules allow only the owner. */
    fun removeMember(member: Member) {
        if (member.uid == myUid || member.isOwner || member.uid in removing.value) return
        removing.update { it + member.uid }
        viewModelScope.launch {
            try {
                householdRepository.removeMember(householdId, member.uid)
                message.value = "${member.displayName} was removed from the household."
            } catch (e: Exception) {
                message.value = e.friendlyMessage()
            } finally {
                removing.update { it - member.uid }
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    companion object {
        fun factory(householdId: String, uid: String, isOwner: Boolean) = viewModelFactory {
            initializer {
                val c = container()
                SettingsViewModel(c.settingsRepository, c.householdRepository, householdId, uid, isOwner)
            }
        }
    }
}
