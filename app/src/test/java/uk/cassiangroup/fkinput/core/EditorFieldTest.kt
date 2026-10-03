package uk.cassiangroup.fkinput.core

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFieldTest {
    @Test fun selectsDedicatedFieldLayouts() {
        assertEquals(EditorFieldKind.PHONE, editorFieldKind(InputType.TYPE_CLASS_PHONE))
        assertEquals(EditorFieldKind.NUMBER, editorFieldKind(InputType.TYPE_CLASS_NUMBER))
        assertEquals(EditorFieldKind.NUMBER, editorFieldKind(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertEquals(EditorFieldKind.EMAIL, editorFieldKind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(EditorFieldKind.EMAIL, editorFieldKind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(EditorFieldKind.URL, editorFieldKind(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(EditorFieldKind.TEXT, editorFieldKind(InputType.TYPE_CLASS_TEXT))
    }

    @Test fun onlyPasswordVariationsAreSecure() {
        assertTrue(isSecureField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(isSecureField(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(isSecureField(InputType.TYPE_CLASS_PHONE))
        assertFalse(isSecureField(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))
    }
}
