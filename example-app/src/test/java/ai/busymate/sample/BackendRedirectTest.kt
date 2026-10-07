package ai.busymate.sample
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
class BackendRedirectTest {
    @Test fun authenticatedPostDoesNotForwardToRedirectTarget() {
        val forwarded = AtomicInteger()
        val target = ServerSocket(0)
        val origin = ServerSocket(0)
        val targetThread = thread(isDaemon = true) {
            runCatching { target.accept().use { socket ->
                forwarded.incrementAndGet()
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } }
        }
        val originThread = thread(isDaemon = true) {
            origin.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                var length = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                }
                repeat(length) { reader.read() }
                socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${target.localPort}/target\r\n" +
                    "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
            }
        }
        try {
            // Loopback-only transport fixture; production settings require HTTPS.
            val config = DemoSettings(backendUrl = "http://127.0.0.1:${origin.localPort}/mint", backendBearer = "test-only-fixture")
            val connection = BackendMint.connection(config)
            try {
                connection.outputStream.use { it.write("{}".toByteArray()) }
                assertEquals(302, connection.responseCode)
                assertEquals(0, forwarded.get())
            } finally { connection.disconnect() }
        } finally {
            origin.close(); target.close()
            originThread.join(1000); targetThread.join(1000)
        }
    }
}
