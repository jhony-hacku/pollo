package com.sge.finca.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `quita espacios, guiones e indicativo del país`() {
        assertEquals("3001234567", PhoneNumbers.normalize("+57 300 123 4567"))
        assertEquals("3001234567", PhoneNumbers.normalize("300-123-4567"))
        assertEquals("3001234567", PhoneNumbers.normalize("(300) 1234567"))
    }

    @Test
    fun `un número demasiado corto no sirve para comparar`() {
        assertNull(PhoneNumbers.normalize("123"))
        assertNull(PhoneNumbers.normalize(null))
        assertNull(PhoneNumbers.normalize(""))
    }

    @Test
    fun `detecta el mismo número escrito distinto`() {
        assertTrue(PhoneNumbers.sameNumber("+573001234567", "300 123 4567"))
        assertFalse(PhoneNumbers.sameNumber("3001234567", "3001234568"))
        assertFalse(PhoneNumbers.sameNumber(null, null))
    }
}
