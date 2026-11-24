package com.sge.finca.ui.screens.reports

import com.sge.finca.domain.report.FinancialReport
import com.sge.finca.domain.report.PeriodType
import com.sge.finca.platform.report.ExportFormat

data class ReportsUiState(
    val isLoading: Boolean = true,
    val periodType: PeriodType = PeriodType.MONTH,
    val report: FinancialReport? = null,
    val farmName: String = "",
    /** No se navega a periodos futuros. */
    val canGoNext: Boolean = false,
    /** Formato que se está generando, para mostrar el indicador en su botón. */
    val exporting: ExportFormat? = null,
    val message: String? = null,
)
