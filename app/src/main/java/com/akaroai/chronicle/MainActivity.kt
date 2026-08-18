package com.akaroai.chronicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akaroai.chronicle.data.ChronicleRepository
import com.akaroai.chronicle.ui.ChronicleScreen
import com.akaroai.chronicle.ui.ChronicleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ChronicleApp
        val repository = ChronicleRepository(app.database.dao())

        setContent {
            MaterialTheme {
                val vm: ChronicleViewModel = viewModel(
                    factory = ChronicleViewModel.Factory(
                        repository = repository,
                        settingsStore = app.providerSettings
                    )
                )
                ChronicleScreen(vm)
            }
        }
    }
}
