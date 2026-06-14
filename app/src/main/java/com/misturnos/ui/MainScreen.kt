package com.misturnos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.WeekSchedule
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val es = Locale("es", "ES")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", es)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onImportImages: () -> Unit,
    onSync: () -> Unit,
    onSelectCalendar: (Long) -> Unit,
    onClear: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis Turnos → Calendar") }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportImages, enabled = !state.loading) {
                    Text("Importar capturas")
                }
                if (state.weeks.isNotEmpty()) {
                    OutlinedButton(onClick = onClear, enabled = !state.loading) { Text("Limpiar") }
                }
            }

            if (state.weeks.isNotEmpty()) {
                Text(
                    "Total: ${formatHours(state.totalHours)} en ${state.weeks.size} semana(s)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.calendars.isNotEmpty() && state.weeks.isNotEmpty()) {
                Text("Calendario destino:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.calendars.take(4).forEach { cal ->
                        FilterChip(
                            selected = cal.id == state.selectedCalendarId,
                            onClick = { onSelectCalendar(cal.id) },
                            label = { Text(cal.displayName, maxLines = 1) },
                        )
                    }
                }
                Button(
                    onClick = onSync,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sincronizar con Google Calendar") }
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.weeks) { week -> WeekCard(week) }
            }
        }
    }
}

@Composable
private fun WeekCard(week: WeekSchedule) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${week.start.format(dayFmt)} - ${week.end.format(dayFmt)}",
                    fontWeight = FontWeight.Bold,
                )
                Text(formatHours(week.totalHours), fontWeight = FontWeight.Bold)
            }
            week.days.forEach { DayRow(it) }
        }
    }
}

@Composable
private fun DayRow(day: DayShift) {
    val label = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, es)
        .replaceFirstChar { it.uppercase() }
    val detail = when (day.dayType) {
        DayType.WORK -> day.segments.joinToString(" / ") { "${it.start} - ${it.end}" }
        DayType.DAY_OFF -> "Día libre"
        DayType.UNASSIGNED -> "Sin asignaciones"
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ${day.date.dayOfMonth}", modifier = Modifier.padding(end = 8.dp))
        Text(detail, modifier = Modifier.padding(end = 8.dp))
        Text(if (day.dayType == DayType.WORK) formatHours(day.totalHours) else "")
    }
}

private fun formatHours(hours: Double): String {
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
