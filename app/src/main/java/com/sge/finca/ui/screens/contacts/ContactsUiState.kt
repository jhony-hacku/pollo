package com.sge.finca.ui.screens.contacts

import com.sge.finca.domain.model.DeviceContact
import com.sge.finca.domain.model.FarmContact

data class ContactsUiState(
    val isLoading: Boolean = true,
    val contacts: List<FarmContact> = emptyList(),
    /** Crear, editar, importar y borrar (Permission.MANAGE_CONTACTS). */
    val canManage: Boolean = false,
    val query: String = "",
    /** Formulario abierto (nuevo o edición); null si no hay. */
    val form: ContactForm? = null,
    /** Selección de contactos del teléfono abierta; null si no hay. */
    val importing: ImportState? = null,
    val isSaving: Boolean = false,
    /** Aviso pendiente para el snackbar. */
    val message: String? = null,
) {
    val visibleContacts: List<FarmContact>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return contacts
            return contacts.filter { contact ->
                contact.name.lowercase().contains(q) ||
                    contact.phone.orEmpty().contains(q) ||
                    contact.notes.orEmpty().lowercase().contains(q)
            }
        }
}

data class ContactForm(
    /** null = contacto nuevo. */
    val id: String? = null,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
    val error: String? = null,
)

data class ImportCandidate(
    val contact: DeviceContact,
    /** Ya hay en el directorio un contacto con el mismo teléfono (o correo). */
    val duplicate: Boolean,
)

data class ImportState(
    val isLoading: Boolean = true,
    val candidates: List<ImportCandidate> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val error: String? = null,
) {
    val visibleCandidates: List<ImportCandidate>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return candidates
            return candidates.filter { candidate ->
                candidate.contact.name.lowercase().contains(q) ||
                    candidate.contact.phones.any { it.contains(q) }
            }
        }
}
