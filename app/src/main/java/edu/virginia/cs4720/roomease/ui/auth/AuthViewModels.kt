package edu.virginia.cs4720.roomease.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import edu.virginia.cs4720.roomease.data.AuthRepository
import edu.virginia.cs4720.roomease.data.authMessage
import edu.virginia.cs4720.roomease.domain.Validation
import edu.virginia.cs4720.roomease.ui.session.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One UiState per screen, updated with copy() (Lecture 6).
 * Email and name go in SavedStateHandle so they survive process death; the password
 * stays in memory only (it survives rotation via the ViewModel but is never written to disk).
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState(email = savedState["email"] ?: ""))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        savedState["email"] = value
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun signIn() {
        val state = _uiState.value
        if (state.isLoading) return
        // Validate before calling the network (Lecture 11).
        val emailError = if (!Validation.isValidEmail(state.email)) "Enter a valid email address" else null
        val passwordError = if (state.password.isEmpty()) "Enter your password" else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signIn(state.email, state.password)
                // Session state flips to signed-in; RoomEaseRoot navigates for us.
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.authMessage()) }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.authMessage()) }
            }
        }
    }

    fun showError(message: String?) = _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    fun setLoading(loading: Boolean) = _uiState.update { it.copy(isLoading = loading, errorMessage = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { LoginViewModel(container().authRepository, createSavedStateHandle()) }
        }
    }
}

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val nameError: String? get() = if (name.isNotEmpty() && !Validation.isValidName(name)) "Use 1–50 characters" else null
    val emailError: String? get() = if (email.isNotEmpty() && !Validation.isValidEmail(email)) "Enter a valid email address" else null
    val passwordError: String? get() = if (password.isNotEmpty() && !Validation.isValidPassword(password)) "At least 6 characters" else null
    val confirmError: String? get() = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords don't match" else null

    /** Lecture 8 error prevention: disable "Create account" until the form is valid. */
    val canSubmit: Boolean
        get() = Validation.isValidName(name) && Validation.isValidEmail(email) &&
            Validation.isValidPassword(password) && confirmPassword == password && !isLoading
}

class SignUpViewModel(
    private val authRepository: AuthRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SignUpUiState(name = savedState["name"] ?: "", email = savedState["email"] ?: "")
    )
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        savedState["name"] = value
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun onEmailChange(value: String) {
        savedState["email"] = value
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun createAccount() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                authRepository.createAccount(state.name, state.email, state.password)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.authMessage()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SignUpViewModel(container().authRepository, createSavedStateHandle()) }
        }
    }
}
