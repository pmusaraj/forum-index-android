package com.musaraj.forumindex

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.net.URL

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
                val stars by model.stars.collectAsState()
                var openTopic by remember { mutableStateOf<Topic?>(null) }
                LaunchedEffect(model) { model.launch() }
                ForumIndexReader(
                    uiState = state,
                    stars = stars,
                    isOpened = model::isOpened,
                    isStarred = model::isStarred,
                    onSelect = model::select,
                    onRetryTaxonomy = model::refreshTaxonomy,
                    onUpdateVisibleOrder = model::updateVisibleOrder,
                    onRefresh = { model.refreshFeed(it) },
                    onRetry = { model.retry(it) },
                    onLoadNextPage = { model.loadNextPage(it) },
                    onOpenTopic = { topic ->
                        validTopicUrl(topic.url)?.let {
                            model.markOpened(topic)
                            openTopic = topic
                        }
                    },
                    onToggleStar = model::toggleStar,
                    onReport = { topic, kind -> model.report(topic.id, kind, true) },
                    onEnroll = { displayName, deviceName -> model.enroll(displayName, deviceName) },
                    onRefreshContribution = model::refreshEnrollment,
                    onUpdateDevice = { model.updateDevice(it) },
                    onOptOut = model::optOut,
                    onOpenStarred = { star ->
                        val url = try { validTopicUrl(URL(star.url)) } catch (_: Exception) { null }
                        if (url != null) {
                            model.markOpened(star)
                            openExternalTopic(url, ::startActivity)
                        }
                    },
                    onRemoveStar = model::removeStar,
                )
                openTopic?.let { topic ->
                    TopicWebView(
                        topic = topic,
                        starred = model.isStarred(topic),
                        onToggleStar = { model.toggleStar(topic) },
                        onDismiss = { openTopic = null },
                    )
                }
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
