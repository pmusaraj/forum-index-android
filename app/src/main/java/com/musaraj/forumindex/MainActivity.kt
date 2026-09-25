package com.musaraj.forumindex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val initialAppearance = ForumIndexPreferences(this).appearance
        val initialDark = initialAppearance == AppAppearance.DARK || (initialAppearance == AppAppearance.AUTO &&
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (initialDark) R.style.Theme_ForumIndex_Dark else R.style.Theme_ForumIndex)
        super.onCreate(savedInstanceState)
        val initialBarStyle = if (initialDark) SystemBarStyle.dark(0xFF1C1C1A.toInt())
            else SystemBarStyle.light(0xFFFAF6EE.toInt(), 0xFFFAF6EE.toInt())
        enableEdgeToEdge(statusBarStyle = initialBarStyle, navigationBarStyle = initialBarStyle)
        setContent {
            val preferences = remember { ForumIndexPreferences(applicationContext) }
            var appearance by remember { mutableStateOf(preferences.appearance) }
            val dark = appearance == AppAppearance.DARK || (appearance == AppAppearance.AUTO && isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(0xFF1C1C1A.toInt()) else SystemBarStyle.light(0xFFFAF6EE.toInt(), 0xFFFAF6EE.toInt())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (dark) 0xFF1C1C1A.toInt() else 0xFFFAF6EE.toInt()))
            }
            CompositionLocalProvider(LocalAppearanceSettings provides AppearanceSettings(appearance) {
                preferences.appearance = it
                appearance = it
            }) {
                ForumIndexTheme(dark) {
                    val appContext = applicationContext
                    val factory = remember {
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
                    val detailSession by model.detailSession.collectAsState()
                    val feedReturnTarget by model.feedReturnTarget.collectAsState()
                    LaunchedEffect(model) { model.launch(android.os.Build.MODEL) }
                    Box(Modifier.safeDrawingPadding().then(if (detailSession != null) Modifier.clearAndSetSemantics { } else Modifier)) {
                        ForumIndexReader(
                            uiState = state,
                            defaultDeviceName = preferences.deviceNameOverride ?: android.os.Build.MODEL,
                            defaultDisplayName = model.defaultDisplayName,
                            stars = stars,
                            isOpened = { "${it.forum.id}:${it.id}" in state.openedTopicIds },
                            isStarred = remember(stars) { { topic -> model.isStarred(topic) } },
                            onSelect = model::select,
                            onRetryTaxonomy = model::refreshTaxonomy,
                            onUpdateVisibleOrder = model::updateVisibleOrder,
                            onRefresh = { model.refreshFeed(it) },
                            onRetry = { model.retry(it) },
                            onLoadNextPage = { model.loadNextPage(it) },
                            onOpenTopic = model::openTopic,
                            returnTarget = feedReturnTarget,
                            onToggleStar = model::toggleStar,
                            onReport = { topic, kind -> model.report(topic.id, kind, true) },
                            onEnroll = { displayName, deviceName -> model.enroll(displayName, deviceName) },
                            onRefreshContribution = model::refreshEnrollment,
                            onUpdateDevice = { model.updateDevice(it) },
                            onSubmitForum = model::submitForum,
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
                    }
                    detailSession?.let { session ->
                        TopicDetailScreen(
                            session = session,
                            isStarred = remember(stars) { { topic -> model.isStarred(topic) } },
                            onSelect = model::selectDetailTopic,
                            onToggleStar = model::toggleStar,
                            onDismiss = model::closeTopic,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ForumIndexTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val background = if (dark) Color(0xFF1C1C1A) else Color(0xFFFAF6EE)
    val surface = if (dark) Color(0xFF272724) else Color(0xFFF1EDE6)
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF54CAD0), onPrimary = Color(0xFF00373B),
        background = background, surface = background, surfaceVariant = surface,
        onBackground = Color(0xFFF5F3ED), onSurface = Color(0xFFF5F3ED),
    ) else lightColorScheme(
        primary = Color(0xFF008C95), onPrimary = Color.White,
        background = background, surface = background, surfaceVariant = surface,
        onBackground = Color.Black, onSurface = Color.Black,
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = background) { content() }
    }
}
