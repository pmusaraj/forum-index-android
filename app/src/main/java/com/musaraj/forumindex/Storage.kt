package com.musaraj.forumindex

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StarredTopic(
    val title: String,
    val url: String,
    val forumName: String,
    val topicId: Int? = null,
    val forumId: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = addedAt,
    val latestReplyAt: Long? = null,
    val forum: ForumSummary? = null,
    val publishedAt: java.time.Instant? = null,
    val replyCount: Int = 0,
    val externalSignals: List<ExternalSignal> = emptyList(),
    val lastReadPostNumber: Int? = null,
    val readerMarkdownUrl: java.net.URL? = if (forum?.supportsMarkdown == true) runCatching { java.net.URL(url) }.getOrNull() else null,
) {
    val hasUnreadReplies get() = latestReplyAt?.let { it > lastReadAt } == true
}

interface PreferencesStore {
    var introducedFeedIds: Set<String>
        get() = emptySet()
        set(value) {}

    var forYouIntroduced: Boolean
        get() = false
        set(value) {}
    var visibleSubjectOrder: List<String>
    var stars: List<StarredTopic>
    var openedTopicIds: Set<String>
    var contributionsOptedOut: Boolean
    var contributorDisplayName: String?
    var deviceNameOverride: String?
    fun markOpened(forumId: Int, topicId: Int)
    fun isOpened(forumId: Int, topicId: Int): Boolean
}

class ForumIndexPreferences(context: Context) : PreferencesStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var appearance: AppAppearance
        get() = AppAppearance.entries.firstOrNull { it.name == preferences.getString("appearance", null) } ?: AppAppearance.AUTO
        set(value) { preferences.edit().putString("appearance", value.name).apply() }

    override var contributionsOptedOut: Boolean
        get() = preferences.getBoolean("contributions_opted_out", false)
        set(value) { check(preferences.edit().putBoolean("contributions_opted_out", value).commit()) }

    override var contributorDisplayName: String?
        get() = preferences.getString("contributor_display_name", null)
        set(value) { preferences.edit().putString("contributor_display_name", value).apply() }

    override var forYouIntroduced: Boolean
        get() = preferences.getBoolean("for_you_introduced", false)
        set(value) { preferences.edit().putBoolean("for_you_introduced", value).apply() }

    override var introducedFeedIds: Set<String>
        get() = preferences.getStringSet("introduced_feeds", emptySet()).orEmpty().toSet()
        set(value) { preferences.edit().putStringSet("introduced_feeds", value).apply() }

    override var visibleSubjectOrder: List<String>
        get() = readArray(SUBJECTS) { getString(it) }
        set(value) = writeArray(SUBJECTS, JSONArray(value))

    override var stars: List<StarredTopic>
        get() = readArray(STARS) { index ->
            getJSONObject(index).let {
                StarredTopic(
                    it.getString("title"),
                    it.getString("url"),
                    it.getString("forum_name"),
                    if (it.isNull("topic_id")) null else it.getInt("topic_id"),
                    if (it.isNull("forum_id")) null else it.getInt("forum_id"),
                    it.optLong("added_at", index.toLong()), it.optLong("last_read_at", System.currentTimeMillis()),
                    if (it.isNull("latest_reply_at")) null else it.getLong("latest_reply_at"),
                    it.optJSONObject("forum")?.let { f -> ForumSummary(f.getInt("id"), f.getString("name"), f.optString("slug"),
                        f.optString("base_url").takeIf(String::isNotEmpty)?.let(::javaUrl),
                        f.optString("icon_url").takeIf(String::isNotEmpty)?.let(::javaUrl), f.optBoolean("supports_markdown")) },
                    runCatching { it.optString("published_at").takeIf(String::isNotEmpty)?.let(java.time.Instant::parse) }.getOrNull(),
                    it.optInt("reply_count", 0),
                    it.optJSONArray("external_signals")?.let { entries -> (0 until entries.length()).mapNotNull { i ->
                        val entry = entries.optJSONObject(i) ?: return@mapNotNull null
                        val source = ExternalSignalSource.entries.firstOrNull { source -> source.apiValue == entry.optString("source") }
                        val url = javaUrl(entry.optString("url"))
                        if (source != null && url != null) ExternalSignal(source, url) else null
                    } }.orEmpty(),
                    if (it.isNull("last_read_post_number")) null else it.getInt("last_read_post_number"),
                    if (it.has("reader_markdown_url")) javaUrl(it.optString("reader_markdown_url"))
                    else if (it.optJSONObject("forum")?.optBoolean("supports_markdown") == true) javaUrl(it.getString("url")) else null,
                )
            }
        }
        set(value) = writeArray(STARS, JSONArray().also { array -> value.forEach { star ->
            array.put(JSONObject()
                .put("title", star.title)
                .put("url", star.url)
                .put("forum_name", star.forumName)
                .put("topic_id", star.topicId)
                .put("published_at", star.publishedAt?.toString()).put("reply_count", star.replyCount)
                .put("external_signals", JSONArray().also { signals -> star.externalSignals.forEach {
                    signals.put(JSONObject().put("source", it.source.apiValue).put("url", it.url.toString()))
                } })
                .put("reader_markdown_url", star.readerMarkdownUrl?.toString() ?: JSONObject.NULL)
                .put("last_read_post_number", star.lastReadPostNumber)
                .put("added_at", star.addedAt).put("last_read_at", star.lastReadAt).put("latest_reply_at", star.latestReplyAt)
                .put("forum", star.forum?.let { f -> JSONObject().put("id", f.id).put("name", f.name).put("slug", f.slug)
                    .put("base_url", f.baseUrl?.toString()).put("icon_url", f.iconUrl?.toString()).put("supports_markdown", f.supportsMarkdown) })
                .also { if (star.forumId != null) it.put("forum_id", star.forumId) })
        } })

    override var openedTopicIds: Set<String>
        get() = readArray(OPENED) { getString(it) }.toSet()
        set(value) = writeArray(OPENED, JSONArray(value.sorted()))

    override var deviceNameOverride: String?
        get() = try {
            preferences.getString(DEVICE_NAME, null)
        } catch (_: ClassCastException) {
            preferences.edit().remove(DEVICE_NAME).apply()
            null
        }
        set(value) {
            preferences.edit().let { editor ->
                if (value == null) editor.remove(DEVICE_NAME) else editor.putString(DEVICE_NAME, value)
            }.apply()
        }

    override fun markOpened(forumId: Int, topicId: Int) {
        openedTopicIds = openedTopicIds + "$forumId:$topicId"
    }

    override fun isOpened(forumId: Int, topicId: Int) = "$forumId:$topicId" in openedTopicIds

    private fun javaUrl(value: String) = try { validTopicUrl(java.net.URL(value)) } catch (_: Exception) { null }

    private fun <T> readArray(key: String, value: JSONArray.(Int) -> T): List<T> {
        return try {
            val raw = preferences.getString(key, null) ?: return emptyList()
            JSONArray(raw).let { array -> List(array.length()) { index -> array.value(index) } }
        } catch (_: Exception) {
            preferences.edit().remove(key).apply()
            emptyList()
        }
    }

    private fun writeArray(key: String, value: JSONArray) {
        preferences.edit().putString(key, value.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "forum_index"
        const val SUBJECTS = "subjects"
        const val STARS = "stars"
        const val OPENED = "opened"
        const val DEVICE_NAME = "device_name"
    }
}

