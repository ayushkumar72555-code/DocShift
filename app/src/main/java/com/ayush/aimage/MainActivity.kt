package com.ayush.aimage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.ayush.aimage.storage.FirstLaunchStore
import com.ayush.aimage.ui.screen.*
import com.ayush.aimage.ui.theme.AImageTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AImageTheme {
                Surface(color = MaterialTheme.colorScheme.background) {

                    val context = this
                    val scope = rememberCoroutineScope()

                    /* ---------- Handle shared content ---------- */

                    val sharedData = remember {
                        extractSharedContent(intent)
                    }

                    /* ---------- Screen state ---------- */

                    var currentScreen by remember {
                        mutableStateOf<Screen?>(null)
                    }

                    /* ---------- Decide initial screen ---------- */

                    LaunchedEffect(Unit) {
                        currentScreen = when {
                            FirstLaunchStore.isFirstLaunch(context) ->
                                Screen.Tutorial

                            sharedData != null ->
                                sharedData.first

                            else ->
                                Screen.Home
                        }
                    }

                    /* ---------- System back ---------- */

                    BackHandler(
                        enabled = currentScreen != Screen.Home &&
                                currentScreen != Screen.Tutorial
                    ) {
                        currentScreen = Screen.Home
                    }

                    /* ---------- UI ---------- */

                    currentScreen?.let { screen ->

                        @OptIn(ExperimentalAnimationApi::class)
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                if (initialState == Screen.Home ||
                                    initialState == Screen.Tutorial
                                ) {
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
                                Screen.SharedChooser -> {
                                    val uris = sharedData?.second ?: emptyList()
                                    SharedActionChooserScreen(
                                        imageUris = uris,
                                        onCompress = {
                                            currentScreen = Screen.Compress
                                        },
                                        onImageToPdf = {
                                            currentScreen = Screen.ImageToPdf
                                        },
                                        onBack = {
                                            currentScreen = Screen.Home
                                        }
                                    )
                                }

                                Screen.Tutorial -> TutorialScreen(
                                    onFinish = {
                                        scope.launch {
                                            FirstLaunchStore.setLaunched(context)
                                            currentScreen = Screen.Home
                                        }
                                    }
                                )

                                Screen.Home -> HomeScreen(
                                    onSelect = { selected ->
                                        currentScreen = selected
                                    }
                                )

                                Screen.Compress -> {
                                    val uris = sharedData?.second ?: emptyList()
                                    CompressScreen(
                                        contentResolver = contentResolver,
                                        cacheDir = cacheDir,
                                        initialUris = uris,
                                        onBack = { currentScreen = Screen.Home }
                                    )
                                }

                                Screen.ImageToPdf -> {
                                    val uris = sharedData?.second ?: emptyList()
                                    ImageToPdfScreen(
                                        contentResolver = contentResolver,
                                        cacheDir = cacheDir,
                                        initialUris = uris,
                                        onBack = { currentScreen = Screen.Home }
                                    )
                                }

                                Screen.PdfToImage -> {
                                    val uri = sharedData?.second?.firstOrNull()
                                    PdfToImageScreen(
                                        contentResolver = contentResolver,
                                        cacheDir = cacheDir,
                                        initialPdf = uri,
                                        onBack = { currentScreen = Screen.Home }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /* ---------- Extract shared data ---------- */

    private fun extractSharedContent(
        intent: Intent
    ): Pair<Screen, List<Uri>>? {

        return when (intent.action) {

            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let {
                    when {
                        intent.type?.startsWith("image") == true ->
                            Screen.SharedChooser to listOf(it)

                        intent.type == "application/pdf" ->
                            Screen.PdfToImage to listOf(it)

                        else -> null
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris =
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.let {
                    Screen.SharedChooser to uris
                }
            }

            else -> null
        }
    }
}
