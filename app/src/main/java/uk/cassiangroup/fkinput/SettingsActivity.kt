package uk.cassiangroup.fkinput

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import uk.cassiangroup.fkinput.data.InputPreferences
import uk.cassiangroup.fkinput.data.InputStore
import uk.cassiangroup.fkinput.data.SensitiveWordFile
import java.io.ByteArrayOutputStream
import java.io.InputStream

class SettingsActivity : Activity() {
    private lateinit var store: InputStore
    private lateinit var preferences: InputPreferences
    private lateinit var termsContainer: LinearLayout
    private lateinit var endpointState: TextView
    private lateinit var tokenState: TextView
    private var search = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = InputStore(this)
        preferences = InputPreferences(this)
        runCatching { SensitiveWordFile(this).initialize() }
            .onFailure { toast("本地敏感词库初始化失败：${it.message}") }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(title("FK 输入法", 28))
        root.addView(info("本地拼音与词库。只有你主动按住语音键时，音频才发送到 ASR 服务。"))
        root.addView(section("启用"))
        root.addView(action("1. 在系统设置启用输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(action("2. 选择 FK 输入法") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(action("授予麦克风权限") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            } else toast("麦克风权限已授予")
        })

        root.addView(section("语音服务"))
        root.addView(info("语音服务地址与访问凭证由你自行填写，仅在本机加密保存，不随应用发布。服务须支持 WSS、16 kHz 单声道 PCM16 流式协议。"))
        endpointState = info(if (preferences.speechEndpoint() == null) "尚未设置语音服务地址" else "语音服务地址已保存")
        root.addView(endpointState)
        val endpointInput = EditText(this).apply {
            hint = "WSS 服务地址"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(endpointInput)
        root.addView(action("保存语音服务地址") {
            runCatching { preferences.setSpeechEndpoint(endpointInput.text.toString()) }
                .onSuccess {
                    endpointInput.setText("")
                    endpointState.text = if (preferences.speechEndpoint() == null) "尚未设置语音服务地址" else "语音服务地址已保存"
                    toast("已保存")
                }
                .onFailure { toast("保存失败：${it.message}") }
        })
        root.addView(action("清除语音服务地址") {
            preferences.setSpeechEndpoint("")
            endpointState.text = "尚未设置语音服务地址"
            toast("已清除")
        })

        tokenState = info(if (preferences.speechToken() == null) "尚未设置访问凭证" else "访问凭证已保存")
        root.addView(tokenState)
        val tokenInput = EditText(this).apply {
            hint = "输入访问凭证"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        root.addView(tokenInput)
        root.addView(action("保存访问凭证") {
            runCatching { preferences.setSpeechToken(tokenInput.text.toString()) }
                .onSuccess {
                    tokenInput.setText("")
                    tokenState.text = if (preferences.speechToken() == null) "尚未设置访问凭证" else "访问凭证已保存"
                    toast("已保存")
                }
                .onFailure { toast("保存失败：${it.message}") }
        })
        root.addView(action("清除访问凭证") {
            preferences.setSpeechToken("")
            tokenState.text = "尚未设置访问凭证"
            toast("已清除")
        })

        root.addView(section("本地敏感词库文件"))
        root.addView(info("在支持系统文本选择菜单的聊天应用中，长按消息选中文本，再点“加入敏感词库”保存到应用私有文件。此文件暂不参与候选生成或消息过滤。"))
        root.addView(section("谐音改写词条"))
        root.addView(info("长按第二行正常候选可把整词加入谐音改写词条；此处词条对所有应用的候选生效。"))
        val newTerm = EditText(this).apply {
            hint = "输入词或短语"
            setSingleLine(true)
        }
        root.addView(newTerm)
        root.addView(action("添加词条") {
            runCatching { store.addTerm(newTerm.text.toString()) }
                .onSuccess {
                    newTerm.setText("")
                    refreshTerms()
                    toast(if (it) "已加入" else "词条已存在")
                }
                .onFailure { toast(it.message ?: "添加失败") }
        })
        val searchInput = EditText(this).apply {
            hint = "搜索词条"
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    search = s?.toString().orEmpty()
                    refreshTerms()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchInput)
        termsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(termsContainer)
        refreshTerms()
        root.addView(action("导入 UTF-8 词库") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "text/plain"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, REQUEST_IMPORT
            )
        })
        root.addView(action("导出 UTF-8 词库") {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/plain"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, "fk-sensitive-terms.txt")
                }, REQUEST_EXPORT
            )
        })

        root.addView(section("输入习惯"))
        root.addView(info("选择第二行候选时，只向前移动一位。九键与全键共享同拼音组合的顺序。"))
        root.addView(action("重置候选排序") {
            AlertDialog.Builder(this)
                .setMessage("清空所有已学习的候选顺序？词库不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置") { _, _ ->
                    store.resetLearning()
                    toast("候选排序已重置")
                }
                .show()
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data ?: return
        runCatching {
            when (requestCode) {
                REQUEST_IMPORT -> {
                    val bytes = contentResolver.openInputStream(uri)?.use(::readBounded)
                        ?: error("无法读取文件")
                    require(bytes.size <= 1_000_000) { "文件过大" }
                    val count = store.importTerms(bytes.toString(Charsets.UTF_8))
                    refreshTerms()
                    toast("已导入 $count 个词条")
                }
                REQUEST_EXPORT -> {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(store.exportTerms().toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                    toast("词库已导出")
                }
            }
        }.onFailure { toast(it.message ?: "文件操作失败") }
    }

    private fun refreshTerms() {
        termsContainer.removeAllViews()
        store.terms(search).forEach { term ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(info(term), LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(action("删除") {
                store.removeTerm(term)
                refreshTerms()
            })
            termsContainer.addView(row)
        }
    }

    private fun title(text: String, size: Int) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(Color.rgb(25, 35, 55))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun section(text: String) = title(text, 19).apply {
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun info(text: String) = title(text, 14).apply {
        setTextColor(Color.rgb(75, 85, 105))
    }

    private fun action(label: String, onClick: (View) -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener(onClick)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 1_000_000) { "文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val REQUEST_IMPORT = 21
        private const val REQUEST_EXPORT = 22
    }
}
