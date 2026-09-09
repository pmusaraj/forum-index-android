package com.musaraj.forumindex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(0xFFFAF6EE.toInt(), 0xFFFAF6EE.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFFAF6EE.toInt(), 0xFFFAF6EE.toInt()),
        )
        setContent {
            ForumIndexTheme {
                val appContext = applicationContext
                val factory = remember {
                    val preferences = ForumIndexPreferences(appContext)
                    val tokens = SecureTokenStore(appContext)
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ForumIndexViewModel(HttpForumIndexApi(), preferences, tokens) as T
                    }
                }
                val model: ForumIndexViewModel = viewModel(factory = factory)
                val state by model.uiState.collectAsState()
                LaunchedEffect(model) { model.launch() }
                ForumIndexReader(
                    uiState = state,
                    isOpened = model::isOpened,
                    onSelect = model::select,
                    onUpdateVisibleOrder = model::updateVisibleOrder,
                    onRefresh = { model.refreshFeed(it) },
                    onRetry = { model.retry(it) },
                    onLoadNextPage = { model.loadNextPage(it) },
                    onOpenTopic = { topic ->
                        topic.url?.let { url ->
                            model.markOpened(topic)
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString())))
                        }
                    },
                    onStars = {},
                    onSettings = {},
                )
            }
        }
    }
}

@Composable
private fun ForumIndexTheme(content: @Composable () -> Unit) {
    val background = Color(0xFFFAF6EE)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF008C95),
            background = background,
            surface = background,
            onBackground = Color.Black,
            onSurface = Color.Black,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Box(Modifier.fillMaxSize().background(background).safeDrawingPadding()) { content() }
        }
    }
}
