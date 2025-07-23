package com.torto.trgym.viewmodel.profile


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            val email = authRepository.getCurrentUser()?.email
                ?: "Ospite"
            _uiState.value = ProfileUiState.Data(email)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.value = ProfileUiState.LoggedOut
        }
    }

    sealed interface ProfileUiState {
        object Loading : ProfileUiState
        data class Data(val email: String) : ProfileUiState
        object LoggedOut : ProfileUiState
    }
}
