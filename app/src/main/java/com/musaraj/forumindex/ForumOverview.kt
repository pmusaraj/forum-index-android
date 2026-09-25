package com.musaraj.forumindex

import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ForumOverview(
    forumId: Int,
    onDismiss: () -> Unit,
    fetch: suspend (Int) -> ForumDetail = { withContext(Dispatchers.IO) { HttpForumIndexApi().fetchForum(it) } },
) {
    var detail by remember(forumId) { mutableStateOf<ForumDetail?>(null) }
    var failed by remember(forumId) { mutableStateOf(false) }
    var attempt by remember(forumId) { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(forumId, attempt) {
        failed = false
        try { detail = fetch(forumId) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(Modifier.testTag("forum-overview"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val loaded = detail
                when {
                    loaded != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ForumIcon(loaded.forum, 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(loaded.forum.name, fontWeight = FontWeight.Bold)
                        }
                        Text(loaded.description?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
                            ?.takeIf { it.isNotBlank() } ?: "No description available.", maxLines = 6)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        loaded.siteUrl?.let { url ->
                            TextButton(onClick = { openExternalTopic(url, context::startActivity) }, Modifier.testTag("forum-overview-forum-link")) {
                                Text("Open forum in browser")
                            }
                        }
                        loaded.organizationUrl?.let { url ->
                            TextButton(onClick = { openExternalTopic(url, context::startActivity) }, Modifier.testTag("forum-overview-organization-link")) {
                                Text("Organization website\n${url.host}")
                            }
                        }
                    }
                    failed -> {
                        Text("Couldn’t load site information.")
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
                    }
                    else -> { CircularProgressIndicator(); Text("Loading site information…") }
                }
            }
        },
    )
}
