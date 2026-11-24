package com.sge.finca.ui.screens.reports

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sge.finca.domain.model.BusinessDate
import com.sge.finca.domain.model.TransactionFilter
import com.sge.finca.domain.report.FinancialReport
import com.sge.finca.domain.report.PeriodType
import com.sge.finca.domain.report.ReportPeriod
import com.sge.finca.domain.repository.FarmRepository
import com.sge.finca.domain.repository.SessionRepository
import com.sge.finca.domain.repository.TransactionRepository
import com.sge.finca.platform.report.ExportFormat
import com.sge.finca.platform.report.ReportExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reportes y balance (VIEW_BALANCE, #67): totales, gráfico y categorías del periodo elegido, y
 * exportación a PDF o CSV para enviarla al administrador. Lee de Room, así que funciona sin red
 * y se actualiza solo cuando llegan movimientos de otros miembros (#32).
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val transactionRepository: TransactionRepository,
    private val farmRepository: FarmRepository,
    private val reportExporter: ReportExporter,
) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.containing(PeriodType.MONTH, BusinessDate.today()))
    private val exporting = MutableStateFlow<ExportFormat?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val _shareRequests = Channel<Intent>(Channel.BUFFERED)
    /** Intents de compartir que la pantalla debe lanzar (una sola vez cada uno). */
    val shareRequests: Flow<Intent> = _shareRequests.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<Pair<FinancialReport?, String>> = sessionRepository.session
        .filter { !it.loading }
        .map { it.farmId }
        .flatMapLatest { farmId ->
            if (farmId == null) return@flatMapLatest flowOf(null to "")
            val transactions = period.flatMapLatest { p ->
                transactionRepository.observeTransactions(
                    farmId,
                    TransactionFilter(fromEpochDay = p.fromDay, toEpochDay = p.toDay),
                ).map { list -> FinancialReport.build(p, list) }
            }
            combine(transactions, farmRepository.observeFarm(farmId).catch { emit(null) }) { report, farm ->
                report to (farm?.name ?: "Mi finca")
            }
        }

    val uiState: StateFlow<ReportsUiState> =
        combine(content, period, exporting, message) { (report, farmName), current, format, pendingMessage ->
            ReportsUiState(
                isLoading = false,
                periodType = current.type,
                report = report,
                farmName = farmName,
                canGoNext = current.next().fromDay <= BusinessDate.today(),
                exporting = format,
                message = pendingMessage,
            )
        }
            .catch { error ->
                Log.e(TAG, "No se pudo calcular el reporte", error)
                emit(ReportsUiState(isLoading = false, message = "No se pudo calcular el reporte"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    /** Cambia a mes/trimestre/año manteniendo la fecha que se estaba viendo. */
    fun onPeriodTypeChange(type: PeriodType) {
        period.value = ReportPeriod.containing(type, period.value.fromDay)
    }

    fun onPrevious() {
        period.value = period.value.previous()
    }

    fun onNext() {
        val next = period.value.next()
        if (next.fromDay <= BusinessDate.today()) period.value = next
    }

    fun onExport(format: ExportFormat) {
        val state = uiState.value
        val report = state.report ?: return
        if (exporting.value != null) return
        exporting.value = format
        viewModelScope.launch {
            try {
                val member = sessionRepository.session.value.member
                val generatedBy = member?.displayName?.ifBlank { null } ?: member?.email ?: "SGE"
                _shareRequests.send(reportExporter.export(report, state.farmName, generatedBy, format))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo exportar el reporte", e)
                message.value = "No se pudo generar el archivo"
            } finally {
                exporting.value = null
            }
        }
    }

    fun onNoAppToShare() {
        message.value = "No hay una app para compartir el archivo"
    }

    fun onMessageShown() {
        message.value = null
    }

    private companion object {
        const val TAG = "SgeReports"
    }
}
