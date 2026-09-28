package com.musaraj.forumindex

import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicAbout(
    topic: Topic,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    fetch: suspend (Int) -> ForumDetail = { withContext(Dispatchers.IO) { HttpForumIndexApi().fetchForum(it) } },
) {
    var detail by remember(topic.forum.id) { mutableStateOf<ForumDetail?>(null) }
    var failed by remember(topic.forum.id) { mutableStateOf(false) }
    var attempt by remember(topic.forum.id) { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(topic.forum.id, attempt) {
        failed = false
        try { detail = fetch(topic.forum.id) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).testTag("topic-about"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("About", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Text("Topic", style = MaterialTheme.typography.labelLarge)
            Text(topic.title, fontWeight = FontWeight.Bold)
            SelectionContainer { Text(plainDescription(topic.excerpt) ?: "No excerpt available.") }
            if (validTopicUrl(topic.url) != null) {
                TextButton(onClick = onOpen, modifier = Modifier.testTag("topic-about-open-topic")) { Text("Open topic") }
            }
            HorizontalDivider()
            Text("Community", style = MaterialTheme.typography.labelLarge)
            Text(topic.forum.name, fontWeight = FontWeight.Bold)
            when {
                detail != null -> SelectionContainer { Text(plainDescription(detail?.description) ?: "No description available.") }
                failed -> {
                    Text("Couldn’t load community information.")
                    TextButton(onClick = { attempt++ }) { Text("Retry") }
                }
                else -> { CircularProgressIndicator(); Text("Loading community information…") }
            }
            (detail?.siteUrl ?: topic.forum.baseUrl)?.let { url ->
                TextButton(onClick = { openExternalTopic(url, context::startActivity) }) { Text("Community website") }
            }
            detail?.organizationUrl?.let { url ->
                TextButton(onClick = { openExternalTopic(url, context::startActivity) },
                    modifier = Modifier.testTag("topic-about-parent-link")) { Text("Parent website") }
            }
        }
    }
}

private fun plainDescription(value: String?): String? = value?.let {
    Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().replace('\u00a0', ' ').trim().takeIf(String::isNotBlank)
}
