package com.sge.finca.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sge.finca.domain.model.DeviceContact
import com.sge.finca.domain.model.FarmContact
import com.sge.finca.domain.model.Permission
import com.sge.finca.domain.model.PhoneNumbers
import com.sge.finca.domain.repository.ContactRepository
import com.sge.finca.domain.repository.SessionRepository
import com.sge.finca.platform.contacts.DeviceContactsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Directorio de contactos de la finca (#20, RF-16): veterinarios, proveedores, compradores.
 * Se comparte entre los miembros (Room + sincronización #32). Los contactos se crean a mano o
 * se importan de la agenda del teléfono, sin duplicar números que ya están.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val contactRepository: ContactRepository,
    private val deviceContactsReader: DeviceContactsReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var farmId: String? = null

    init {
        observeContacts()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeContacts() {
        viewModelScope.launch {
            sessionRepository.session
                .filter { !it.loading }
                .map { session -> session.farmId to session.can(Permission.MANAGE_CONTACTS) }
                .distinctUntilChanged()
                .collectLatest { (currentFarmId, canManage) ->
                    farmId = currentFarmId
                    if (currentFarmId == null) {
                        _uiState.update { it.copy(isLoading = false, contacts = emptyList(), canManage = false) }
                        return@collectLatest
                    }
                    contactRepository.observeContacts(currentFarmId).collect { contacts ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                contacts = contacts.sortedBy { contact -> contact.name.lowercase() },
                                canManage = canManage,
                            )
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    // ---------- Crear y editar a mano ----------

    fun onAddClick() {
        _uiState.update { it.copy(form = ContactForm()) }
    }

    fun onEditClick(contact: FarmContact) {
        if (!_uiState.value.canManage) return
        _uiState.update {
            it.copy(
                form = ContactForm(
                    id = contact.id,
                    name = contact.name,
                    phone = contact.phone.orEmpty(),
                    email = contact.email.orEmpty(),
                    notes = contact.notes.orEmpty(),
                ),
            )
        }
    }

    fun onFormChange(form: ContactForm) {
        _uiState.update { it.copy(form = form.copy(error = null)) }
    }

    fun onFormDismiss() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(form = null) }
    }

    fun onFormSave() {
        val form = _uiState.value.form ?: return
        val currentFarmId = farmId ?: return
        val validation = validate(form)
        if (validation != null) {
            _uiState.update { it.copy(form = form.copy(error = validation)) }
            return
        }
        runSaving(onError = { message -> _uiState.update { it.copy(form = form.copy(error = message)) } }) {
            if (form.id == null) {
                contactRepository.create(currentFarmId, form.name, form.phone, form.email, form.notes)
                _uiState.update { it.copy(form = null, message = "Contacto agregado") }
            } else {
                val current = contactRepository.getContact(currentFarmId, form.id)
                    ?: throw IllegalArgumentException("Este contacto ya no existe")
                contactRepository.update(
                    current.copy(name = form.name, phone = form.phone, email = form.email, notes = form.notes),
                )
                _uiState.update { it.copy(form = null, message = "Contacto actualizado") }
            }
        }
    }

    fun onDelete(contact: FarmContact) {
        val currentFarmId = farmId ?: return
        runSaving(onError = { message -> _uiState.update { it.copy(message = message) } }) {
            contactRepository.delete(currentFarmId, contact.id)
            _uiState.update { it.copy(form = null, message = "Se eliminó ${contact.name}") }
        }
    }

    private fun validate(form: ContactForm): String? = when {
        form.name.isBlank() -> "Escribe un nombre"
        form.phone.isBlank() && form.email.isBlank() -> "Escribe un teléfono o un correo"
        form.phone.isNotBlank() && PhoneNumbers.normalize(form.phone) == null -> "El teléfono no es válido"
        form.email.isNotBlank() && !EMAIL.matches(form.email.trim()) -> "El correo no es válido"
        form.phone.isNotBlank() && _uiState.value.contacts.any {
            it.id != form.id && PhoneNumbers.sameNumber(it.phone, form.phone)
        } -> "Ya hay un contacto con ese teléfono"
        else -> null
    }

    // ---------- Importar del teléfono ----------

    /** Se llama cuando el permiso READ_CONTACTS ya está concedido. */
    fun onImportRequested() {
        if (!_uiState.value.canManage) return
        _uiState.update { it.copy(importing = ImportState(isLoading = true)) }
        viewModelScope.launch {
            try {
                val deviceContacts = deviceContactsReader.readAll()
                val existing = _uiState.value.contacts
                _uiState.update { state ->
                    state.copy(
                        importing = ImportState(
                            isLoading = false,
                            candidates = deviceContacts.map { ImportCandidate(it, isDuplicate(it, existing)) },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _uiState.update { it.copy(importing = null, message = "Sin permiso para leer los contactos del teléfono") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(importing = ImportState(isLoading = false, error = "No se pudieron leer los contactos del teléfono"))
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(message = "Sin el permiso de contactos no se puede importar, pero puedes agregarlos a mano")
        }
    }

    fun onImportQueryChange(query: String) {
        _uiState.update { state -> state.copy(importing = state.importing?.copy(query = query)) }
    }

    fun onToggleCandidate(id: String) {
        _uiState.update { state ->
            val importing = state.importing ?: return@update state
            val candidate = importing.candidates.firstOrNull { it.contact.id == id }
            if (candidate == null || candidate.duplicate) return@update state
            val selected = if (id in importing.selected) importing.selected - id else importing.selected + id
            state.copy(importing = importing.copy(selected = selected))
        }
    }

    fun onImportDismiss() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(importing = null) }
    }

    fun onImportSelected() {
        val importing = _uiState.value.importing ?: return
        val currentFarmId = farmId ?: return
        val chosen = importing.candidates.filter { it.contact.id in importing.selected && !it.duplicate }.map { it.contact }
        if (chosen.isEmpty()) return

        runSaving(onError = { message -> _uiState.update { it.copy(message = message) } }) {
            var imported = 0
            var skipped = 0
            val added = mutableListOf<FarmContact>()
            for (contact in chosen) {
                // Dos contactos del teléfono con el mismo número: solo entra el primero.
                if (isDuplicate(contact, _uiState.value.contacts + added)) {
                    skipped++
                    continue
                }
                val phone = contact.phones.firstOrNull()
                val id = contactRepository.create(
                    farmId = currentFarmId,
                    name = contact.name,
                    phone = phone,
                    email = contact.email,
                    notes = contact.phones.drop(1).takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = "Otros teléfonos: "),
                )
                added += FarmContact(
                    id = id, farmId = currentFarmId, name = contact.name, phone = phone, email = contact.email,
                    createdBy = "", createdAt = 0, updatedAt = 0,
                )
                imported++
            }
            val message = buildString {
                append(if (imported == 1) "Se importó 1 contacto" else "Se importaron $imported contactos")
                if (skipped > 0) append(" ($skipped ya estaban)")
            }
            _uiState.update { it.copy(importing = null, message = message) }
        }
    }

    private fun isDuplicate(contact: DeviceContact, existing: List<FarmContact>): Boolean =
        existing.any { farmContact ->
            contact.phones.any { PhoneNumbers.sameNumber(it, farmContact.phone) } ||
                (contact.email != null && contact.email.equals(farmContact.email, ignoreCase = true))
        }

    // ---------- Común ----------

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Llamar no requiere permisos: ACTION_DIAL abre el marcador y el usuario confirma. */
    fun onNoDialerApp() {
        _uiState.update { it.copy(message = "Este dispositivo no tiene una app de teléfono") }
    }

    private fun runSaving(onError: (String) -> Unit, block: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                onError("No tienes permiso para gestionar contactos")
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo guardar")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
