package ua.school.windowsdesktop

import android.os.Bundle
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ua.school.windowsdesktop.data.LearningFileRepository
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var repository: LearningFileRepository? = null
    private var loadState by mutableStateOf<LoadState>(LoadState.Loading)
    private var keyboardLanguage by mutableStateOf("uk")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                when (val state = loadState) {
                    LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is LoadState.Ready -> WindowsLearningDesktopApp(state.repository, keyboardLanguage)
                    is LoadState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(getString(R.string.storage_open_failed, state.message))
                    }
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
        lifecycleScope.launch {
            while (isActive) {
                refreshKeyboardLanguage()
                delay(250)
            }
        }
    }

    private fun refreshKeyboardLanguage() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val subtype = manager.currentInputMethodSubtype
        val tag = subtype?.languageTag?.ifBlank { subtype.locale.replace('_', '-') }.orEmpty()
        val language = Locale.forLanguageTag(tag).language.ifBlank { Locale.getDefault().language }
        keyboardLanguage = language.lowercase(Locale.ROOT).take(2)
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
