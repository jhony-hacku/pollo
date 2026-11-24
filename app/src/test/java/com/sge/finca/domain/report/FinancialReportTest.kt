package com.sge.finca.domain.report

import com.sge.finca.domain.model.BusinessDate
import com.sge.finca.domain.model.Transaction
import com.sge.finca.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialReportTest {

    private fun tx(day: Int, type: TransactionType, pesos: Long, category: String, month: Int = 11) = Transaction(
        id = "$month-$day-$category-$pesos",
        farmId = "f1",
        type = type,
        amountCents = pesos * 100,
        categoryId = category,
        categoryName = category,
        dateEpochDay = BusinessDate.of(2026, month, day),
        createdBy = "u1",
        createdAt = day.toLong(),
        updatedAt = day.toLong(),
    )

    private val november = ReportPeriod(PeriodType.MONTH, 2026, 11)

    @Test
    fun `totales y balance solo del periodo`() {
        val report = FinancialReport.build(
            november,
            listOf(
                tx(3, TransactionType.INCOME, 500_000, "Venta de leche"),
                tx(10, TransactionType.EXPENSE, 120_000, "Sanidad"),
                tx(28, TransactionType.EXPENSE, 80_000, "Alimento"),
                tx(30, TransactionType.INCOME, 999_999, "Venta de leche", month = 10), // octubre: fuera
            ),
        )
        assertEquals(500_000_00L, report.incomeCents)
        assertEquals(200_000_00L, report.expenseCents)
        assertEquals(300_000_00L, report.balanceCents)
        assertEquals(3, report.transactions.size)
        assertEquals(BusinessDate.of(2026, 11, 28), report.transactions.first().dateEpochDay)
    }

    @Test
    fun `categorías ordenadas por monto con su porcentaje`() {
        val report = FinancialReport.build(
            november,
            listOf(
                tx(1, TransactionType.EXPENSE, 30_000, "Sanidad"),
                tx(2, TransactionType.EXPENSE, 60_000, "Alimento"),
                tx(3, TransactionType.EXPENSE, 10_000, "Sanidad"),
            ),
        )
        val top = report.expenseByCategory.first()
        assertEquals("Alimento", top.name)
        assertEquals(0.6, top.share, 0.0001)
        assertEquals(2, report.expenseByCategory[1].count)
        assertTrue(report.incomeByCategory.isEmpty())
    }

    @Test
    fun `el mes se grafica por semanas y el año por meses`() {
        val monthReport = FinancialReport.build(november, listOf(tx(8, TransactionType.INCOME, 100, "Leche")))
        assertEquals(listOf("1–7", "8–14", "15–21", "22–28", "29–30"), monthReport.buckets.map { it.label })
        assertEquals(100_00L, monthReport.buckets[1].incomeCents)

        val yearReport = FinancialReport.build(ReportPeriod(PeriodType.YEAR, 2026), listOf(tx(8, TransactionType.EXPENSE, 50, "Sal")))
        assertEquals(12, yearReport.buckets.size)
        assertEquals("Nov", yearReport.buckets[10].label)
        assertEquals(50_00L, yearReport.buckets[10].expenseCents)
    }

    @Test
    fun `sin movimientos el reporte queda vacío`() {
        val report = FinancialReport.build(november, emptyList())
        assertTrue(report.isEmpty)
        assertEquals(0L, report.balanceCents)
    }
}
