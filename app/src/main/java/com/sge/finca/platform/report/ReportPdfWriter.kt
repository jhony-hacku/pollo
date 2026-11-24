package com.sge.finca.platform.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sge.finca.domain.model.TransactionType
import com.sge.finca.domain.report.CategoryTotal
import com.sge.finca.domain.report.FinancialReport
import com.sge.finca.domain.report.PeriodType
import com.sge.finca.ui.format.BusinessDateFormat
import com.sge.finca.ui.format.Cop
import java.io.OutputStream

/**
 * PDF del reporte financiero para el administrador: encabezado de la finca, resumen, gráfico,
 * totales por categoría y el detalle de todos los movimientos (con saltos de página).
 * Usa PdfDocument del SDK: sin librerías externas. Tamaño carta en puntos (612 × 792).
 */
class ReportPdfWriter(
    private val report: FinancialReport,
    private val farmName: String,
    private val generatedBy: String,
    private val generatedAt: String,
) {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var pageNumber = 0
    private var y = 0f

    fun write(out: OutputStream) {
        try {
            newPage(withHeader = true)
            summary()
            chart()
            categoryTable("Egresos por categoría", report.expenseByCategory, EXPENSE)
            categoryTable("Ingresos por categoría", report.incomeByCategory, INCOME)
            transactionsTable()
            finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    // ---------- Páginas ----------

    private fun newPage(withHeader: Boolean = false) {
        finishPage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        page = document.startPage(info).also { canvas = it.canvas }
        y = MARGIN
        if (withHeader) header() else miniHeader()
    }

    private fun finishPage() {
        page?.let {
            footer()
            document.finishPage(it)
        }
        page = null
    }

    /** Pide una página nueva si lo que sigue ([height]) no cabe. */
    private fun ensureSpace(height: Float): Boolean {
        if (y + height <= PAGE_HEIGHT - MARGIN - FOOTER_SPACE) return false
        newPage()
        return true
    }

    private fun header() {
        val band = RectF(0f, 0f, PAGE_WIDTH.toFloat(), 92f)
        canvas.drawRect(band, fill(BRAND))
        canvas.drawText("Reporte financiero", MARGIN, 38f, text(20f, Color.WHITE, bold = true))
        canvas.drawText(farmName, MARGIN, 60f, text(13f, Color.WHITE))
        canvas.drawText(report.period.label, MARGIN, 80f, text(11f, BRAND_LIGHT))
        val sge = text(11f, BRAND_LIGHT, bold = true).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("SGE · Gestión de finca", PAGE_WIDTH - MARGIN, 38f, sge)
        y = 92f + 22f

        val meta = text(9f, MUTED)
        val range = "Del ${BusinessDateFormat.format(report.period.fromDay)} al ${BusinessDateFormat.format(report.period.toDay)}"
        canvas.drawText("$range · ${report.transactions.size} movimientos", MARGIN, y, meta)
        canvas.drawText(
            "Generado el $generatedAt por $generatedBy",
            PAGE_WIDTH - MARGIN,
            y,
            text(9f, MUTED).apply { textAlign = Paint.Align.RIGHT },
        )
        y += 18f
    }

    private fun miniHeader() {
        canvas.drawText("$farmName · ${report.period.label}", MARGIN, y + 10f, text(9f, MUTED, bold = true))
        canvas.drawLine(MARGIN, y + 16f, PAGE_WIDTH - MARGIN, y + 16f, stroke(DIVIDER))
        y += 30f
    }

    private fun footer() {
        val baseline = PAGE_HEIGHT - MARGIN / 2
        canvas.drawLine(MARGIN, baseline - 12f, PAGE_WIDTH - MARGIN, baseline - 12f, stroke(DIVIDER))
        canvas.drawText("SGE · Sistema de Gestión de Finca", MARGIN, baseline, text(8f, MUTED))
        canvas.drawText(
            "Página $pageNumber",
            PAGE_WIDTH - MARGIN,
            baseline,
            text(8f, MUTED).apply { textAlign = Paint.Align.RIGHT },
        )
    }

    // ---------- Secciones ----------

    private fun summary() {
        val gap = 12f
        val width = (CONTENT_WIDTH - gap * 2) / 3
        val height = 64f
        listOf(
            Triple("Ingresos", report.incomeCents, INCOME),
            Triple("Egresos", report.expenseCents, EXPENSE),
            Triple("Balance", report.balanceCents, if (report.balanceCents >= 0) BRAND else EXPENSE),
        ).forEachIndexed { i, (label, cents, color) ->
            val left = MARGIN + i * (width + gap)
            val box = RectF(left, y, left + width, y + height)
            canvas.drawRoundRect(box, 8f, 8f, fill(SURFACE))
            canvas.drawRect(RectF(left, y, left + 4f, y + height), fill(color))
            canvas.drawText(label.uppercase(), left + 14f, y + 22f, text(8.5f, MUTED, bold = true))
            canvas.drawText(Cop.format(cents), left + 14f, y + 48f, text(16f, color, bold = true))
        }
        y += height + 26f
    }

    private fun chart() {
        val title = if (report.period.type == PeriodType.MONTH) "Ingresos y egresos por semana" else "Ingresos y egresos por mes"
        val chartHeight = 140f
        ensureSpace(chartHeight + 70f)
        sectionTitle(title)
        val top = y
        val bottom = y + chartHeight
        val max = report.buckets.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) }?.takeIf { it > 0 } ?: 1L

        // Líneas guía: 0, 50 % y 100 % del máximo.
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val lineY = bottom - chartHeight * fraction
            canvas.drawLine(MARGIN + 54f, lineY, PAGE_WIDTH - MARGIN, lineY, stroke(DIVIDER))
            canvas.drawText(
                Cop.format((max * fraction).toLong()),
                MARGIN + 48f,
                lineY + 3f,
                text(7f, MUTED).apply { textAlign = Paint.Align.RIGHT },
            )
        }

        val area = CONTENT_WIDTH - 54f
        val slot = area / report.buckets.size.coerceAtLeast(1)
        val barWidth = (slot * 0.32f).coerceAtMost(22f)
        report.buckets.forEachIndexed { i, bucket ->
            val center = MARGIN + 54f + slot * i + slot / 2
            val incomeHeight = chartHeight * bucket.incomeCents / max
            val expenseHeight = chartHeight * bucket.expenseCents / max
            canvas.drawRect(RectF(center - barWidth - 1f, bottom - incomeHeight, center - 1f, bottom), fill(INCOME))
            canvas.drawRect(RectF(center + 1f, bottom - expenseHeight, center + barWidth + 1f, bottom), fill(EXPENSE))
            canvas.drawText(bucket.label, center, bottom + 13f, text(8f, MUTED).apply { textAlign = Paint.Align.CENTER })
        }
        y = bottom + 30f
        legend(top - 4f)
        y += 6f
    }

    private fun legend(atY: Float) {
        val right = PAGE_WIDTH - MARGIN
        val paint = text(8f, MUTED)
        listOf("Egresos" to EXPENSE, "Ingresos" to INCOME).fold(right) { x, (label, color) ->
            val labelWidth = paint.measureText(label)
            canvas.drawText(label, x - labelWidth, atY, paint)
            canvas.drawRect(RectF(x - labelWidth - 12f, atY - 7f, x - labelWidth - 4f, atY + 1f), fill(color))
            x - labelWidth - 24f
        }
    }

    private fun categoryTable(title: String, rows: List<CategoryTotal>, color: Int) {
        if (rows.isEmpty()) return
        ensureSpace(60f)
        sectionTitle(title)
        val columns = floatArrayOf(MARGIN, MARGIN + 230f, MARGIN + 300f, PAGE_WIDTH - MARGIN)
        tableHeader(listOf("Categoría", "Mov.", "% del total", "Monto"), columns)
        rows.forEach { row ->
            if (ensureSpace(ROW_HEIGHT)) tableHeader(listOf("Categoría", "Mov.", "% del total", "Monto"), columns)
            val baseline = y + 13f
            canvas.drawText(ellipsize(row.name, 220f, text(9f, INK)), columns[0], baseline, text(9f, INK))
            canvas.drawText(row.count.toString(), columns[1], baseline, text(9f, INK))
            // Barra proporcional al porcentaje.
            val barMax = 90f
            canvas.drawRoundRect(RectF(columns[2], y + 5f, columns[2] + barMax, y + 12f), 3f, 3f, fill(SURFACE))
            canvas.drawRoundRect(
                RectF(columns[2], y + 5f, columns[2] + barMax * row.share.toFloat(), y + 12f), 3f, 3f, fill(color),
            )
            canvas.drawText(percent(row.share), columns[2] + barMax + 6f, baseline, text(8f, MUTED))
            canvas.drawText(Cop.format(row.totalCents), columns[3], baseline, text(9f, INK, bold = true).right())
            y += ROW_HEIGHT
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, stroke(DIVIDER))
        }
        y += 18f
    }

    private fun transactionsTable() {
        ensureSpace(60f)
        sectionTitle("Detalle de movimientos")
        if (report.transactions.isEmpty()) {
            canvas.drawText("No hay movimientos en este periodo.", MARGIN, y + 12f, text(9f, MUTED))
            y += 24f
            return
        }
        val headers = listOf("Fecha", "Categoría", "Nota", "Registró", "Monto")
        val columns = floatArrayOf(MARGIN, MARGIN + 66f, MARGIN + 196f, MARGIN + 366f, PAGE_WIDTH - MARGIN)
        tableHeader(headers, columns)
        report.transactions.forEachIndexed { i, t ->
            if (ensureSpace(ROW_HEIGHT)) tableHeader(headers, columns)
            if (i % 2 == 1) canvas.drawRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT), fill(STRIPE))
            val baseline = y + 13f
            val paint = text(8.5f, INK)
            val income = t.type == TransactionType.INCOME
            canvas.drawText(BusinessDateFormat.format(t.dateEpochDay), columns[0], baseline, paint)
            canvas.drawText(ellipsize(t.categoryName, 124f, paint), columns[1], baseline, paint)
            val note = listOfNotNull(t.note, t.paymentMethod?.let { "($it)" }).joinToString(" ")
            canvas.drawText(ellipsize(note, 164f, paint), columns[2], baseline, text(8.5f, MUTED))
            canvas.drawText(ellipsize(t.createdByName.orEmpty(), 80f, paint), columns[3], baseline, text(8.5f, MUTED))
            canvas.drawText(
                (if (income) "+" else "-") + Cop.format(t.amountCents),
                columns[4],
                baseline,
                text(8.5f, if (income) INCOME else EXPENSE, bold = true).right(),
            )
            y += ROW_HEIGHT
        }
        ensureSpace(ROW_HEIGHT + 4f)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, stroke(INK))
        canvas.drawText("Balance del periodo", columns[0], y + 15f, text(9f, INK, bold = true))
        canvas.drawText(
            Cop.format(report.balanceCents),
            columns[4],
            y + 15f,
            text(9f, if (report.balanceCents >= 0) BRAND else EXPENSE, bold = true).right(),
        )
        y += ROW_HEIGHT + 8f
    }

    // ---------- Ayudas de dibujo ----------

    private fun sectionTitle(title: String) {
        ensureSpace(30f)
        canvas.drawText(title, MARGIN, y + 12f, text(12f, INK, bold = true))
        y += 22f
    }

    private fun tableHeader(titles: List<String>, columns: FloatArray) {
        canvas.drawRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT), fill(SURFACE))
        titles.forEachIndexed { i, title ->
            val paint = text(8f, MUTED, bold = true)
            if (i == titles.lastIndex) paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(title.uppercase(), columns[i] + if (i == titles.lastIndex) 0f else 4f, y + 13f, paint)
        }
        y += ROW_HEIGHT
    }

    private fun ellipsize(value: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maxWidth) return value
        var end = value.length
        while (end > 0 && paint.measureText(value, 0, end) + paint.measureText("…") > maxWidth) end--
        return value.take(end) + "…"
    }

    private fun percent(share: Double) = String.format(java.util.Locale.ROOT, "%.1f %%", share * 100).replace('.', ',')

    private fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun Paint.right() = apply { textAlign = Paint.Align.RIGHT }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
    }

    private companion object {
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
        const val FOOTER_SPACE = 16f
        const val ROW_HEIGHT = 19f

        val BRAND = Color.rgb(46, 125, 50)
        val BRAND_LIGHT = Color.rgb(200, 230, 201)
        val INCOME = Color.rgb(46, 125, 50)
        val EXPENSE = Color.rgb(198, 40, 40)
        val INK = Color.rgb(33, 33, 33)
        val MUTED = Color.rgb(117, 117, 117)
        val SURFACE = Color.rgb(241, 245, 241)
        val STRIPE = Color.rgb(248, 250, 248)
        val DIVIDER = Color.rgb(224, 224, 224)
    }
}
