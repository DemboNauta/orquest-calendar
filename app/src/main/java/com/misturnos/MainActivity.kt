package com.misturnos

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.misturnos.ui.MainScreen
import com.misturnos.ui.MainViewModel
import com.misturnos.ui.theme.MisTurnosTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> if (uris.isNotEmpty()) viewModel.processImages(uris) }

    private val requestCalendar = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            viewModel.loadCalendars()
            viewModel.syncToCalendar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadCalendars()

        // Tras una importación correcta, sincroniza automáticamente con el calendario.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.autoSync.collect { ensureCalendarThenSync() }
            }
        }

        setContent {
            MisTurnosTheme {
                val state by viewModel.state.collectAsState()
                MainScreen(
                    state = state,
                    onImportImages = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSync = { ensureCalendarThenSync() },
                    onSelectCalendar = viewModel::selectCalendar,
                    onClear = viewModel::clear,
                )
            }
        }
    }

    private fun ensureCalendarThenSync() {
        requestCalendar.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }
}
