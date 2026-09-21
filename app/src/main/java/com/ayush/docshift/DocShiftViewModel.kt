package com.ayush.docshift

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayush.docshift.storage.FirstLaunchStore
import com.ayush.docshift.ui.screen.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DocShiftUiState(
    val screen: Screen? = null,
    val sharedUris: List<Uri> = emptyList(),
    val initialized: Boolean = false
)

class DocShiftViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DocShiftUiState())
    val uiState: StateFlow<DocShiftUiState> = _uiState.asStateFlow()

    private var pendingSharedScreen: Screen? = null

    fun initialize(context: Context, intent: Intent?) {
        if (_uiState.value.initialized) return

        val shared = extractSharedContent(intent)
        pendingSharedScreen = shared?.first

        viewModelScope.launch {
            val firstLaunch = FirstLaunchStore.isFirstLaunch(context)
            _uiState.value = DocShiftUiState(
                screen = when {
                    firstLaunch -> Screen.Tutorial
                    shared != null -> shared.first
                    else -> Screen.Home
                },
                sharedUris = shared?.second.orEmpty(),
                initialized = true
            )
        }
    }

    fun handleNewIntent(intent: Intent?) {
        val shared = extractSharedContent(intent) ?: return
        _uiState.value = _uiState.value.copy(
            screen = shared.first,
            sharedUris = shared.second
        )
    }

    fun finishTutorial(context: Context) {
        viewModelScope.launch {
            FirstLaunchStore.setLaunched(context)
            val state = _uiState.value
            val nextScreen = pendingSharedScreen ?: if (state.sharedUris.isNotEmpty()) {
                Screen.SharedChooser
            } else {
                Screen.Home
            }
            pendingSharedScreen = null
            _uiState.value = state.copy(screen = nextScreen)
        }
    }

    fun selectFromHome(screen: Screen) {
        _uiState.value = _uiState.value.copy(
            screen = screen,
            sharedUris = emptyList()
        )
    }

    fun selectSharedAction(screen: Screen) {
        _uiState.value = _uiState.value.copy(screen = screen)
    }

    fun goHome() {
        _uiState.value = _uiState.value.copy(
            screen = Screen.Home,
            sharedUris = emptyList()
        )
    }

    fun goBack() {
        val state = _uiState.value
        if (state.screen != Screen.Home && state.screen != Screen.Tutorial) {
            goHome()
        }
    }

    private fun extractSharedContent(intent: Intent?): Pair<Screen, List<Uri>>? {
        intent ?: return null

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let {
                    when {
                        intent.type?.startsWith("image") == true ->
                            Screen.SharedChooser to listOf(it)

                        intent.type == "application/pdf" ->
                            Screen.PdfResize to listOf(it)

                        else -> null
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    Screen.SharedChooser to uris
                } else {
                    null
                }
            }

            else -> null
        }
    }
}
