package uk.cassiangroup.fkinput.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputPreferencesTest {
    @Test fun speechConfigurationIsLocalEncryptedAndRequiresWss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = InputPreferences(context)
        val endpoint = "wss://example.invalid/stream"
        val token = "mock-credential"

        assertThrows(IllegalArgumentException::class.java) {
            preferences.setSpeechEndpoint("http://example.invalid/stream")
        }
        preferences.setSpeechEndpoint(endpoint)
        preferences.setSpeechToken(token)

        assertEquals(endpoint, preferences.speechEndpoint())
        assertEquals(token, preferences.speechToken())
        val stored = context.getSharedPreferences("input", Context.MODE_PRIVATE)
        assertNotEquals(endpoint, stored.getString("speech_endpoint", null))
        assertNotEquals(token, stored.getString("speech_token", null))

        preferences.setSpeechEndpoint("")
        preferences.setSpeechToken("")
        assertNull(preferences.speechEndpoint())
        assertNull(preferences.speechToken())
    }
}
