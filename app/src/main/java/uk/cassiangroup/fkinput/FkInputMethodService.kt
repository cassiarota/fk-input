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
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import uk.cassiangroup.fkinput.core.CandidateRules
import uk.cassiangroup.fkinput.core.KeyboardLayout
import uk.cassiangroup.fkinput.core.PinyinEngine
import uk.cassiangroup.fkinput.core.PinyinGroup
import uk.cassiangroup.fkinput.core.WordCandidate
import uk.cassiangroup.fkinput.core.sensitivePositions
import uk.cassiangroup.fkinput.data.InputPreferences
import uk.cassiangroup.fkinput.data.InputStore
import uk.cassiangroup.fkinput.data.SensitiveWordFile
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
    private var panel = Panel.LETTERS
    private var rawInput = ""
    private var speechResult: String? = null
    private var selectedPinyin: String? = null
    private var groups: List<PinyinGroup> = emptyList()
    private var normal: List<WordCandidate> = emptyList()
    private var homophones: List<String> = emptyList()
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
    private lateinit var keyboard: LinearLayout

    override fun onCreate() {
        super.onCreate()
        store = InputStore(this)
        preferences = InputPreferences(this)
        layout = preferences.layout(isLandscape())
        worker.execute {
            runCatching { SensitiveWordFile(this).initialize() }
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
        keyboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyboard)
        renderKeyboard()
        updateCandidates()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        secureField = attribute?.inputType?.let(::isPasswordField) ?: false
        chinese = !secureField
        shift = false
        panel = Panel.LETTERS
        resetComposition()
        layout = preferences.layout(isLandscape())
        if (::root.isInitialized) renderKeyboard()
    }

    override fun onFinishInput() {
        super.onFinishInput()
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
        toolbar.addView(key(if (secureField || layout == KeyboardLayout.FULL_KEY) "全键" else "九键", 1f) {
            if (secureField) return@key
            if (rawInput.isNotEmpty() && !commitDefault()) return@key
            layout = if (layout == KeyboardLayout.NINE_KEY) KeyboardLayout.FULL_KEY else KeyboardLayout.NINE_KEY
            preferences.setLayout(isLandscape(), layout)
            renderKeyboard()
        })
        toolbar.addView(key(if (chinese) "中" else "英", 1f) { toggleLanguage() })
        toolbar.addView(key("123", 1f) { panel = Panel.NUMBERS; renderKeyboard() })
        toolbar.addView(key("符", 1f) { panel = Panel.SYMBOLS; renderKeyboard() })
        toolbar.addView(key("⚙", 1f) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        toolbar.addView(key("切换", 1f) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        keyboard.removeAllViews()
        when (panel) {
            Panel.NUMBERS -> renderNumbers()
            Panel.SYMBOLS -> renderSymbols()
            Panel.LETTERS -> if (layout == KeyboardLayout.NINE_KEY && chinese && !secureField) {
                renderNineKey()
            } else renderFullKey()
        }
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
        addRow(
            listOf("123", "符", if (chinese) "，" else ",",
                if (secureField) "空格" else "空格 · 语音", if (chinese) "。" else ".", "↵", "中/英"),
            listOf(1f, 1f, 1f, 4f, 1f, 1.2f, 1.2f)
        )
    }

    private fun renderNumbers() {
        addRow(listOf("1", "2", "3", "⌫"), listOf(1f, 1f, 1f, 1.1f))
        addRow(listOf("4", "5", "6", "-"), List(4) { 1f })
        addRow(listOf("7", "8", "9", "/"), List(4) { 1f })
        addRow(listOf("返回", "0", ".", "空格", "↵"), listOf(1.5f, 1f, 1f, 2f, 1f))
    }

    private fun renderSymbols() {
        addRow(listOf("，", "。", "？", "！", "；", "："), List(6) { 1f })
        addRow(listOf("（", "）", "“", "”", "、", "…"), List(6) { 1f })
        addRow(listOf("@", "#", "&", "%", "+", "="), List(6) { 1f })
        addRow(listOf("返回", "⌫", "空格", "↵"), listOf(1.5f, 1f, 3f, 1f))
    }

    private fun addRow(labels: List<String>, weights: List<Float>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { index, label ->
            val button = key(label, weights[index]) { handleKey(label) }
            if (label == "0 · 语音" || label == "空格 · 语音") {
                voiceGesture(button) { handleKey(if (label.startsWith("0")) "0" else "空格") }
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
                if ((rawInput.isNotEmpty() || speechResult != null) && !secureField) commitDefault()
                else commitText(" ")
            }
            "↵" -> {
                if ((rawInput.isNotEmpty() || speechResult != null) && !commitDefault()) return
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
                    rawInput += value
                    updateCandidates()
                } else {
                    if ((rawInput.isNotEmpty() || speechResult != null) && !commitDefault()) return
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
        if ((rawInput.isNotEmpty() || speechResult != null) && !commitDefault()) return
        chinese = !chinese && !secureField
        shift = false
        panel = Panel.LETTERS
        renderKeyboard()
        updateCandidates()
    }

    private fun backspace() {
        if (rawInput.isNotEmpty()) {
            rawInput = rawInput.dropLast(1)
            updateCandidates()
        } else if (speechResult != null) {
            speechResult = null
            updateCandidates()
        } else currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
    }

    private fun updateCandidates() {
        if (!::root.isInitialized) return
        generation++
        val token = generation
        composing.text = when {
            voicePreview.isNotEmpty() -> "🎙 $voicePreview"
            speechResult != null -> speechResult
            else -> rawInput
        }
        updateCandidateVisibility()
        if (secureField || (!chinese && speechResult == null) ||
            (rawInput.isEmpty() && speechResult == null)) {
            groups = emptyList()
            normal = emptyList()
            homophones = emptyList()
            sensitiveFirst = false
            candidateReadyGeneration = token
            renderCandidates()
            return
        }
        groups = emptyList()
        normal = emptyList()
        homophones = emptyList()
        renderCandidates()
        val activeEngine = engine
        val activeRules = rules
        if (activeEngine == null || activeRules == null) {
            composing.text = "词典加载中…"
            return
        }
        val input = rawInput
        val spoken = speechResult
        val chosen = selectedPinyin
        val currentLayout = layout
        worker.execute {
            val terms = store.terms()
            val foundGroups = if (spoken != null) listOf(
                PinyinGroup("", listOf(WordCandidate(spoken, "", 0)), 0.0)
            ) else activeEngine.groups(input, currentLayout)
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
                sensitiveFirst = firstIsSensitive
                candidateReadyGeneration = token
                renderCandidates()
            }
        }
    }

    private fun renderCandidates() {
        if (!::root.isInitialized) return
        groupStrip.removeAllViews()
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
                        .onSuccess { notice(if (it) "已加入敏感词列表" else "词条已存在"); updateCandidates() }
                        .onFailure { notice(it.message ?: "无法加入") }
                    true
                }
            })
        }
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
        val show = !secureField && (speechResult != null || (chinese && rawInput.isNotEmpty()))
        groupStrip.parent?.let { (it as View).visibility = if (show && speechResult == null) View.VISIBLE else View.GONE }
        topCandidates.parent?.let { (it as View).visibility = if (show) View.VISIBLE else View.GONE }
        normalCandidates.parent?.let { (it as View).visibility = if (show) View.VISIBLE else View.GONE }
    }

    private fun commitCandidate(text: String, ordinary: Boolean) {
        if (ordinary && speechResult == null && rawInput.isNotEmpty() && selectedPinyin != null) {
            store.promote(selectedPinyin!!, text, normal)
        }
        commitText(text)
        resetComposition()
    }

    private fun commitDefault(): Boolean {
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
        else if (rawInput.isNotEmpty()) {
            commitText(rawInput)
            resetComposition()
        }
        return true
    }

    private fun resetComposition() {
        rawInput = ""
        speechResult = null
        selectedPinyin = null
        voicePreview = ""
        updateCandidates()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
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
                    rawInput = ""
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

    private fun isPasswordField(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun notice(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private enum class Panel { LETTERS, NUMBERS, SYMBOLS }
}
