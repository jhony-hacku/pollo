package com.sge.finca.domain.report

import com.sge.finca.domain.model.BusinessDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPeriodTest {

    @Test
    fun `un mes va del primer al último día, también en febrero bisiesto`() {
        val feb = ReportPeriod(PeriodType.MONTH, 2028, 2)
        assertEquals(BusinessDate.of(2028, 2, 1), feb.fromDay)
        assertEquals(BusinessDate.of(2028, 2, 29), feb.toDay)
        assertEquals("Febrero 2028", feb.label)
    }

    @Test
    fun `navegar entre meses cruza el cambio de año`() {
        val dic = ReportPeriod(PeriodType.MONTH, 2026, 12)
        assertEquals(ReportPeriod(PeriodType.MONTH, 2027, 1), dic.next())
        assertEquals(dic, dic.next().previous())
        assertEquals(ReportPeriod(PeriodType.MONTH, 2025, 12), ReportPeriod(PeriodType.MONTH, 2026, 1).previous())
    }

    @Test
    fun `trimestres y años`() {
        val q4 = ReportPeriod(PeriodType.QUARTER, 2026, 4)
        assertEquals(BusinessDate.of(2026, 10, 1), q4.fromDay)
        assertEquals(BusinessDate.of(2026, 12, 31), q4.toDay)
        assertEquals(ReportPeriod(PeriodType.QUARTER, 2027, 1), q4.next())
        assertEquals("Trimestre 4 de 2026 (oct–dic)", q4.label)

        val year = ReportPeriod(PeriodType.YEAR, 2026)
        assertEquals(BusinessDate.of(2026, 1, 1), year.fromDay)
        assertEquals(BusinessDate.of(2026, 12, 31), year.toDay)
    }

    @Test
    fun `el periodo que contiene un día`() {
        val day = BusinessDate.of(2026, 11, 23)
        assertEquals(ReportPeriod(PeriodType.MONTH, 2026, 11), ReportPeriod.containing(PeriodType.MONTH, day))
        assertEquals(ReportPeriod(PeriodType.QUARTER, 2026, 4), ReportPeriod.containing(PeriodType.QUARTER, day))
        assertTrue(ReportPeriod(PeriodType.YEAR, 2026).contains(day))
        assertFalse(ReportPeriod(PeriodType.MONTH, 2026, 10).contains(day))
    }
}