interface EnrollmentStore {
    fun save(enrollment: Enrollment)
    fun load(): Enrollment?
    fun delete()
}

class SecureTokenStore(context: Context) : EnrollmentStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun save(enrollment: Enrollment) {
        require(enrollment.token.isNotBlank() && enrollment.token.none(Char::isISOControl)) { "invalid token" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(enrollment.token.toByteArray(StandardCharsets.UTF_8))
        val blob = cipher.iv + ciphertext
        preferences.edit()
            .putString(TOKEN, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(INSTALLATION, enrollment.installation.toJson().toString())
            .apply()
    }

    override fun load(): Enrollment? {
        return try {
            val encoded = preferences.getString(TOKEN, null) ?: return signedOut()
            val metadata = preferences.getString(INSTALLATION, null) ?: return signedOut()
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            require(blob.size > IV_BYTES + 16)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, IV_BYTES)))
            val token = String(cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)), StandardCharsets.UTF_8)
            require(token.isNotBlank() && token.none(Char::isISOControl))
            Enrollment(token, JSONObject(metadata).installation())
        } catch (_: Exception) {
            signedOut()
        }
    }

    override fun delete() {
        check(preferences.edit().clear().commit()) { "could not clear contribution credential" }
    }

    private fun signedOut(): Enrollment? {
        delete()
        return null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private fun Installation.toJson() = JSONObject()
        .put("public_id", publicId)
        .put("display_name", displayName)
        .put("device_name", deviceName)
        .put("device_verified", deviceVerified)
        .put("trusted", trusted).put("read_count", readCount).put("for_you_eligible", forYouEligible)

    private fun JSONObject.installation() = Installation(
        getString("public_id"), getString("display_name"), getString("device_name"),
        getBoolean("device_verified"), getBoolean("trusted"),
        if (isNull("read_count")) null else getInt("read_count"), optBoolean("for_you_eligible"),
    )

    private companion object {
        const val PREFERENCES = "forum_index_secure"
        const val TOKEN = "token"
        const val INSTALLATION = "installation"
        const val KEY_ALIAS = "forum_index_contribution_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
