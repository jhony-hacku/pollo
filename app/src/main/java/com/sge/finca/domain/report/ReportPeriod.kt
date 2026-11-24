package com.sge.finca.domain.report

import com.sge.finca.domain.model.BusinessDate
import java.util.Calendar
import java.util.TimeZone

/** Tipo de periodo del reporte financiero. */
enum class PeriodType { MONTH, QUARTER, YEAR }

/**
 * Periodo calendario del reporte: un mes, un trimestre o un año. Trabaja con epoch days
 * (ver [BusinessDate]) porque minSdk 24 no tiene java.time.
 *
 * @param index mes 1..12 para MONTH, trimestre 1..4 para QUARTER; se ignora en YEAR.
 */
data class ReportPeriod(val type: PeriodType, val year: Int, val index: Int = 1) {

    init {
        when (type) {
            PeriodType.MONTH -> require(index in 1..12) { "Mes inválido: $index" }
            PeriodType.QUARTER -> require(index in 1..4) { "Trimestre inválido: $index" }
            PeriodType.YEAR -> Unit
        }
    }

    /** Primer mes del periodo (1..12). */
    val firstMonth: Int
        get() = when (type) {
            PeriodType.MONTH -> index
            PeriodType.QUARTER -> (index - 1) * 3 + 1
            PeriodType.YEAR -> 1
        }

    /** Cantidad de meses que abarca. */
    val monthCount: Int
        get() = when (type) {
            PeriodType.MONTH -> 1
            PeriodType.QUARTER -> 3
            PeriodType.YEAR -> 12
        }

    val fromDay: Long get() = BusinessDate.of(year, firstMonth, 1)

    /** Último día, inclusivo. */
    val toDay: Long get() = next().fromDay - 1

    val label: String
        get() = when (type) {
            PeriodType.MONTH -> "${MONTH_NAMES[index - 1].replaceFirstChar { it.uppercase() }} $year"
            PeriodType.QUARTER -> "Trimestre $index de $year (${MONTH_SHORT[firstMonth - 1]}–${MONTH_SHORT[firstMonth + 1]})"
            PeriodType.YEAR -> "Año $year"
        }

    fun previous(): ReportPeriod = shift(-1)

    fun next(): ReportPeriod = shift(1)

    fun contains(epochDay: Long): Boolean = epochDay in fromDay..toDay

    private fun shift(step: Int): ReportPeriod = when (type) {
        PeriodType.YEAR -> copy(year = year + step)
        PeriodType.MONTH -> rollover(12, step)
        PeriodType.QUARTER -> rollover(4, step)
    }

    private fun rollover(perYear: Int, step: Int): ReportPeriod {
        val zeroBased = (year * perYear + (index - 1)) + step
        return copy(year = Math.floorDiv(zeroBased, perYear), index = Math.floorMod(zeroBased, perYear) + 1)
    }

    companion object {
        val MONTH_NAMES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        val MONTH_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

        /** El periodo de [type] que contiene el día [epochDay]. */
        fun containing(type: PeriodType, epochDay: Long): ReportPeriod {
            val (year, month, _) = dateParts(epochDay)
            return when (type) {
                PeriodType.MONTH -> ReportPeriod(type, year, month)
                PeriodType.QUARTER -> ReportPeriod(type, year, (month - 1) / 3 + 1)
                PeriodType.YEAR -> ReportPeriod(type, year)
            }
        }

        /** epoch day → (año, mes 1..12, día). */
        fun dateParts(epochDay: Long): Triple<Int, Int, Int> {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = epochDay * 86_400_000L }
            return Triple(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH) + 1, utc.get(Calendar.DAY_OF_MONTH))
        }
    }
}
