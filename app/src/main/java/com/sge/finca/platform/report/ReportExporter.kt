package com.sge.finca.platform.report

import android.content.Context
import android.content.Intent
import com.sge.finca.domain.report.FinancialReport
import com.sge.finca.domain.report.PeriodType
import com.sge.finca.domain.report.ReportCsv
import com.sge.finca.platform.storage.FileProviderHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Formatos en los que el propietario puede enviar el reporte al administrador. */
enum class ExportFormat(val extension: String, val mimeType: String) {
    /** Documento listo para imprimir o enviar. */
    PDF("pdf", "application/pdf"),

    /** Para Excel o Google Sheets: los montos se pueden sumar y filtrar. */
    CSV("csv", "text/csv"),
}

/**
 * Genera el archivo del reporte en la caché de la app y devuelve el Intent para compartirlo
 * (correo, WhatsApp, Drive...). El archivo se expone con el FileProvider de la app, solo de
 * lectura y solo a la app que el usuario elija.
 */
@Singleton
class ReportExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileProviderHelper: FileProviderHelper,
) {
    suspend fun export(
        report: FinancialReport,
        farmName: String,
        generatedBy: String,
        format: ExportFormat,
    ): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, REPORTS_DIR).apply { mkdirs() }
        // Solo se guarda el último: son copias para compartir, no un archivo de la app.
        directory.listFiles()?.forEach { it.delete() }

        val file = File(directory, "${fileName(report, farmName)}.${format.extension}")
        val generatedAt = SimpleDateFormat("d MMM yyyy, h:mm a", SPANISH).format(Date()).replace(".", "")
        when (format) {
            ExportFormat.PDF -> file.outputStream().use { out ->
                ReportPdfWriter(report, farmName, generatedBy, generatedAt).write(out)
            }
            ExportFormat.CSV -> file.writeText(ReportCsv.write(report, farmName, generatedAt), Charsets.UTF_8)
        }

        val send = fileProviderHelper.createShareIntent(file, format.mimeType).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Reporte financiero · $farmName · ${report.period.label}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Reporte financiero de $farmName (${report.period.label}), generado con SGE.",
            )
        }
        Intent.createChooser(send, "Enviar reporte").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** "reporte-la-esperanza-2026-11", sin tildes ni espacios para que cualquier app lo acepte. */
    private fun fileName(report: FinancialReport, farmName: String): String {
        val farm = Normalizer.normalize(farmName, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "finca" }
        val period = report.period
        val suffix = when (period.type) {
            PeriodType.MONTH -> "%04d-%02d".format(Locale.ROOT, period.year, period.index)
            PeriodType.QUARTER -> "${period.year}-T${period.index}"
            PeriodType.YEAR -> "${period.year}"
        }
        return "reporte-$farm-$suffix"
    }

    private companion object {
        const val REPORTS_DIR = "reports"
        val SPANISH: Locale = Locale.forLanguageTag("es-CO")
    }
}
