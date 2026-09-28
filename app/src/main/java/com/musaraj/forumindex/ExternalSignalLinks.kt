package com.musaraj.forumindex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExternalSignalIcon(source: ExternalSignalSource) {
    Box(Modifier.size(18.dp).semantics { contentDescription = source.displayName }
        .then(if (source == ExternalSignalSource.HACKER_NEWS) Modifier.background(Color(0xFFFF6600)) else Modifier),
        contentAlignment = Alignment.Center) {
        Text(if (source == ExternalSignalSource.HACKER_NEWS) "Y" else "🦞",
            fontSize = 14.sp, color = Color.White)
    }
}

@Composable
internal fun ExternalSignalLinks(signals: List<ExternalSignal>) {
    val context = LocalContext.current
    Column {
        signals.forEach { signal ->
            TextButton(onClick = { openExternalTopic(signal.url, context::startActivity) },
                modifier = Modifier.testTag("external-signal-${signal.source.apiValue}")) {
                ExternalSignalIcon(signal.source)
                Spacer(Modifier.width(8.dp))
                Text("Discuss on ${signal.source.displayName} ↗")
            }
        }
    }
}
