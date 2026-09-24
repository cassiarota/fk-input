package uk.cassiangroup.fkinput.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One push-to-talk WebSocket session. The listener is always invoked on the main thread. */
class StreamingAsr(
    private val listener: Listener,
    private val endpoint: String,
) {
    interface Listener {
        fun onPreview(text: String)
        fun onDone(text: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val recording = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val finishRequested = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val doneReceived = AtomicBoolean(false)
    private val transcript = TranscriptAssembler()
    private var socket: WebSocket? = null
    private var audio: AudioRecord? = null
    private var worker: Thread? = null
    private var endSent = false

    fun start(token: String) {
        check(socket == null) { "ASR session already started" }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "FKInput/0.1")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    """{"type":"start","sample_rate":16000,"encoding":"pcm_s16le","channels":1}"""
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (event.optString("type")) {
                    "ready" -> {
                        ready.set(true)
                        if (finishRequested.get()) sendEnd() else startRecorder(webSocket)
                    }
                    "partial" -> {
                        val segment = event.optInt("segment_id", 0)
                        transcript.partial(segment, event.optString("text"))
                        preview()
                    }
                    "final" -> {
                        val segment = event.optInt("segment_id", 0)
                        transcript.final(segment, event.optString("text"))
                        preview()
                    }
                    "done" -> {
                        doneReceived.set(true)
                        recording.set(false)
                        if (!cancelled.get()) main.post {
                            if (!cancelled.get()) listener.onDone(transcript.completed())
                        }
                        webSocket.close(1000, null)
                    }
                    "error" -> fail(when (event.optString("code")) {
                        "busy" -> "语音服务繁忙，请稍后重试"
                        else -> "语音识别失败，请重试"
                    })
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (cancelled.get()) return
                val message = when (response?.code) {
                    401, 403 -> "语音服务访问凭证无效，请到设置中更新"
                    429 -> "语音请求过于频繁，请稍后重试"
                    503 -> "语音服务繁忙，请稍后重试"
                    else -> "语音连接失败，请重试"
                }
                fail(message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!doneReceived.get() && !cancelled.get()) fail("语音连接已断开，请重试")
            }
        })
    }

    /** Called when the finger is released; the record loop sends end after its last PCM frame. */
    fun finish() {
        finishRequested.set(true)
        recording.set(false)
        if (ready.get() && worker == null) sendEnd()
    }

    fun cancel() {
        cancelled.set(true)
        recording.set(false)
        runCatching { audio?.stop() }
        socket?.close(1000, "cancel")
    }

    private fun preview() {
        if (cancelled.get()) return
        val text = transcript.preview()
        main.post { if (!cancelled.get()) listener.onPreview(text) }
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder(webSocket: WebSocket) {
        if (cancelled.get()) return
        if (finishRequested.get()) {
            sendEnd()
            return
        }
        val minimum = AudioRecord.getMinBufferSize(
            16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimum <= 0) {
            fail("麦克风初始化失败")
            return
        }
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16_000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, 6_400)
            )
        }.getOrElse {
            fail("麦克风不可用")
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            fail("麦克风不可用")
            return
        }
        audio = recorder
        recording.set(!finishRequested.get())
        worker = Thread({
            try {
                recorder.startRecording()
                val samples = ShortArray(1_600)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                while (recording.get() && System.nanoTime() < deadline) {
                    val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) throw IllegalStateException("录音设备读取失败")
                    val bytes = ByteArray(count * 2)
                    for (index in 0 until count) {
                        bytes[index * 2] = samples[index].toByte()
                        bytes[index * 2 + 1] = (samples[index].toInt() shr 8).toByte()
                    }
                    if (!webSocket.send(bytes.toByteString())) {
                        throw IllegalStateException("语音上传失败")
                    }
                }
                if (!cancelled.get()) sendEnd()
            } catch (_: Exception) {
                if (!cancelled.get()) fail("录音中断，请重试")
            } finally {
                recording.set(false)
                runCatching { recorder.stop() }
                recorder.release()
                audio = null
            }
        }, "fk-asr-capture").apply { start() }
    }

    @Synchronized
    private fun sendEnd() {
        if (!endSent && !cancelled.get()) {
            endSent = true
            socket?.send("""{"type":"end"}""")
        }
    }

    private fun fail(message: String) {
        if (cancelled.get()) return
        cancelled.set(true)
        recording.set(false)
        runCatching { audio?.stop() }
        socket?.close(1000, "error")
        main.post { listener.onError(message) }
    }

}
