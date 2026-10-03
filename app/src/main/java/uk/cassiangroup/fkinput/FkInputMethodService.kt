package uk.cassiangroup.fkinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import uk.cassiangroup.fkinput.core.CandidateRules
import uk.cassiangroup.fkinput.core.CompositionState
import uk.cassiangroup.fkinput.core.EditorFieldKind
import uk.cassiangroup.fkinput.core.KeyboardLayout
import uk.cassiangroup.fkinput.core.PinyinEngine
import uk.cassiangroup.fkinput.core.PinyinGroup
import uk.cassiangroup.fkinput.core.WordCandidate
import uk.cassiangroup.fkinput.core.editorFieldKind
import uk.cassiangroup.fkinput.core.isSecureField
import uk.cassiangroup.fkinput.core.sensitivePositions
import uk.cassiangroup.fkinput.data.InputPreferences
import uk.cassiangroup.fkinput.data.InputStore
import uk.cassiangroup.fkinput.data.SensitiveWordRepository
import uk.cassiangroup.fkinput.voice.StreamingAsr
import java.util.concurrent.Executors

class FkInputMethodService : InputMethodService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: InputStore
    private lateinit var preferences: InputPreferences
    private var engine: PinyinEngine? = null
    private var rules: CandidateRules? = null
    private var layout = KeyboardLayout.NINE_KEY
    private var chinese = true
    private var shift = false
    private var secureField = false
    private var fieldKind = EditorFieldKind.TEXT
    private var numberInputType = 0
    private var panel = Panel.LETTERS
    private val composition = CompositionState()
    private var speechResult: String? = null
    private var selectedPinyin: String? = null
    private var groups: List<PinyinGroup> = emptyList()
    private var normal: List<WordCandidate> = emptyList()
    private var homophones: List<String> = emptyList()
    private var segmentLengths: List<Int> = emptyList()
    private var selectionAnchor: Int? = null
    private var selectionExtent: Int? = null
    private var repeatDelete: Runnable? = null
    private var generation = 0
    private var candidateReadyGeneration = 0
    private var sensitiveFirst = false
    private var speech: StreamingAsr? = null
    private var voicePreview = ""
    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var composing: TextView
    private lateinit var groupStrip: LinearLayout
    private lateinit var topCandidates: LinearLayout
    private lateinit var normalCandidates: LinearLayout
    private lateinit var editingRow: LinearLayout
    private lateinit var keyboard: LinearLayout

    override fun onCreate() {
        super.onCreate()
        store = InputStore(this)
        preferences = InputPreferences(this)
        layout = preferences.layout(isLandscape())
        worker.execute {
            runCatching { SensitiveWordRepository(this, store).initialize() }
                .onFailure { main.post { notice("本地敏感词库初始化失败") } }
            runCatching {
                val loadedEngine = PinyinEngine(this)
                val loadedRules = CandidateRules.fromAssets(this)
                main.post {
                    engine = loadedEngine
                    rules = loadedRules
                    updateCandidates()
                }
            }.onFailure {
                main.post { notice("拼音词典加载失败") }
            }
        }
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(8))
            setBackgroundColor(Color.rgb(226, 233, 244))
        }
        toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(toolbar)
        composing = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(34, 52, 83))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            minHeight = dp(30)
        }
        root.addView(composing)
        groupStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(scroll(groupStrip, dp(29)))
        topCandidates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(scroll(topCandidates, dp(44)))
        normalCandidates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(scroll(normalCandidates, dp(44)))
        editingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(editingRow)
        keyboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyboard)
        renderKeyboard()
        updateCandidates()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val inputType = attribute?.inputType ?: InputType.TYPE_CLASS_TEXT
        secureField = isSecureField(inputType)
        fieldKind = editorFieldKind(inputType)
        numberInputType = inputType
        chinese = !secureField && fieldKind == EditorFieldKind.TEXT
        shift = false
        panel = Panel.LETTERS
        selectionAnchor = null
        selectionExtent = null
        resetComposition()
        layout = if (fieldKind == EditorFieldKind.TEXT) preferences.layout(isLandscape()) else KeyboardLayout.FULL_KEY
        if (::root.isInitialized) renderKeyboard()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        stopRepeatDelete()
        selectionAnchor = null
        selectionExtent = null
        speech?.cancel()
        speech = null
        resetComposition()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layout = preferences.layout(isLandscape())
        if (::root.isInitialized) renderKeyboard()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        stopRepeatDelete()
        speech?.cancel()
        worker.shutdownNow()
        engine?.close()
        store.close()
        super.onDestroy()
    }

    private fun renderKeyboard() {
        if (!::root.isInitialized) return
        val compact = isLandscape()
        root.setPadding(dp(3), dp(4), dp(3), dp(if (compact) 20 else 24))
        composing.minHeight = dp(if (compact) 22 else 30)
        resizeStrip(groupStrip, if (compact) 22 else 29)
        resizeStrip(topCandidates, if (compact) 30 else 44)
        resizeStrip(normalCandidates, if (compact) 30 else 44)
        toolbar.removeAllViews()
        toolbar.addView(key(when (fieldKind) {
            EditorFieldKind.PHONE -> "电话"
            EditorFieldKind.NUMBER -> "数字"
            EditorFieldKind.EMAIL -> "邮箱"
            EditorFieldKind.URL -> "网址"
            EditorFieldKind.TEXT -> if (secureField || layout == KeyboardLayout.FULL_KEY) "全键" else "九键"
        }, 1f) {
            if (secureField || fieldKind != EditorFieldKind.TEXT) return@key
            if (composition.hasContent && !commitDefault()) return@key
            layout = if (layout == KeyboardLayout.NINE_KEY) KeyboardLayout.FULL_KEY else KeyboardLayout.NINE_KEY
            preferences.setLayout(isLandscape(), layout)
            renderKeyboard()
        })
        if (fieldKind == EditorFieldKind.TEXT || fieldKind == EditorFieldKind.EMAIL || fieldKind == EditorFieldKind.URL) {
            toolbar.addView(key(if (chinese) "中" else "英", 1f) { toggleLanguage() })
            toolbar.addView(key("123", 1f) { panel = Panel.NUMBERS; renderKeyboard() })
            toolbar.addView(key("符", 1f) { panel = Panel.SYMBOLS; renderKeyboard() })
        }
        toolbar.addView(key("⚙", 1f) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        toolbar.addView(key("切换", 1f) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        keyboard.removeAllViews()
        when {
            fieldKind == EditorFieldKind.PHONE -> renderPhoneKey()
            fieldKind == EditorFieldKind.NUMBER -> renderFieldNumberKey()
            panel == Panel.NUMBERS -> renderNumbers()
            panel == Panel.SYMBOLS -> renderSymbols()
            layout == KeyboardLayout.NINE_KEY && chinese && !secureField -> renderNineKey()
            else -> renderFullKey()
        }
        renderEditingRow()
        updateCandidateVisibility()
    }

    private fun renderNineKey() {
        addRow(listOf("，", "1", "2 ABC", "3 DEF", "⌫"), listOf(1f, 1f, 1.5f, 1.5f, 1f))
        addRow(listOf("。", "4 GHI", "5 JKL", "6 MNO", "重输"), listOf(1f, 1.5f, 1.5f, 1.5f, 1f))
        addRow(listOf("？", "7 PQRS", "8 TUV", "9 WXYZ", "↵"), listOf(1f, 1.5f, 1.5f, 1.5f, 1f))
        addRow(listOf("！", "符", "123", "0 · 语音", "空格", "中/英"),
            listOf(1f, 1f, 1f, 1.6f, 2f, 1.2f))
    }

    private fun renderFullKey() {
        val upper = !chinese && shift
        addRow("qwertyuiop".map { if (upper) it.uppercase() else it.toString() }, List(10) { 1f })
        addRow("asdfghjkl".map { if (upper) it.uppercase() else it.toString() }, List(9) { 1f })
        addRow(listOf("⇧") + "zxcvbnm".map { if (upper) it.uppercase() else it.toString() } + "⌫", List(9) { 1f })
        when (fieldKind) {
            EditorFieldKind.EMAIL -> addRow(listOf("123", "符", "@", ".", "_", "-", "↵"),
                listOf(1f, 1f, 1.4f, 1f, 1f, 1f, 1.2f))
            EditorFieldKind.URL -> addRow(listOf("123", "符", "/", ":", ".", "-", "↵"),
                listOf(1f, 1f, 1.4f, 1f, 1f, 1f, 1.2f))
            else -> addRow(
                listOf("123", "符", if (chinese) "，" else ",",
                    if (secureField) "空格" else "空格 · 语音", if (chinese) "。" else ".", "↵", "中/英"),
                listOf(1f, 1f, 1f, 4f, 1f, 1.2f, 1.2f)
            )
        }
    }

    private fun renderPhoneKey() {
        addRow(listOf("1", "2", "3"), List(3) { 1f })
        addRow(listOf("4", "5", "6"), List(3) { 1f })
        addRow(listOf("7", "8", "9"), List(3) { 1f })
        addRow(listOf("+", "0", "⌫"), List(3) { 1f })
        addRow(listOf("*", "#", "-", "↵"), List(4) { 1f })
    }

    private fun renderFieldNumberKey() {
        addRow(listOf("1", "2", "3"), List(3) { 1f })
        addRow(listOf("4", "5", "6"), List(3) { 1f })
        addRow(listOf("7", "8", "9"), List(3) { 1f })
        val controls = ArrayList<String>()
        if (numberInputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0) controls.add("-")
        controls.add("0")
        if (numberInputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) controls.add(".")
        if (numberInputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_DATETIME) {
            controls.add("/")
            controls.add(":")
        }
        controls.add("⌫")
        controls.add("↵")
        addRow(controls, List(controls.size) { 1f })
    }

    private fun renderEditingRow() {
        if (!::editingRow.isInitialized) return
        editingRow.removeAllViews()
        fun add(label: String, weight: Float = 1f, action: () -> Unit): TextView {
            val view = key(label, weight, action)
            view.textSize = 14f
            view.layoutParams = LinearLayout.LayoutParams(0, dp(34), weight).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            }
            editingRow.addView(view)
            return view
        }
        add("◀") { moveCursor(-1) }
        add("▶") { moveCursor(1) }
        add(if (selectionAnchor == null) "选择" else "取消选择", 1.5f) { toggleSelection() }
        val canSplit = chinese && composition.raw.length > 1 && segmentLengths.size > 1
        add("短词") { adjustSegment(-1) }.visibility = if (canSplit) View.VISIBLE else View.GONE
        add("长词") { adjustSegment(1) }.visibility = if (canSplit) View.VISIBLE else View.GONE
    }

    private fun compositionDisplay(): String {
        val selected = composition.selectedText
        val raw = composition.raw
        if (raw.isEmpty()) return selected
        val cursor = composition.cursor
        val cursorMarked = raw.substring(0, cursor) + "│" + raw.substring(cursor)
        return if (composition.pendingSuffix.isEmpty()) selected + cursorMarked
        else "$selected[${composition.candidateInput}]${composition.pendingSuffix}  ·  局部选词"
    }

    private fun adjustSegment(direction: Int) {
        if (segmentLengths.isEmpty() || composition.raw.isEmpty()) return
        val current = composition.segmentEnd
        val next = if (direction < 0) segmentLengths.lastOrNull { it < current }
            else segmentLengths.firstOrNull { it > current }
        if (next == null) return
        composition.setSegmentEnd(next)
        selectedPinyin = null
        updateCandidates()
    }

    private fun toggleSelection() {
        val connection = currentInputConnection ?: return
        if (composition.hasContent) {
            notice("请先完成拼音输入，再选中编辑框文字")
            return
        }
        val anchor = selectionAnchor
        if (anchor != null) {
            selectionAnchor = null
            val end = selectionExtent ?: anchor
            selectionExtent = null
            connection.setSelection(end, end)
        } else {
            val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted == null) {
                notice("当前应用不支持键盘内选中")
                return
            }
            selectionAnchor = extracted.selectionEnd
            selectionExtent = extracted.selectionEnd
        }
        renderEditingRow()
    }

    private fun moveCursor(direction: Int) {
        if (composition.hasContent) {
            if (composition.raw.isEmpty()) {
                composition.reopen(composition.selectedParts.lastIndex)
                selectedPinyin = null
                updateCandidates()
            }
            composition.moveCursor(direction)
            composing.text = compositionDisplay()
            return
        }
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted == null) {
            val code = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            return
        }
        val anchor = selectionAnchor
        val old = selectionExtent ?: if (direction < 0) extracted.selectionStart else extracted.selectionEnd
        val next = (old + direction).coerceAtLeast(0)
        if (anchor != null) {
            if (connection.setSelection(minOf(anchor, next), maxOf(anchor, next))) {
                selectionExtent = next
            }
        } else connection.setSelection(next, next)
    }

    private fun repeatDeleteGesture(view: TextView) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopRepeatDelete()
                    backspace()
                    val repeat = object : Runnable {
                        override fun run() {
                            backspace()
                            main.postDelayed(this, 65)
                        }
                    }
                    repeatDelete = repeat
                    main.postDelayed(repeat, 380)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeatDelete()
                    true
                }
                else -> true
            }
        }
    }

    private fun stopRepeatDelete() {
        repeatDelete?.let(main::removeCallbacks)
        repeatDelete = null
    }

    private fun renderNumbers() {
        addRow(listOf("1", "2", "3", "⌫"), listOf(1f, 1f, 1f, 1.1f))
        addRow(listOf("4", "5", "6", "-"), List(4) { 1f })
        addRow(listOf("7", "8", "9", "/"), List(4) { 1f })
        if (fieldKind == EditorFieldKind.EMAIL || fieldKind == EditorFieldKind.URL) {
            addRow(listOf("返回", "0", ".", "@", "↵"), listOf(1.5f, 1f, 1f, 1f, 1f))
        } else addRow(listOf("返回", "0", ".", "空格", "↵"), listOf(1.5f, 1f, 1f, 2f, 1f))
    }

    private fun renderSymbols() {
        if (chinese) {
            addRow(listOf("，", "。", "？", "！", "；", "："), List(6) { 1f })
            addRow(listOf("（", "）", "“", "”", "、", "…"), List(6) { 1f })
        } else {
            addRow(listOf(",", ".", "?", "!", ";", ":"), List(6) { 1f })
            addRow(listOf("(", ")", "'", "\"", "_", "/"), List(6) { 1f })
        }
        addRow(listOf("@", "#", "&", "%", "+", "="), List(6) { 1f })
        if (fieldKind == EditorFieldKind.EMAIL || fieldKind == EditorFieldKind.URL) {
            addRow(listOf("返回", "⌫", "-", "↵"), listOf(1.5f, 1f, 3f, 1f))
        } else addRow(listOf("返回", "⌫", "空格", "↵"), listOf(1.5f, 1f, 3f, 1f))
    }

    private fun addRow(labels: List<String>, weights: List<Float>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { index, label ->
            val button = key(label, weights[index]) { handleKey(label) }
            if (label == "0 · 语音" || label == "空格 · 语音") {
                voiceGesture(button) { handleKey(if (label.startsWith("0")) "0" else "空格") }
            } else if (label == "⌫") {
                repeatDeleteGesture(button)
            }
            row.addView(button)
        }
        keyboard.addView(row)
    }

    private fun key(label: String, weight: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = if (label.length > 5) 13f else 18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(30, 45, 68))
            background = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
                setColor(Color.WHITE)
            }
            elevation = dp(1).toFloat()
            val margins = LinearLayout.LayoutParams(0, dp(if (isLandscape()) 34 else 47), weight)
            margins.setMargins(dp(2), dp(2), dp(2), dp(2))
            layoutParams = margins
            setOnClickListener { onClick() }
        }

    private fun handleKey(label: String) {
        when (label) {
            "⌫" -> backspace()
            "重输" -> resetComposition()
            "中/英" -> toggleLanguage()
            "返回" -> { panel = Panel.LETTERS; renderKeyboard() }
            "123" -> { panel = Panel.NUMBERS; renderKeyboard() }
            "符" -> { panel = Panel.SYMBOLS; renderKeyboard() }
            "空格", "空格 · 语音" -> {
                if ((composition.hasContent || speechResult != null) && !secureField) commitDefault()
                else commitText(" ")
            }
            "↵" -> {
                if ((composition.hasContent || speechResult != null) && !commitDefault()) return
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                if (action == EditorInfo.IME_ACTION_NONE ||
                    !((currentInputConnection?.performEditorAction(action)) ?: false)
                ) commitText("\n")
            }
            "⇧" -> { shift = !shift; renderKeyboard() }
            else -> {
                val value = if (label.length > 1 && label[0] in '2'..'9') label.take(1) else label
                if (panel == Panel.LETTERS && chinese && !secureField &&
                    ((layout == KeyboardLayout.NINE_KEY && value[0] in '2'..'9') ||
                        (layout == KeyboardLayout.FULL_KEY && value.length == 1 && value[0] in 'a'..'z'))
                ) {
                    speechResult = null
                    composition.insert(value)
                    selectedPinyin = null
                    updateCandidates()
                } else {
                    if ((composition.hasContent || speechResult != null) && !commitDefault()) return
                    commitText(value)
                    if (shift && !chinese && value.length == 1 && value[0].isLetter()) {
                        shift = false
                        renderKeyboard()
                    }
                }
            }
        }
    }

    private fun toggleLanguage() {
        if (fieldKind != EditorFieldKind.TEXT) return
        if ((composition.hasContent || speechResult != null) && !commitDefault()) return
        chinese = !chinese && !secureField
        shift = false
        panel = Panel.LETTERS
        renderKeyboard()
        updateCandidates()
    }

    private fun backspace() {
        if (composition.hasContent) {
            composition.backspace()
            selectedPinyin = null
            updateCandidates()
        } else if (speechResult != null) {
            speechResult = null
            updateCandidates()
        } else {
            val connection = currentInputConnection ?: return
            val selected = connection.getSelectedText(0)
            if (!selected.isNullOrEmpty()) connection.commitText("", 1)
            else connection.deleteSurroundingTextInCodePoints(1, 0)
            selectionAnchor = null
            selectionExtent = null
            renderEditingRow()
        }
    }

    private fun updateCandidates() {
        if (!::root.isInitialized) return
        generation++
        val token = generation
        composing.text = when {
            voicePreview.isNotEmpty() -> "🎙 $voicePreview"
            speechResult != null -> speechResult
            else -> compositionDisplay()
        }
        updateCandidateVisibility()
        if (secureField || (!chinese && speechResult == null) ||
            (composition.raw.isEmpty() && speechResult == null)) {
            groups = emptyList()
            normal = emptyList()
            homophones = emptyList()
            segmentLengths = emptyList()
            sensitiveFirst = false
            candidateReadyGeneration = token
            renderCandidates()
            return
        }
        groups = emptyList()
        normal = emptyList()
        homophones = emptyList()
        segmentLengths = emptyList()
        renderCandidates()
        val activeEngine = engine
        val activeRules = rules
        if (activeEngine == null || activeRules == null) {
            composing.text = "词典加载中…"
            return
        }
        val input = composition.candidateInput
        val rawForLengths = composition.raw
        val spoken = speechResult
        val chosen = selectedPinyin
        val currentLayout = layout
        worker.execute {
            val terms = store.terms()
            val foundGroups = if (spoken != null) listOf(
                PinyinGroup("", listOf(WordCandidate(spoken, "", 0)), 0.0)
            ) else if (input.length < rawForLengths.length) activeEngine.exactGroups(input, currentLayout)
            else activeEngine.groups(input, currentLayout)
            val validLengths = if (spoken == null) activeEngine.segmentLengths(rawForLengths, currentLayout)
                else emptyList()
            val group = foundGroups.firstOrNull { it.pinyin == chosen } ?: foundGroups.firstOrNull()
            val ranked = group?.let {
                if (spoken == null) store.ordered(it.pinyin, it.candidates) else it.candidates
            }.orEmpty()
            val generated = ranked.firstOrNull()?.let { activeRules.homophones(it.text, terms) }.orEmpty()
            val firstIsSensitive = ranked.firstOrNull()?.let {
                sensitivePositions(it.text, terms).isNotEmpty()
            } ?: false
            main.post {
                if (token != generation) return@post
                groups = foundGroups
                selectedPinyin = group?.pinyin
                normal = ranked
                homophones = generated
                segmentLengths = validLengths
                sensitiveFirst = firstIsSensitive
                candidateReadyGeneration = token
                renderCandidates()
            }
        }
    }

    private fun renderCandidates() {
        if (!::root.isInitialized) return
        groupStrip.removeAllViews()
        composition.selectedParts.forEachIndexed { index, part ->
            groupStrip.addView(TextView(this).apply {
                text = "${part.text} ↶"
                textSize = 14f
                setPadding(dp(8), dp(2), dp(8), dp(2))
                setTextColor(Color.rgb(37, 84, 180))
                setOnClickListener {
                    composition.reopen(index)
                    selectedPinyin = null
                    updateCandidates()
                }
            })
        }
        groups.forEach { group ->
            val label = TextView(this).apply {
                text = group.pinyin
                textSize = 13f
                setTextColor(if (group.pinyin == selectedPinyin) Color.rgb(37, 84, 180) else Color.DKGRAY)
                setPadding(dp(10), dp(2), dp(10), dp(2))
                setOnClickListener {
                    selectedPinyin = group.pinyin
                    updateCandidates()
                }
            }
            groupStrip.addView(label)
        }
        topCandidates.removeAllViews()
        homophones.forEach { word -> topCandidates.addView(candidateView(word, false) {
            commitCandidate(word, false)
        }) }
        normalCandidates.removeAllViews()
        normal.forEach { item ->
            normalCandidates.addView(candidateView(item.text, true) {
                commitCandidate(item.text, true)
            }.apply {
                setOnLongClickListener {
                    runCatching { store.addTerm(item.text) }
                        .onSuccess { notice(if (it) "已加入谐音改写词条" else "词条已存在"); updateCandidates() }
                        .onFailure { notice(it.message ?: "无法加入") }
                    true
                }
            })
        }
        renderEditingRow()
        updateCandidateVisibility()
    }

    private fun candidateView(text: String, ordinary: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (ordinary) Color.rgb(32, 40, 57) else Color.rgb(33, 89, 171))
            val width = resources.displayMetrics.widthPixels / 5
            layoutParams = LinearLayout.LayoutParams(width, dp(if (isLandscape()) 30 else 44))
            setOnClickListener { onClick() }
        }

    private fun updateCandidateVisibility() {
        if (!::root.isInitialized) return
        val show = !secureField && (speechResult != null || (chinese && composition.raw.isNotEmpty()))
        val showParts = !secureField && chinese && composition.selectedParts.isNotEmpty()
        val hidden = if (chinese && fieldKind == EditorFieldKind.TEXT && !secureField) View.INVISIBLE else View.GONE
        groupStrip.parent?.let { (it as View).visibility = if ((show || showParts) && speechResult == null) View.VISIBLE else hidden }
        topCandidates.parent?.let { (it as View).visibility = if (show) View.VISIBLE else hidden }
        normalCandidates.parent?.let { (it as View).visibility = if (show) View.VISIBLE else hidden }
    }

    private fun commitCandidate(text: String, ordinary: Boolean) {
        if (ordinary && speechResult == null && composition.raw.isNotEmpty() && selectedPinyin != null) {
            store.promote(selectedPinyin!!, text, normal)
        }
        if (speechResult != null) {
            commitText(text)
            resetComposition()
        } else {
            val finished = composition.choose(text)
            selectedPinyin = null
            if (finished == null) updateCandidates()
            else {
                commitText(finished)
                resetComposition()
            }
        }
    }

    private fun commitDefault(): Boolean {
        if (composition.raw.isEmpty() && composition.selectedParts.isNotEmpty()) {
            commitText(composition.takeSelectedOnly())
            resetComposition()
            return true
        }
        if (composition.pendingSuffix.isNotEmpty()) {
            notice("请先选择当前局部候选，或点“长词”返回全句")
            return false
        }
        if (candidateReadyGeneration != generation) {
            notice("候选生成中，请稍候")
            return false
        }
        val word = homophones.firstOrNull() ?: normal.firstOrNull()?.text
        if (homophones.isEmpty() && sensitiveFirst) {
            notice("没有可用谐音，请手动选择正常候选")
            return false
        }
        if (word != null) commitCandidate(word, false)
        else if (composition.raw.isNotEmpty()) {
            commitText(composition.selectedText + composition.raw)
            resetComposition()
        }
        return true
    }

    private fun resetComposition() {
        composition.clear()
        speechResult = null
        selectedPinyin = null
        voicePreview = ""
        updateCandidates()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (selectionAnchor != null) {
            selectionAnchor = null
            selectionExtent = null
            renderEditingRow()
        }
    }

    private fun voiceGesture(view: TextView, shortPress: () -> Unit) {
        var triggered = false
        var cancelledGesture = false
        var longPressAttempted = false
        var initialY = 0f
        val trigger = Runnable {
            longPressAttempted = true
            triggered = beginVoice()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggered = false
                    cancelledGesture = false
                    longPressAttempted = false
                    initialY = event.rawY
                    main.postDelayed(trigger, 450)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (triggered && initialY - event.rawY > dp(72)) {
                        speech?.cancel()
                        speech = null
                        voicePreview = ""
                        composing.text = "录音已取消"
                        triggered = false
                        cancelledGesture = true
                        main.removeCallbacks(trigger)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(trigger)
                    if (triggered) {
                        speech?.finish()
                        composing.text = "正在识别…"
                    } else if (!cancelledGesture && !longPressAttempted && speech == null) shortPress()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(trigger)
                    speech?.cancel()
                    speech = null
                    triggered = false
                    true
                }
                else -> false
            }
        }
    }

    private fun beginVoice(): Boolean {
        if (secureField) {
            notice("密码输入框不支持语音")
            return false
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notice("请先在 FK 输入法设置中授予麦克风权限")
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val endpoint = preferences.speechEndpoint()
        val token = preferences.speechToken()
        if (endpoint.isNullOrBlank() || token.isNullOrBlank()) {
            notice("请先在设置中填写语音服务地址和访问凭证")
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        speech?.cancel()
        voicePreview = ""
        composing.text = "正在连接语音服务…"
        speech = StreamingAsr(object : StreamingAsr.Listener {
            override fun onPreview(text: String) {
                voicePreview = text
                composing.text = "🎙 $text"
            }
            override fun onDone(text: String) {
                speech = null
                voicePreview = ""
                if (text.isBlank()) {
                    notice("没有识别到文字")
                    updateCandidates()
                } else {
                    composition.clear()
                    speechResult = text
                    selectedPinyin = null
                    updateCandidates()
                }
            }
            override fun onError(message: String) {
                speech = null
                voicePreview = ""
                notice(message)
                updateCandidates()
            }
        }, endpoint).also { it.start(token) }
        return true
    }

    private fun scroll(content: LinearLayout, height: Int): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(-1, height)
        }

    private fun resizeStrip(content: LinearLayout, height: Int) {
        val view = content.parent as? View ?: return
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
            this.height = dp(height)
        }
    }

    private fun isLandscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun notice(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private enum class Panel { LETTERS, NUMBERS, SYMBOLS }
}
