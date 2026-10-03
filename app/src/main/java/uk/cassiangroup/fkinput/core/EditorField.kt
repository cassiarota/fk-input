package uk.cassiangroup.fkinput.core

import android.text.InputType

enum class EditorFieldKind { TEXT, PHONE, NUMBER, EMAIL, URL }

fun editorFieldKind(inputType: Int): EditorFieldKind = when (inputType and InputType.TYPE_MASK_CLASS) {
    InputType.TYPE_CLASS_PHONE -> EditorFieldKind.PHONE
    InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> EditorFieldKind.NUMBER
    InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EditorFieldKind.EMAIL
        InputType.TYPE_TEXT_VARIATION_URI -> EditorFieldKind.URL
        else -> EditorFieldKind.TEXT
    }
    else -> EditorFieldKind.TEXT
}

fun isSecureField(inputType: Int): Boolean {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return (inputClass == InputType.TYPE_CLASS_TEXT &&
        (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
        (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
}
