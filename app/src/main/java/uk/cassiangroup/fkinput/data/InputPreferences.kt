package uk.cassiangroup.fkinput.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import uk.cassiangroup.fkinput.core.KeyboardLayout
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class InputPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("input", Context.MODE_PRIVATE)

    fun layout(landscape: Boolean): KeyboardLayout {
        val fallback = if (landscape) KeyboardLayout.FULL_KEY else KeyboardLayout.NINE_KEY
        return runCatching {
            KeyboardLayout.valueOf(prefs.getString(if (landscape) "landscape" else "portrait", null) ?: "")
        }.getOrDefault(fallback)
    }

    fun setLayout(landscape: Boolean, value: KeyboardLayout) {
        prefs.edit().putString(if (landscape) "landscape" else "portrait", value.name).apply()
    }

    fun speechEndpoint(): String? = decrypt("speech_endpoint")

    fun setSpeechEndpoint(value: String) {
        val endpoint = value.trim()
        if (endpoint.isNotEmpty()) {
            val uri = URI(endpoint)
            require(uri.scheme.equals("wss", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
                "请输入不含账号信息的 WSS 地址"
            }
        }
        encrypt("speech_endpoint", endpoint)
    }

    fun speechToken(): String? = decrypt("speech_token")

    fun setSpeechToken(value: String) = encrypt("speech_token", value.trim())

    private fun decrypt(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun encrypt(name: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("fk_speech_config", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "fk_speech_config",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
