package kz.mybrain.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfdTcpNetworkClientTest {

    @Test
    fun testSuccessfulSendAndReceive() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            val readChannel = clientSocket.openReadChannel()
            val writeChannel = clientSocket.openWriteChannel(autoFlush = true)

            val header = ByteArray(18)
            readChannel.readFully(header, 0, 18)

            val response = ByteArray(20)
            response[0] = 0x12
            response[4] = 20
            response[18] = 0xAA.toByte()
            response[19] = 0xBB.toByte()

            writeChannel.writeFully(response)
            clientSocket.close()
        }

        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 2000)
        val request = ByteArray(18)
        request[0] = 0x12
        request[4] = 18

        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", localPort), request)
        }
        assertTrue(result.isSuccess)
        val responseBytes = result.getOrThrow()
        assertEquals(20, responseBytes.size)
        assertEquals(0xAA.toByte(), responseBytes[18])
        assertEquals(0xBB.toByte(), responseBytes[19])

        serverJob.await()
        serverSocket.close()
        selectorManager.close()
    }

    @Test
    fun testRequestTooShort() = runBlocking {
        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 1000)
        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", 9999), ByteArray(5))
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OfdProtocolViolation)
    }

    @Test
    fun testTimeout() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            delay(2000) // Delay longer than client timeout
            clientSocket.close()
        }

        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 200)
        val request = ByteArray(18)
        request[4] = 18
        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", localPort), request)
        }
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is OfdTimeoutNoResponse)

        serverJob.await()
        serverSocket.close()
        selectorManager.close()
    }

    @Test
    fun testProtocolViolationHeaderClosed() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            clientSocket.close()
        }

        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 1000)
        val request = ByteArray(18)
        request[4] = 18
        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", localPort), request)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OfdProtocolViolation)

        serverJob.await()
        serverSocket.close()
        selectorManager.close()
    }

    @Test
    fun testProtocolViolationPayloadClosed() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            val readChannel = clientSocket.openReadChannel()
            val writeChannel = clientSocket.openWriteChannel(autoFlush = true)

            val header = ByteArray(18)
            readChannel.readFully(header, 0, 18)

            val response = ByteArray(18)
            response[4] = 30
            writeChannel.writeFully(response)
            @Suppress("DEPRECATION")
            writeChannel.close()
            clientSocket.close()
        }

        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 1000)
        val request = ByteArray(18)
        request[4] = 18
        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", localPort), request)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OfdProtocolViolation)

        serverJob.await()
        serverSocket.close()
        selectorManager.close()
    }

    @Test
    fun testProtocolViolationSizeTooSmall() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind("127.0.0.1", 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            val readChannel = clientSocket.openReadChannel()
            val writeChannel = clientSocket.openWriteChannel(autoFlush = true)

            val header = ByteArray(18)
            readChannel.readFully(header, 0, 18)

            val response = ByteArray(18)
            response[4] = 5
            writeChannel.writeFully(response)
            clientSocket.close()
        }

        val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 1000)
        val request = ByteArray(18)
        request[4] = 18
        val result = withContext(Dispatchers.Default) {
            client.sendAndReceive(OfdEndpoint("127.0.0.1", localPort), request)
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OfdProtocolViolation)

        serverJob.await()
        serverSocket.close()
        selectorManager.close()
    }
}
