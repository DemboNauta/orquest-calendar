package com.misturnos.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.misturnos.calendar.CalendarInfo
import com.misturnos.calendar.CalendarSync
import com.misturnos.calendar.SyncResult
import com.misturnos.data.ScheduleStore
import com.misturnos.model.WeekSchedule
import com.misturnos.ocr.OcrService
import com.misturnos.parser.ShiftParser
import com.misturnos.widget.ShiftWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = false,
    val weeks: List<WeekSchedule> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarId: Long? = null,
    val message: String? = null,
) {
    val totalHours: Double get() = weeks.sumOf { it.totalHours }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ocr = OcrService()
    private val parser = ShiftParser()
    private val calendarSync = CalendarSync(app)
    private val store = ScheduleStore(app)

    private val _state = MutableStateFlow(UiState(weeks = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Se emite tras una importación correcta para que la UI lance la sincronización automática. */
    private val _autoSync = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoSync: SharedFlow<Unit> = _autoSync.asSharedFlow()

    /** Procesa una o varias capturas, las parsea y las fusiona por semana. */
    fun processImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val parsed = mutableListOf<WeekSchedule>()
            var failed = 0
            for (uri in uris) {
                runCatching {
                    val lines = ocr.recognizeLines(getApplication(), uri)
                    parser.parse(lines)
                }.onSuccess { week ->
                    if (week != null) parsed += week else failed++
                }.onFailure { failed++ }
            }

            // Fusionamos con las semanas ya cargadas, quedándonos con la última versión de cada una.
            val merged = (_state.value.weeks + parsed)
                .associateBy { it.start }
                .values
                .sortedBy { it.start }

            if (parsed.isNotEmpty()) {
                store.save(merged)
                ShiftWidgetProvider.refresh(getApplication())
                _autoSync.tryEmit(Unit)
            }

            val msg = when {
                parsed.isEmpty() -> "No pude leer turnos en la imagen. Asegúrate de capturar la pantalla \"Mis turnos\"."
                failed > 0 -> "Procesadas ${parsed.size}, $failed sin turnos reconocibles."
                else -> "Leídas ${parsed.size} semana(s)."
            }
            _state.update { it.copy(loading = false, weeks = merged, message = msg) }
        }
    }

    fun loadCalendars() {
        if (!calendarSync.hasPermissions()) return
        val cals = calendarSync.availableCalendars()
        _state.update {
            it.copy(
                calendars = cals,
                selectedCalendarId = it.selectedCalendarId ?: cals.firstOrNull()?.id,
            )
        }
    }

    fun selectCalendar(id: Long) = _state.update { it.copy(selectedCalendarId = id) }

    fun clear() {
        store.clear()
        ShiftWidgetProvider.refresh(getApplication())
        _state.update { UiState(calendars = it.calendars, selectedCalendarId = it.selectedCalendarId) }
    }

    fun syncToCalendar() {
        val calId = _state.value.selectedCalendarId ?: run {
            _state.update { it.copy(message = "Elige un calendario primero.") }
            return
        }
        val allDays = _state.value.weeks.flatMap { it.days }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result: SyncResult = withContext(Dispatchers.IO) {
                calendarSync.sync(calId, allDays)
            }
            _state.update {
                it.copy(
                    loading = false,
                    message = "Sincronizado: ${result.inserted} nuevos, ${result.updated} actualizados.",
                )
            }
        }
    }
}
