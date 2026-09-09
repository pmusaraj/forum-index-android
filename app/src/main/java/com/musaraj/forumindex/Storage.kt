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
    val topicId: Int,
)

class ForumIndexPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var visibleSubjectOrder: List<String>
        get() = readArray(SUBJECTS) { getString(it) }
        set(value) = writeArray(SUBJECTS, JSONArray(value))

    var stars: List<StarredTopic>
        get() = readArray(STARS) { index ->
            getJSONObject(index).let {
                StarredTopic(it.getString("title"), it.getString("url"), it.getString("forum_name"), it.getInt("topic_id"))
            }
        }
        set(value) = writeArray(STARS, JSONArray().also { array -> value.forEach { star ->
            array.put(JSONObject()
                .put("title", star.title)
                .put("url", star.url)
                .put("forum_name", star.forumName)
                .put("topic_id", star.topicId))
        } })

    var openedTopicIds: Set<String>
        get() = readArray(OPENED) { getString(it) }.toSet()
        set(value) = writeArray(OPENED, JSONArray(value.sorted()))

    var deviceNameOverride: String?
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

    fun markOpened(forumId: Int, topicId: Int) {
        openedTopicIds = openedTopicIds + "$forumId:$topicId"
    }

    fun isOpened(forumId: Int, topicId: Int) = "$forumId:$topicId" in openedTopicIds

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

class SecureTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(enrollment: Enrollment) {
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

    fun load(): Enrollment? {
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

    fun delete() {
        preferences.edit().clear().apply()
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
        .put("trusted", trusted)

    private fun JSONObject.installation() = Installation(
        getString("public_id"), getString("display_name"), getString("device_name"),
        getBoolean("device_verified"), getBoolean("trusted"),
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
