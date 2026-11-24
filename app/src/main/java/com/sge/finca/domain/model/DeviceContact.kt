package com.sge.finca.domain.model

/** Contacto de la agenda del teléfono, listo para importarlo al directorio de la finca (#20). */
data class DeviceContact(
    val id: String,
    val name: String,
    val phones: List<String>,
    val email: String?,
)
