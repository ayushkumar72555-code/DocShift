package com.ayush.docshift

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.ayush.docshift.ui.screen.*
import com.ayush.docshift.ui.theme.DocShiftTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DocShiftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.initialize(this, intent)

        setContent {
            DocShiftTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    val screen = uiState.screen

                    BackHandler(
                        enabled = screen != Screen.Home && screen != Screen.Tutorial
                    ) {
                        viewModel.goBack()
                    }

                    screen?.let { currentScreen ->
                        @OptIn(ExperimentalAnimationApi::class)
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                if (initialState == Screen.Home || initialState == Screen.Tutorial) {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(300)
                                    ) with slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(300)
                                    )
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(300)
                                    ) with slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(300)
                                    )
                                }
                            }
                        ) { target ->
                            when (target) {
                                Screen.SharedChooser -> SharedActionChooserScreen(
                                    imageUris = uiState.sharedUris,
                                    onCompress = {
                                        viewModel.selectSharedAction(Screen.Compress)
                                    },
                                    onResize = {
                                        viewModel.selectSharedAction(Screen.Resize)
                                    },
                                    onImageToPdf = {
                                        viewModel.selectSharedAction(Screen.ImageToPdf)
                                    },
                                    onBack = viewModel::goHome
                                )

                                Screen.Tutorial -> TutorialScreen(
                                    onFinish = {
                                        viewModel.finishTutorial(this@MainActivity)
                                    }
                                )

                                Screen.Home -> HomeScreen(
                                    onSelect = viewModel::selectFromHome
                                )

                                Screen.Compress -> CompressScreen(
                                    contentResolver = contentResolver,
                                    cacheDir = cacheDir,
                                    initialUris = uiState.sharedUris,
                                    onBack = viewModel::goHome
                                )

                                Screen.Resize -> ResizeScreen(
                                    contentResolver = contentResolver,
                                    cacheDir = cacheDir,
                                    initialUris = uiState.sharedUris,
                                    onBack = viewModel::goHome
                                )

                                Screen.ImageToPdf -> ImageToPdfScreen(
                                    contentResolver = contentResolver,
                                    cacheDir = cacheDir,
                                    initialUris = uiState.sharedUris,
                                    onBack = viewModel::goHome
                                )

                                Screen.PdfToImage -> PdfToImageScreen(
                                    contentResolver = contentResolver,
                                    cacheDir = cacheDir,
                                    initialPdf = uiState.sharedUris.firstOrNull(),
                                    onBack = viewModel::goHome
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleNewIntent(intent)
    }
}
