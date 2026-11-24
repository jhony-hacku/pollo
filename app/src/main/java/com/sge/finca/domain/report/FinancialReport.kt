package com.sge.finca.domain.report

import com.sge.finca.domain.model.Transaction
import com.sge.finca.domain.model.TransactionType

/** Total de una categoría dentro del periodo. [share] va de 0 a 1 respecto a su tipo. */
data class CategoryTotal(val name: String, val totalCents: Long, val count: Int, val share: Double)

/** Un tramo del gráfico: una semana del mes o un mes del trimestre/año. */
data class ReportBucket(val label: String, val incomeCents: Long, val expenseCents: Long)

/**
 * Reporte financiero de la finca para un periodo: lo que ve el propietario en pantalla y lo que
 * se exporta a PDF o CSV para el administrador. Se calcula en Kotlin puro (ver [build]).
 */
data class FinancialReport(
    val period: ReportPeriod,
    val incomeCents: Long,
    val expenseCents: Long,
    val incomeByCategory: List<CategoryTotal>,
    val expenseByCategory: List<CategoryTotal>,
    val buckets: List<ReportBucket>,
    /** Del más reciente al más antiguo. */
    val transactions: List<Transaction>,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val isEmpty: Boolean get() = transactions.isEmpty()

    companion object {
        /** Arma el reporte con las transacciones que caen dentro de [period] (las demás se ignoran). */
        fun build(period: ReportPeriod, transactions: List<Transaction>): FinancialReport {
            val inPeriod = transactions
                .filter { period.contains(it.dateEpochDay) }
                .sortedWith(compareByDescending<Transaction> { it.dateEpochDay }.thenByDescending { it.createdAt })
            val incomes = inPeriod.filter { it.type == TransactionType.INCOME }
            val expenses = inPeriod.filter { it.type == TransactionType.EXPENSE }

            return FinancialReport(
                period = period,
                incomeCents = incomes.sumOf { it.amountCents },
                expenseCents = expenses.sumOf { it.amountCents },
                incomeByCategory = byCategory(incomes),
                expenseByCategory = byCategory(expenses),
                buckets = buckets(period, inPeriod),
                transactions = inPeriod,
            )
        }

        private fun byCategory(transactions: List<Transaction>): List<CategoryTotal> {
            val total = transactions.sumOf { it.amountCents }.toDouble()
            return transactions
                .groupBy { it.categoryName.ifBlank { "Sin categoría" } }
                .map { (name, list) ->
                    val sum = list.sumOf { it.amountCents }
                    CategoryTotal(name, sum, list.size, if (total > 0) sum / total else 0.0)
                }
                .sortedByDescending { it.totalCents }
        }

        /** Mes: semanas (días 1–7, 8–14...). Trimestre y año: un tramo por mes. */
        private fun buckets(period: ReportPeriod, transactions: List<Transaction>): List<ReportBucket> {
            fun totals(list: List<Transaction>) =
                list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents } to
                    list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }

            return if (period.type == PeriodType.MONTH) {
                val daysInMonth = (period.toDay - period.fromDay + 1).toInt()
                val weeks = (daysInMonth + 6) / 7
                (1..weeks).map { week ->
                    val firstDay = (week - 1) * 7 + 1
                    val lastDay = minOf(week * 7, daysInMonth)
                    val (income, expense) = totals(
                        transactions.filter { ReportPeriod.dateParts(it.dateEpochDay).third in firstDay..lastDay },
                    )
                    ReportBucket("$firstDay–$lastDay", income, expense)
                }
            } else {
                (0 until period.monthCount).map { offset ->
                    val month = period.firstMonth + offset
                    val (income, expense) = totals(
                        transactions.filter { ReportPeriod.dateParts(it.dateEpochDay).second == month },
                    )
                    ReportBucket(ReportPeriod.MONTH_SHORT[month - 1].replaceFirstChar { it.uppercase() }, income, expense)
                }
            }
        }
    }
}
