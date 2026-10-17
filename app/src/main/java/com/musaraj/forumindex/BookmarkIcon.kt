package com.musaraj.forumindex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun BookmarkIcon(saved: Boolean, unread: Boolean = false) {
    val color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * .25f, size.height * .12f)
            lineTo(size.width * .75f, size.height * .12f)
            lineTo(size.width * .75f, size.height * .9f)
            lineTo(size.width * .5f, size.height * .7f)
            lineTo(size.width * .25f, size.height * .9f)
            close()
        }
        if (saved) drawPath(path, color) else drawPath(path, color, style = Stroke(2.dp.toPx()))
        if (unread) drawArc(color, -90f, 180f, useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * .65f, size.height * .25f),
            size = androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .5f))
    }
}
