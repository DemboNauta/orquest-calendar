package com.misturnos

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.misturnos.ui.MainScreen
import com.misturnos.ui.MainViewModel

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
        setContent {
            MaterialTheme {
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
