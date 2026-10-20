package com.musaraj.forumindex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TopicReaderMetadata(
    post: MarkdownPost?,
    publishedAt: Instant?,
    hasSummary: Boolean,
    showsSummary: Boolean,
    onToggleSummary: () -> Unit,
) {
    if (post?.username == null && publishedAt == null && post?.publishedAt == null && !hasSummary) return
    // Fit both controls on one line when possible, matching iOS's ViewThatFits.
    // Otherwise keep the summary control trailing on a separate line.
    Layout(modifier = Modifier.fillMaxWidth().testTag("topic-preview-details"), content = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (post?.username != null) {
                ForumIcon(ForumSummary(0, post.username, "", null, post.avatarUrl), size = 28.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                post?.username?.let { Text("@$it", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                (publishedAt ?: post?.publishedAt)?.let {
                    Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (hasSummary) {
            TextButton(
                onClick = onToggleSummary,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("topic-summary-toggle").semantics {
                    stateDescription = if (showsSummary) "Expanded" else "Collapsed"
                },
            ) {
                Text(if (showsSummary) "Hide AI summary" else "AI summary", fontSize = 12.sp)
            }
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val byline = measurables[0].measure(loose)
        val button = measurables.getOrNull(1)?.measure(loose)
        val horizontalGap = 12.dp.roundToPx()
        val verticalGap = 4.dp.roundToPx()
        val stacked = button != null && byline.width + horizontalGap + button.width > constraints.maxWidth
        val height = if (stacked) byline.height + verticalGap + button!!.height else maxOf(byline.height, button?.height ?: 0)
        layout(constraints.maxWidth, height) {
            byline.placeRelative(0, if (stacked) 0 else (height - byline.height) / 2)
            button?.placeRelative(constraints.maxWidth - button.width,
                if (stacked) byline.height + verticalGap else (height - button.height) / 2)
        }
    }
}
