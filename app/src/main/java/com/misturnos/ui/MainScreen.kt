package com.misturnos.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.misturnos.model.DayShift
import com.misturnos.model.DayType
import com.misturnos.model.WeekSchedule
import com.misturnos.ui.theme.RestColor
import com.misturnos.util.ShiftFormat

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mis Turnos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportImages,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Importar") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (state.weeks.isEmpty() && !state.loading) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = state.message,
                onImport = onImportImages,
            )
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SummaryHeader(state) }

            state.message?.let { msg ->
                item {
                    AnimatedVisibility(visible = true) {
                        MessageBanner(msg)
                    }
                }
            }

            if (state.calendars.isNotEmpty()) {
                item {
                    CalendarPicker(
                        state = state,
                        onSelectCalendar = onSelectCalendar,
                        onSync = onSync,
                    )
                }
            }

            items(state.weeks) { week -> WeekCard(week) }

            item {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Limpiar todo")
                }
            }
        }
            if (state.loading) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: UiState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Horas totales",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                ShiftFormat.hours(state.totalHours),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            val days = state.weeks.sumOf { it.workedDays }
            Text(
                "${state.weeks.size} semana(s) · $days días trabajados",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPicker(
    state: UiState,
    onSelectCalendar: (Long) -> Unit,
    onSync: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sincronizar con Google Calendar", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.calendars.take(4).forEach { cal ->
                    FilterChip(
                        selected = cal.id == state.selectedCalendarId,
                        onClick = { onSelectCalendar(cal.id) },
                        label = { Text(cal.displayName, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            FilledTonalButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir turnos al calendario")
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            message,
            Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WeekCard(week: WeekSchedule) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ShiftFormat.rangeLabel(week.start, week.end),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                HoursPill(week.totalHours, big = true)
            }
            week.days.forEachIndexed { i, day ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                DayRow(day)
            }
        }
    }
}

@Composable
private fun DayRow(day: DayShift) {
    val isWork = day.dayType == DayType.WORK
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayBadge(day)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ShiftFormat.dayDetail(day),
                fontWeight = if (isWork) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isWork) MaterialTheme.colorScheme.onSurface else RestColor,
                style = MaterialTheme.typography.bodyLarge,
            )
            day.location?.takeIf { isWork }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isWork) {
            Spacer(Modifier.width(8.dp))
            HoursPill(day.totalHours, big = false)
        }
    }
}

@Composable
private fun DayBadge(day: DayShift) {
    val bg = if (day.dayType == DayType.WORK) MaterialTheme.colorScheme.onSurface else RestColor
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${day.date.dayOfMonth}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, ShiftFormat.ES)
                    .replaceFirstChar { it.uppercase() }.take(3),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun HoursPill(hours: Double, big: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = if (big) 14.dp else 10.dp, vertical = if (big) 6.dp else 4.dp),
    ) {
        Text(
            ShiftFormat.hours(hours),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 15.sp else 13.sp,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, message: String?, onImport: () -> Unit) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Aún no hay turnos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message ?: "Importa una captura de la pantalla \"Mis turnos\" de Orquest y leeré tus horarios automáticamente.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onImport) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importar captura")
        }
    }
}
