package uk.cassiangroup.fkinput

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardCandidatesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = instrumentation.uiAutomation

    private fun shell(command: String): String = ui.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        listOf(root) + (0 until root.childCount).flatMap { index -> root.getChild(index)?.let(::nodes).orEmpty() }

    private fun keyboardNodes(): List<AccessibilityNodeInfo> = ui.windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root?.let(::nodes).orEmpty()

    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            keyboardNodes().firstOrNull { it.text?.toString() == text && it.isVisibleToUser }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Keyboard did not show $text: ${keyboardNodes().map { it.text }}")
    }

    private fun tap(text: String) {
        val bounds = Rect().also(awaitText(text)::getBoundsInScreen)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            assertTrue(ui.injectInputEvent(event, true))
            event.recycle()
        }
    }

    private fun pairedCandidates(): List<AccessibilityNodeInfo> {
        val visible = keyboardNodes().filter { it.isVisibleToUser }
        val top = visible.filter { it.contentDescription?.contains("正常候选") == true ||
            it.contentDescription?.contains("没有同音同调") == true }
        assertTrue(top.isNotEmpty())
        for (item in top) {
            val description = item.contentDescription.toString()
            val normal = if ("对应正常候选 " in description) description.substringAfter("对应正常候选 ")
                else description.substringBefore(" 没有")
            val bounds = Rect().also(item::getBoundsInScreen)
            assertTrue("Missing paired word: $description", visible.any { below ->
                val lower = Rect().also(below::getBoundsInScreen)
                below.text?.toString() == normal && lower.left == bounds.left && lower.top >= bounds.bottom
            })
            if (item.text?.toString() == "—") assertFalse(item.isEnabled)
            else assertNotEquals(normal, item.text?.toString())
        }
        return top
    }

    @Test fun qwertyRowsStayPairedAndCommitTheirOwnCandidate() {
        val previousIme = shell("settings get secure default_input_method")
        val previousInfo = ui.serviceInfo
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val context = instrumentation.targetContext
        context.getSharedPreferences("input", Context.MODE_PRIVATE).edit()
            .putString("portrait", "NINE_KEY").putString("landscape", "NINE_KEY").commit()
        val activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        val editor = views(activity.findViewById(android.R.id.content)).filterIsInstance<EditText>()
            .first { it.hint?.toString() == "输入词或短语" }
        try {
            shell("ime enable uk.cassiangroup.fkinput/.FkInputMethodService")
            shell("ime set uk.cassiangroup.fkinput/.FkInputMethodService")
            instrumentation.runOnMainSync {
                editor.requestFocus()
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            assertFalse(awaitText("26键").isEnabled)
            for (letter in 'a'..'z') awaitText(letter.toString())
            fun type(input: String) { for (letter in input) tap(letter.toString()) }
            fun assertEditor(expected: String) = instrumentation.runOnMainSync { assertEquals(expected, editor.text.toString()) }
            fun clearEditor() = instrumentation.runOnMainSync { editor.setText("") }

            type("made")
            awaitText("妈的")
            pairedCandidates()
            tap("妈地")
            assertEditor("妈地")
            clearEditor()
            type("made")
            tap("妈的")
            assertEditor("妈的")
            clearEditor()

            type("ni")
            awaitText("你")
            val before = pairedCandidates().first()
            var scroll = before.parent
            while (scroll.className?.toString() != "android.widget.HorizontalScrollView") scroll = scroll.parent
            assertTrue(scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            SystemClock.sleep(300)
            val after = pairedCandidates().first()
            assertNotEquals(before.contentDescription?.toString(), after.contentDescription?.toString())
            tap("⌫")
            tap("⌫")

            type("xiexie")
            awaitText("谢谢")
            pairedCandidates()
            assertFalse(awaitText("—").isEnabled)
            tap("空格 · 语音")
            assertEditor("谢谢")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            if (previousIme != "null" && previousIme.isNotBlank()) shell("ime set $previousIme")
            ui.serviceInfo = previousInfo
        }
    }
}
