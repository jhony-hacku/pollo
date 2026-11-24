package com.sge.finca.ui.screens.reports

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sge.finca.domain.report.CategoryTotal
import com.sge.finca.domain.report.FinancialReport
import com.sge.finca.domain.report.PeriodType
import com.sge.finca.platform.report.ExportFormat
import com.sge.finca.ui.format.BusinessDateFormat
import com.sge.finca.ui.format.Cop

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)

/**
 * Reportes y balance: lo que entró, lo que salió y en qué, por mes, trimestre o año. El PDF y el
 * CSV llevan lo mismo más el detalle de cada movimiento, para el administrador.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                viewModel.onNoAppToShare()
            }
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(message)
        } finally {
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reportes y balance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        val report = state.report
        if (state.isLoading || report == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text("Únete a una finca para ver sus reportes")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PeriodSelector(
                    type = state.periodType,
                    label = report.period.label,
                    canGoNext = state.canGoNext,
                    onTypeChange = viewModel::onPeriodTypeChange,
                    onPrevious = viewModel::onPrevious,
                    onNext = viewModel::onNext,
                )
            }
            item { BalanceCard(report) }
            if (report.isEmpty) {
                item {
                    Text(
                        "No hay movimientos en este periodo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                item { ChartCard(report) }
                if (report.expenseByCategory.isNotEmpty()) {
                    item { CategoryCard("¿En qué se gastó?", report.expenseByCategory, ExpenseColor) }
                }
                if (report.incomeByCategory.isNotEmpty()) {
                    item { CategoryCard("¿De dónde entró la plata?", report.incomeByCategory, IncomeColor) }
                }
            }
            item {
                ExportCard(
                    enabled = !report.isEmpty,
                    exporting = state.exporting,
                    onExport = viewModel::onExport,
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    type: PeriodType,
    label: String,
    canGoNext: Boolean,
    onTypeChange: (PeriodType) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PeriodType.MONTH to "Mes", PeriodType.QUARTER to "Trimestre", PeriodType.YEAR to "Año")
                .forEach { (option, text) ->
                    FilterChip(selected = type == option, onClick = { onTypeChange(option) }, label = { Text(text) })
                }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, contentDescription = "Periodo anterior") }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Periodo siguiente")
            }
        }
    }
}

@Composable
private fun BalanceCard(report: FinancialReport) {
    val positive = report.balanceCents >= 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Balance del periodo", style = MaterialTheme.typography.labelLarge)
            Text(
                Cop.format(report.balanceCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else ExpenseColor,
            )
            Text(
                "Del ${BusinessDateFormat.format(report.period.fromDay)} al ${BusinessDateFormat.format(report.period.toDay)} · " +
                    "${report.transactions.size} movimientos",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Total("Ingresos", report.incomeCents, IncomeColor, up = true, modifier = Modifier.weight(1f))
                Total("Egresos", report.expenseCents, ExpenseColor, up = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Total(label: String, cents: Long, color: Color, up: Boolean, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Cop.format(cents), style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(report: FinancialReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (report.period.type == PeriodType.MONTH) "Por semana" else "Por mes",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Legend("Ingresos", IncomeColor)
                Legend("Egresos", ExpenseColor)
            }
            val max = report.buckets.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) }?.takeIf { it > 0 } ?: 1L
            val gridColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val slot = size.width / report.buckets.size
                val barWidth = (slot * 0.3f).coerceAtMost(28.dp.toPx())
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 0.5.dp.toPx())
                report.buckets.forEachIndexed { i, bucket ->
                    val center = slot * i + slot / 2
                    val incomeHeight = size.height * bucket.incomeCents / max
                    val expenseHeight = size.height * bucket.expenseCents / max
                    drawRoundRect(
                        IncomeColor,
                        topLeft = Offset(center - barWidth - 2f, size.height - incomeHeight),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawRoundRect(
                        ExpenseColor,
                        topLeft = Offset(center + 2f, size.height - expenseHeight),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                report.buckets.forEach { bucket ->
                    Text(
                        bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Máximo: ${Cop.format(max)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CategoryCard(title: String, rows: List<CategoryTotal>, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Cop.format(row.totalCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.weight(1f).height(8.dp)) {
                            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                            drawRoundRect(
                                color,
                                size = Size(size.width * row.share.toFloat(), size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        Text(
                            "  ${"%.0f".format(row.share * 100)} % · ${row.count} mov.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCard(enabled: Boolean, exporting: ExportFormat?, onExport: (ExportFormat) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enviar al administrador", style = MaterialTheme.typography.titleMedium)
            Text(
                "El PDF trae el resumen, el gráfico, los totales por categoría y el detalle de cada movimiento. " +
                    "El archivo de Excel (CSV) sirve para filtrar y sumar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onExport(ExportFormat.PDF) },
                    enabled = enabled && exporting == null,
                    modifier = Modifier.weight(1f),
                ) {
                    ExportLabel(Icons.Default.PictureAsPdf, "PDF", exporting == ExportFormat.PDF)
                }
                OutlinedButton(
                    onClick = { onExport(ExportFormat.CSV) },
                    enabled = enabled && exporting == null,
                    modifier = Modifier.weight(1f),
                ) {
                    ExportLabel(Icons.Default.TableChart, "Excel (CSV)", exporting == ExportFormat.CSV)
                }
            }
        }
    }
}

@Composable
private fun ExportLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.width(8.dp))
    Text(text)
}
