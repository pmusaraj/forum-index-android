package com.musaraj.forumindex

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackSheet(
    enrolled: Boolean, sending: Boolean, result: String?,
    onSend: (FeedbackSubmission, () -> Unit) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("feedback", Context.MODE_PRIVATE) }
    var message by remember { mutableStateOf(preferences.getString("message", "").orEmpty()) }
    var requestId by remember { mutableStateOf(preferences.getString("request_id", null) ?: UUID.randomUUID().toString()) }
    val count = message.trim().let { it.codePointCount(0, it.length) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Feedback", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(message, onValueChange = {
                message = it
                requestId = UUID.randomUUID().toString()
                preferences.edit().putString("message", message).putString("request_id", requestId).apply()
            }, label = { Text("Feedback message") }, minLines = 4, maxLines = 8, enabled = !sending, modifier = Modifier.fillMaxWidth())
            Text("$count / 1000")
            Text("Feedback is linked to your contributor profile and sent to app admins with your app and OS versions.")
            if (!enrolled) Text("Enable contributions in Settings to send feedback. Your draft is saved.")
            result?.let { Text(it) }
            Button(enabled = enrolled && !sending && count in 1..1000, onClick = {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val submittedId = requestId
                preferences.edit().putString("request_id", requestId).apply()
                onSend(FeedbackSubmission(requestId, message, info.versionName ?: "unknown", info.longVersionCode.toString(), android.os.Build.VERSION.RELEASE)) {
                    if (preferences.getString("request_id", null) == submittedId) {
                        message = ""
                        requestId = UUID.randomUUID().toString()
                        preferences.edit().remove("message").putString("request_id", requestId).apply()
                    }
                }
            }) { Text(if (sending) "Sending…" else "Send feedback") }
        }
    }
}
