package com.sge.finca.domain.model

/**
 * Números de teléfono para comparar contactos (#20): el mismo número puede venir como
 * "+57 300 123 4567", "300-123-4567" o "3001234567". Se comparan los últimos 10 dígitos, que es
 * el largo de un celular o fijo en Colombia sin el indicativo del país.
 */
object PhoneNumbers {
    private const val SIGNIFICANT_DIGITS = 10

    /** Solo dígitos y sin indicativo; null si no queda nada útil (menos de 7 dígitos). */
    fun normalize(raw: String?): String? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.length < 7) return null
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }

    fun sameNumber(a: String?, b: String?): Boolean {
        val left = normalize(a) ?: return false
        return left == normalize(b)
    }
}
