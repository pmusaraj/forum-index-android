package com.musaraj.forumindex

import org.json.JSONObject

data class FeedbackSubmission(
    val requestId: String,
    val message: String,
    val appVersion: String,
    val appBuild: String,
    val osVersion: String,
) {
    val isValid get() = message.trim().let { it.codePointCount(0, it.length) in 1..1_000 }
    fun toJson(): JSONObject = JSONObject().put("request_id", requestId).put("message", message.trim())
        .put("app_version", appVersion).put("app_build", appBuild).put("platform", "android").put("os_version", osVersion)
}
