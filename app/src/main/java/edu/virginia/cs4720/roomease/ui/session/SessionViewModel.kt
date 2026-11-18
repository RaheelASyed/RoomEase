package edu.virginia.cs4720.roomease.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.virginia.cs4720.roomease.AppContainer
import edu.virginia.cs4720.roomease.RoomEaseApp
import edu.virginia.cs4720.roomease.data.AuthRepository
import edu.virginia.cs4720.roomease.data.HouseholdRepository
import edu.virginia.cs4720.roomease.data.friendlyMessage
import edu.virginia.cs4720.roomease.data.model.Household
import edu.virginia.cs4720.roomease.data.model.Member
import edu.virginia.cs4720.roomease.data.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Where the signed-in (or signed-out) user belongs. One sealed type = the compiler checks every case. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsHousehold(val profile: UserProfile) : SessionState
    data class Ready(
        val profile: UserProfile,
        val household: Household,
        val members: List<Member>
    ) : SessionState {
        val uid: String get() = profile.uid
        val householdId: String get() = household.id
        val isOwner: Boolean get() = household.ownerId == profile.uid
        val myName: String get() = members.firstOrNull { it.uid == uid }?.displayName ?: profile.displayName
        fun nameOf(uid: String): String =
            if (uid == this.uid) "You" else members.firstOrNull { it.uid == uid }?.displayName ?: "Former roommate"
    }
    data class Error(val message: String) : SessionState
}

/**
 * Lecture 11 "Auth state drives navigation": auth user -> users/{uid} -> real membership doc.
 * The users doc's householdId is only a hint for where to look; access requires members/{uid}.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val state: StateFlow<SessionState> = retryTrigger
        .flatMapLatest { authRepository.currentUser }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(SessionState.SignedOut)
            } else {
                householdRepository.observeUser(user.uid)
                    .flatMapLatest { profile -> stateFor(profile, ensure = { authRepository.ensureUserDocument(user) }) }
                    .catch { emit(SessionState.Error(it.friendlyMessage())) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    private fun stateFor(profile: UserProfile?, ensure: suspend () -> Unit): Flow<SessionState> = when {
        profile == null -> flow {
            emit(SessionState.Loading)
            ensure() // first sign-in on this account: create the private routing doc
        }
        profile.householdId == null -> flowOf(SessionState.NeedsHousehold(profile))
        else -> householdRepository.observeMembership(profile.householdId, profile.uid)
            .flatMapLatest { isMember ->
                if (!isMember) {
                    flowOf(SessionState.NeedsHousehold(profile))
                } else {
                    combine(
                        householdRepository.observeHousehold(profile.householdId),
                        householdRepository.observeMembers(profile.householdId)
                    ) { household, members ->
                        if (household == null) SessionState.NeedsHousehold(profile)
                        else SessionState.Ready(profile, household, members)
                    }
                }
            }
    }

    fun retry() {
        retryTrigger.value++
    }

    fun signOut() = authRepository.signOut()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                SessionViewModel(c.authRepository, c.householdRepository)
            }
        }
    }
}

/** Shortcut for ViewModel factories: reach the hand-wired AppContainer. */
fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as RoomEaseApp).container
