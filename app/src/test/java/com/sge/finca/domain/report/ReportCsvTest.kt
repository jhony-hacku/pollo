package com.sge.finca.domain.report

import com.sge.finca.domain.model.BusinessDate
import com.sge.finca.domain.model.Transaction
import com.sge.finca.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCsvTest {

    private val report = FinancialReport.build(
        ReportPeriod(PeriodType.MONTH, 2026, 11),
        listOf(
            Transaction(
                id = "t1", farmId = "f1", type = TransactionType.EXPENSE, amountCents = 12_500_000,
                categoryId = "c1", categoryName = "Sanidad", dateEpochDay = BusinessDate.of(2026, 11, 5),
                note = "Vacuna; aftosa \"lote 2\"", createdBy = "u1", createdByName = "Juan", createdAt = 1, updatedAt = 1,
            ),
        ),
    )

    @Test
    fun `abre en Excel en español con tildes y montos sumables`() {
        val csv = ReportCsv.write(report, "La Esperanza", "2026-11-23 21:00")
        assertTrue(csv.startsWith(ReportCsv.BOM))
        assertTrue(csv.contains("Balance;-125000\r\n"))
        assertTrue(csv.contains("2026-11-05;Egreso;Sanidad;-125000;;\"Vacuna; aftosa \"\"lote 2\"\"\";Juan\r\n"))
        assertTrue(csv.contains("Sanidad;125000;1;100,0\r\n"))
    }

    @Test
    fun `escapa separadores, comillas y saltos de línea`() {
        assertEquals("simple", ReportCsv.escape("simple"))
        assertEquals("\"a;b\"", ReportCsv.escape("a;b"))
        assertEquals("\"dice \"\"hola\"\"\"", ReportCsv.escape("dice \"hola\""))
        assertEquals("\"dos\nlíneas\"", ReportCsv.escape("dos\nlíneas"))
    }
}
