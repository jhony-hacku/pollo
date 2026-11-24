package com.sge.finca.domain.report

import com.sge.finca.domain.model.BusinessDate
import com.sge.finca.domain.model.TransactionType

/**
 * CSV del reporte para abrir en Excel o Google Sheets (#reportes).
 *
 * - Separador ";" y BOM UTF-8: Excel en español lo abre en columnas y con tildes sin importar
 *   nada a mano.
 * - Montos en pesos enteros, sin puntos de miles, para que la hoja pueda sumarlos.
 * - Fechas ISO (aaaa-mm-dd), que Excel reconoce como fecha.
 */
object ReportCsv {
    private const val SEP = ";"
    const val BOM = "﻿"

    fun write(report: FinancialReport, farmName: String, generatedAt: String): String = buildString {
        append(BOM)
        line("Reporte financiero", farmName)
        line("Periodo", report.period.label)
        line("Desde", BusinessDate.toIsoString(report.period.fromDay), "Hasta", BusinessDate.toIsoString(report.period.toDay))
        line("Generado", generatedAt)
        line()
        line("Resumen", "Pesos (COP)")
        line("Ingresos", pesos(report.incomeCents))
        line("Egresos", pesos(report.expenseCents))
        line("Balance", pesos(report.balanceCents))
        line("Movimientos", report.transactions.size.toString())
        line()
        categoryTable("Egresos por categoría", report.expenseByCategory)
        categoryTable("Ingresos por categoría", report.incomeByCategory)
        line("Detalle de movimientos")
        line("Fecha", "Tipo", "Categoría", "Monto (COP)", "Método de pago", "Nota", "Registrado por")
        for (t in report.transactions) {
            line(
                BusinessDate.toIsoString(t.dateEpochDay),
                if (t.type == TransactionType.INCOME) "Ingreso" else "Egreso",
                t.categoryName,
                pesos(if (t.type == TransactionType.INCOME) t.amountCents else -t.amountCents),
                t.paymentMethod.orEmpty(),
                t.note.orEmpty(),
                t.createdByName.orEmpty(),
            )
        }
    }

    private fun StringBuilder.categoryTable(title: String, rows: List<CategoryTotal>) {
        if (rows.isEmpty()) return
        line(title, "Pesos (COP)", "Movimientos", "% del total")
        for (row in rows) {
            line(row.name, pesos(row.totalCents), row.count.toString(), percent(row.share))
        }
        line()
    }

    private fun StringBuilder.line(vararg cells: String) {
        append(cells.joinToString(SEP) { escape(it) })
        append("\r\n")
    }

    /** Comillas si el texto trae separador, comillas o saltos de línea (RFC 4180). */
    fun escape(value: String): String {
        val needsQuotes = value.contains(SEP) || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun pesos(cents: Long): String = (cents / 100).toString()

    private fun percent(share: Double): String = String.format(java.util.Locale.ROOT, "%.1f", share * 100).replace('.', ',')
}
