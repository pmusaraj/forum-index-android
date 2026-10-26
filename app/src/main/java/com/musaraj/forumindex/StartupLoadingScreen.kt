package com.musaraj.forumindex

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun StartupLoadingScreen() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("startup-loading"),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Original website wordmark, including its italic “i”, in both app appearances.
        Image(
            painter = painterResource(if (dark) R.drawable.forum_index_logo_dark else R.drawable.forum_index_logo_light),
            contentDescription = "Forum Index",
            modifier = Modifier.fillMaxWidth().aspectRatio(600f / 160f),
        )
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp).semantics { contentDescription = "Loading topics" },
            strokeWidth = 2.dp,
        )
    }
}
