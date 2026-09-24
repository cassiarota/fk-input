package uk.cassiangroup.fkinput.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StreamingAsrTest {
    @Test fun releaseBeforeReadyStillSendsEndAndUsesFinalRevision() {
        val previews = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var result = ""
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    when {
                        text.contains("\"start\"") -> webSocket.send("""{"type":"ready"}""")
                        text.contains("\"end\"") -> {
                            webSocket.send("""{"type":"partial","segment_id":0,"text":"和"}""")
                            webSocket.send("""{"type":"partial","segment_id":0,"text":"和平"}""")
                            webSocket.send("""{"type":"final","segment_id":0,"text":"和平"}""")
                            webSocket.send("""{"type":"done"}""")
                            webSocket.close(1000, "done")
                        }
                    }
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.countDown()
                }
            }))
            server.start()
            val asr = StreamingAsr(object : StreamingAsr.Listener {
                override fun onPreview(text: String) { previews.add(text) }
                override fun onDone(text: String) { result = text; done.countDown() }
                override fun onError(message: String) { result = message; done.countDown() }
            }, server.url("/stream").toString().replaceFirst("http", "ws"))
            asr.start("test-key")
            asr.finish()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("和平", result)
            assertTrue(previews.contains("和"))
            assertTrue(previews.contains("和平"))
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun busyErrorIsReportedWithoutCompletingTranscript() {
        val done = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var error = ""
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.send("""{"type":"error","code":"busy","message":"Too many active sessions"}""")
                    webSocket.close(1013, "busy")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.countDown()
                }
            }))
            server.start()
            val asr = StreamingAsr(object : StreamingAsr.Listener {
                override fun onPreview(text: String) = Unit
                override fun onDone(text: String) { done.countDown() }
                override fun onError(message: String) { error = message; done.countDown() }
            }, server.url("/stream").toString().replaceFirst("http", "ws"))
            asr.start("test-key")
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals("语音服务繁忙，请稍后重试", error)
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun cancelSuppressesLateServerMessages() {
        val done = CountDownLatch(1)
        val opened = CountDownLatch(1)
        val closed = CountDownLatch(1)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.countDown()
                    Thread {
                        Thread.sleep(200)
                        webSocket.send("""{"type":"final","segment_id":0,"text":"取消后结果"}""")
                        webSocket.send("""{"type":"done"}""")
                        webSocket.close(1000, "done")
                    }.start()
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.countDown()
                }
            }))
            server.start()
            val asr = StreamingAsr(object : StreamingAsr.Listener {
                override fun onPreview(text: String) { done.countDown() }
                override fun onDone(text: String) { done.countDown() }
                override fun onError(message: String) { done.countDown() }
            }, server.url("/stream").toString().replaceFirst("http", "ws"))
            asr.start("test-key")
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            asr.cancel()
            assertFalse(done.await(600, TimeUnit.MILLISECONDS))
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
    }
}
