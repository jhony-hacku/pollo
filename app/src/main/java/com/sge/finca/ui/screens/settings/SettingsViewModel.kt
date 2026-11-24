package com.sge.finca.ui.screens.settings

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sge.finca.data.repository.AuthRepository
import com.sge.finca.domain.repository.UserRepository
import com.sge.finca.platform.biometric.BiometricAvailability
import com.sge.finca.platform.biometric.BiometricSetup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val biometricSetup: BiometricSetup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshBiometricState()
        observeProfile()
    }

    private fun observeProfile() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { profile ->
                _uiState.update {
                    it.copy(
                        userName = profile?.name.orEmpty(),
                        userEmail = profile?.email.orEmpty(),
                        photoUrl = profile?.photoUrl.orEmpty(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun saveProfile(
        name: String,
        selectedPhotoUri: String?,
        onSaved: () -> Unit,
    ) {
        if (_uiState.value.isSavingProfile) return

        val cleanName = name.trim()
        if (cleanName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            showMessage(
                "El nombre debe tener entre $MIN_NAME_LENGTH y $MAX_NAME_LENGTH caracteres.",
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true) }
            try {
                userRepository.updateProfile(cleanName, selectedPhotoUri)
                showMessage("Perfil actualizado.")
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "No se pudo actualizar el perfil.")
            } finally {
                _uiState.update { it.copy(isSavingProfile = false) }
            }
        }
    }

    fun onSignOut() {
        authRepository.signOut()
    }

    /** El usuario encendió el switch: se valida el sensor y se confirma con una huella. */
    fun onBiometricEnableRequested(activity: FragmentActivity) {
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            showMessage("No hay una sesión activa.")
            return
        }
        when (biometricSetup.availability()) {
            BiometricAvailability.AVAILABLE ->
                biometricSetup.enableWithConfirmation(activity, uid) { enabled ->
                    refreshBiometricState()
                    if (!enabled) showMessage("No se activó el inicio rápido con huella.")
                }

            BiometricAvailability.NONE_ENROLLED ->
                showMessage(
                    "No hay huellas registradas en este dispositivo. " +
                            "Regístrala en los ajustes del teléfono y vuelve a intentarlo.",
                )

            BiometricAvailability.NO_HARDWARE ->
                showMessage("Este dispositivo no tiene sensor de huella.")

            BiometricAvailability.UNAVAILABLE ->
                showMessage("El sensor de huella no está disponible en este momento.")
        }
        refreshBiometricState()
    }

    /** El usuario apagó el switch. */
    fun onBiometricDisabled() {
        biometricSetup.disable()
        refreshBiometricState()
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshBiometricState() {
        val uid = authRepository.currentUser?.uid
        _uiState.update {
            it.copy(
                biometricAvailability = biometricSetup.availability(),
                biometricEnabled = uid != null && biometricSetup.isEnabledFor(uid),
            )
        }
    }

    private fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    private companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 60
    }
}