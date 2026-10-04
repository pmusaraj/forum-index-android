package com.musaraj.forumindex

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in asset capture using production composables and public iOS store fixtures. */
class StoreScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun captureStoreScreenshots() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureStore") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun fixture(name: String) = instrumentation.context.assets.open("store/$name").bufferedReader().use { it.readText() }
        val topics = ForumIndexJson.feed(fixture("Feed.json")).results
        val subjects = ForumIndexJson.navigation(fixture("Subjects.json")).subjects
        val forum = ForumIndexJson.forumDetail(fixture("Forum.json"))
        val markdown = fixture("Reader.md")
        val destinations = listOf(Destination.Main) + subjects.map { Destination.Subject(it) }
        val preferred = listOf("ai", "technology", "creative", "markets", "opensource", "jobs", "gaming")
        val visible = listOf(Destination.Main) + preferred.mapNotNull { slug ->
            subjects.find { it.slug == slug }?.let { Destination.Subject(it) }
        }
        val state = UiState(
            taxonomy = TaxonomyState.Loaded(subjects), allDestinations = destinations,
            visibleDestinations = visible, selectedDestination = Destination.Main,
            feeds = visible.associate { it.id to FeedState.Loaded(topics, 1, false) },
            contribution = ContributionState(loading = false),
        )
        val stars = topics.take(6).map { StarredTopic(it.title, it.url.toString(), it.forum.name, it.id, it.forum.id) }
        val readerTopic = topics.first { it.id == 103187 }
        val session = TopicDetailSession.create(topics, readerTopic, Destination.Main.id)!!
        var dark by mutableStateOf(false)
        var screen by mutableStateOf("feed")
        var generation by mutableIntStateOf(0)
        compose.setContent {
            ForumIndexTheme(dark) {
                CompositionLocalProvider(LocalAppearanceSettings provides AppearanceSettings(if (dark) AppAppearance.DARK else AppAppearance.LIGHT)) {
                    Surface(Modifier.fillMaxSize()) {
                        key(generation) {
                            Box(Modifier.fillMaxSize().safeDrawingPadding()) { ForumIndexReader(state, stars = stars) }
                            if (screen == "reader" || screen == "community") {
                                TopicDetailScreen(session, { false }, {}, {}, {},
                                    source = TopicMarkdownSource { MarkdownPage(markdown, it) })
                            }
                            if (screen == "community") ForumOverview(forum.forum.id, {}, fetch = { forum })
                        }
                    }
                }
            }
        }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle {
                dark = appearance == "dark"
                val color = if (dark) 0xFF1C1C1A.toInt() else 0xFFFAF6EE.toInt()
                val style = if (dark) SystemBarStyle.dark(color) else SystemBarStyle.light(color, color)
                compose.activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                screen = "feed"
                generation++
            }
            fun capture(name: String) {
                val only = InstrumentationRegistry.getArguments().getString("captureOnly")
                if (only != null && only != name) return
                compose.waitForIdle()
                Thread.sleep(10_000) // Settle sheets, images, and system bars before the OS capture.
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(instrumentation.targetContext.getExternalFilesDir(null), "store-screenshots/$appearance/$name.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithTag("topic-952-105238").assertIsDisplayed()
            capture("01-discover")
            compose.runOnIdle { screen = "reader" }
            compose.onNodeWithTag("topic-detail-pager").assertExists()
            capture("02-reader")
            compose.runOnIdle { screen = "community" }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("forum-overview-forum-link").fetchSemanticsNodes().isNotEmpty() }
            capture("03-community")
            compose.runOnIdle { screen = "feed"; generation++ }
            compose.onNodeWithContentDescription("Starred topics").performClick()
            expandSheet()
            capture("04-starred")
            compose.runOnIdle { generation++ }
            compose.onNodeWithContentDescription("Choose subjects").performClick()
            expandSheet()
            compose.onNode(hasScrollAction() and hasAnyDescendant(hasContentDescription("Toggle AI"))).performScrollToIndex(0)
            capture("05-subjects")
        }
    }

    private fun expandSheet() {
        compose.waitForIdle()
        val expandable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        if (expandable.fetchSemanticsNodes().isNotEmpty()) {
            expandable.onFirst().performSemanticsAction(SemanticsActions.Expand) { it() }
        }
        compose.waitForIdle()
    }
}
