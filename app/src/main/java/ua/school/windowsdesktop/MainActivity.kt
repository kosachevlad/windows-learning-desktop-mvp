package ua.school.windowsdesktop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ua.school.windowsdesktop.data.LearningFileRepository

class MainActivity : ComponentActivity() {
    private var repository: LearningFileRepository? = null
    private var loadState by mutableStateOf<LoadState>(LoadState.Loading)
    private var keyboardLanguage by mutableStateOf("unknown")
    private var crashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(applicationContext)
        crashReport = CrashReporter.read(applicationContext)
        setContent {
            MaterialTheme {
                when (val state = loadState) {
                    LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is LoadState.Ready -> WindowsLearningDesktopApp(state.repository, keyboardLanguage, ::changeKeyboardLanguage)
                    is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(getString(R.string.storage_open_failed, state.message))
                    }
                }
                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Звіт про збій") },
                        text = { Text(report, Modifier.verticalScroll(rememberScrollState())) },
                        confirmButton = { TextButton(onClick = {
                            CrashReporter.clear(applicationContext)
                            crashReport = null
                        }) { Text("Закрити звіт") } },
                    )
                }
            }
        }
        lifecycleScope.launch {
            try {
                val opened = LearningFileRepository.open(applicationContext)
                repository = opened
                loadState = LoadState.Ready(opened)
            } catch (failure: Exception) {
                loadState = LoadState.Failed(failure.message ?: getString(R.string.error_unknown))
            }
        }
    }

    private fun changeKeyboardLanguage(language: String) {
        keyboardLanguage = language
    }

    override fun onDestroy() {
        val opened = repository
        repository = null
        if (opened != null) runBlocking { opened.close() }
        super.onDestroy()
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val repository: LearningFileRepository) : LoadState
    data class Failed(val message: String) : LoadState
}
