package kz.mybrain.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class OfdTcpNetworkClientTest {

    @Test
    fun testSuccessfulSendAndReceive() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = 20) { response ->
                    response[0] = 0x12
                    response[18] = 0xAA.toByte()
                    response[19] = 0xBB.toByte()
                })
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isSuccess)
                val responseBytes = result.getOrThrow()
                assertEquals(20, responseBytes.size)
                assertEquals(0xAA.toByte(), responseBytes[18])
                assertEquals(0xBB.toByte(), responseBytes[19])
            }
        )
    }

    @Test
    fun testHeaderOnlyResponse() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = HEADER_SIZE))
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isSuccess)
                assertEquals(HEADER_SIZE, result.getOrThrow().size)
            }
        )
    }

    @Test
    fun testMultiByteLittleEndianSize() = runBlocking {
        val totalSize = 300
        val lastPayloadByte = 0x7A.toByte()

        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = totalSize) { response ->
                    response[totalSize - 1] = lastPayloadByte
                })
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(
                    headerSize = HEADER_SIZE,
                    timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS,
                    maxResponseBytes = totalSize
                )
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isSuccess)
                val responseBytes = result.getOrThrow()
                assertEquals(totalSize, responseBytes.size)
                assertEquals(lastPayloadByte, responseBytes[totalSize - 1])
            }
        )
    }

    @Test
    fun testRequestTooShort() = runBlocking {
        val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
        val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, 9999), ByteArray(5))

        assertTrue(result.isFailure)
        val exception = assertFailure<OfdRequestTooShort>(result)
        assertEquals(OfdProtocolViolationReason.REQUEST_TOO_SHORT, exception.reason)
        assertEquals(OfdFailureSide.CLIENT, exception.side)
    }

    @Test
    fun testTimeout() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                delay(500.milliseconds)
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = 100)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdTimeoutNoResponse>(result)
                assertEquals(OfdTimeoutReason.NO_RESPONSE, exception.reason)
                assertEquals(OfdFailureSide.NETWORK, exception.side)
            }
        )
    }

    @Test
    fun testExternalCancellationIsPropagated() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                delay(DEFAULT_TEST_TIMEOUT_MILLIS.milliseconds)
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val requestJob = async(Dispatchers.Default) {
                    client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())
                }

                delay(100.milliseconds)
                requestJob.cancel()

                assertFailsWith<CancellationException> {
                    requestJob.await()
                }
            }
        )
    }

    @Test
    fun testExternalCancellationDuringPayloadReadIsPropagated() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = HEADER_SIZE + 1).copyOf(HEADER_SIZE))
                delay(DEFAULT_TEST_TIMEOUT_MILLIS.milliseconds)
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val requestJob = async(Dispatchers.Default) {
                    client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())
                }

                delay(100.milliseconds)
                requestJob.cancel()

                assertFailsWith<CancellationException> {
                    requestJob.await()
                }
            }
        )
    }

    @Test
    fun testConnectionFailure() = runBlocking {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(LOCALHOST, 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port
        serverSocket.close()
        selectorManager.close()

        val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
        val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

        assertTrue(result.isFailure)
        val exception = assertFailure<OfdTransportFailure>(result)
        assertEquals(OfdTransportFailureReason.IO_FAILURE, exception.reason)
        assertEquals(OfdFailureSide.NETWORK, exception.side)
    }

    @Test
    fun testUnexpectedEndpointFailure() = runBlocking {
        val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
        val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, -1), makeRequest())

        assertTrue(result.isFailure)
        val exception = assertFailure<OfdTransportFailure>(result)
        assertEquals(OfdTransportFailureReason.UNEXPECTED_FAILURE, exception.reason)
        assertEquals(OfdFailureSide.UNKNOWN, exception.side)
    }

    @Test
    fun testProtocolViolationHeaderClosed() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdResponseHeaderIncomplete>(result)
                assertEquals(OfdProtocolViolationReason.RESPONSE_HEADER_INCOMPLETE, exception.reason)
                assertEquals(OfdFailureSide.SERVER, exception.side)
            }
        )
    }

    @Test
    fun testProtocolViolationPartialHeaderClosed() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                writeResponseAndCloseOutput(clientSocket, ByteArray(5))
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdResponseHeaderIncomplete>(result)
                assertEquals(OfdProtocolViolationReason.RESPONSE_HEADER_INCOMPLETE, exception.reason)
                assertEquals(OfdFailureSide.SERVER, exception.side)
            }
        )
    }

    @Test
    fun testProtocolViolationPayloadClosed() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                val response = makeMessage(totalSize = HEADER_SIZE + 2).copyOf(HEADER_SIZE + 1)
                writeResponseAndCloseOutput(clientSocket, response)
                clientSocket.close()
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdResponsePayloadIncomplete>(result)
                assertEquals(OfdProtocolViolationReason.RESPONSE_PAYLOAD_INCOMPLETE, exception.reason)
                assertEquals(OfdFailureSide.SERVER, exception.side)
            }
        )
    }

    @Test
    fun testProtocolViolationSizeTooSmall() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = 5))
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdResponseSizeTooSmall>(result)
                assertEquals(OfdProtocolViolationReason.RESPONSE_SIZE_TOO_SMALL, exception.reason)
                assertEquals(OfdFailureSide.SERVER, exception.side)
            }
        )
    }

    @Test
    fun testProtocolViolationResponseTooLarge() = runBlocking {
        val maxResponseBytes = HEADER_SIZE

        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = maxResponseBytes + 1))
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(
                    headerSize = HEADER_SIZE,
                    timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS,
                    maxResponseBytes = maxResponseBytes
                )
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isFailure)
                val exception = assertFailure<OfdResponseSizeTooLarge>(result)
                assertEquals(OfdProtocolViolationReason.RESPONSE_SIZE_TOO_LARGE, exception.reason)
                assertEquals(OfdFailureSide.SERVER, exception.side)
            }
        )
    }

    @Test
    fun testMaxResponseBytesBoundaryIsAccepted() = runBlocking {
        val maxResponseBytes = HEADER_SIZE + 1

        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = maxResponseBytes))
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(
                    headerSize = HEADER_SIZE,
                    timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS,
                    maxResponseBytes = maxResponseBytes
                )
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isSuccess)
                assertEquals(maxResponseBytes, result.getOrThrow().size)
            }
        )
    }

    @Test
    fun testDefaultMaxResponseBytesIsFiveMebibytes() = runBlocking {
        withTcpServer(
            server = { clientSocket ->
                readRequest(clientSocket)
                writeResponse(clientSocket, makeMessage(totalSize = DEFAULT_MAX_RESPONSE_BYTES))
            },
            client = { localPort ->
                val client = OfdTcpNetworkClient(headerSize = HEADER_SIZE, timeoutMillis = DEFAULT_TEST_TIMEOUT_MILLIS)
                val result = client.sendAndReceive(OfdEndpoint(LOCALHOST, localPort), makeRequest())

                assertTrue(result.isSuccess)
                assertEquals(DEFAULT_MAX_RESPONSE_BYTES, result.getOrThrow().size)
            }
        )
    }

    @Test
    fun testInvalidHeaderSizeRejected() {
        assertFailsWith<IllegalArgumentException> {
            OfdTcpNetworkClient(headerSize = 7)
        }
    }

    @Test
    fun testInvalidTimeoutRejected() {
        assertFailsWith<IllegalArgumentException> {
            OfdTcpNetworkClient(timeoutMillis = 0)
        }
    }

    @Test
    fun testInvalidMaxResponseBytesRejected() {
        assertFailsWith<IllegalArgumentException> {
            OfdTcpNetworkClient(headerSize = HEADER_SIZE, maxResponseBytes = HEADER_SIZE - 1)
        }
    }

    @Test
    fun testDefaultExceptionClassification() {
        val timeout = OfdTimeoutNoResponse("timeout")
        assertEquals(OfdTimeoutReason.NO_RESPONSE, timeout.reason)
        assertEquals(OfdFailureSide.NETWORK, timeout.side)

        val transport = OfdTransportFailure("transport")
        assertEquals(OfdTransportFailureReason.IO_FAILURE, transport.reason)
        assertEquals(OfdFailureSide.NETWORK, transport.side)
    }

    @Test
    fun testTransportFailureMessageFallbackWithoutCauseMessage() {
        val failure = IOException().toOfdTransportFailure()

        assertEquals(OfdTransportFailureReason.IO_FAILURE, failure.reason)
        assertEquals(OfdFailureSide.NETWORK, failure.side)
        assertTrue(failure.message?.contains("no details") == true)
    }

    @Test
    fun testUnexpectedFailureClassification() {
        val failure = IllegalStateException("boom").toUnexpectedOfdTransportFailure()

        assertEquals(OfdTransportFailureReason.UNEXPECTED_FAILURE, failure.reason)
        assertEquals(OfdFailureSide.UNKNOWN, failure.side)
        assertTrue(failure.message?.contains("boom") == true)
    }

    @Test
    fun testUnexpectedFailureMessageFallbackWithoutCauseMessage() {
        val failure = RuntimeException().toUnexpectedOfdTransportFailure()

        assertEquals(OfdTransportFailureReason.UNEXPECTED_FAILURE, failure.reason)
        assertEquals(OfdFailureSide.UNKNOWN, failure.side)
        assertTrue(failure.message?.contains("no details") == true)
    }

    private suspend fun withTcpServer(
        server: suspend (Socket) -> Unit,
        client: suspend (port: Int) -> Unit,
    ) = coroutineScope {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(LOCALHOST, 0)
        val localPort = (serverSocket.localAddress as InetSocketAddress).port

        val serverJob = async(Dispatchers.Default) {
            val clientSocket = serverSocket.accept()
            try {
                server(clientSocket)
            } finally {
                clientSocket.close()
            }
        }

        try {
            client(localPort)
            serverJob.await()
        } finally {
            serverJob.cancelAndJoin()
            serverSocket.close()
            selectorManager.close()
        }
    }

    private suspend fun readRequest(clientSocket: Socket): ByteArray {
        val readChannel = clientSocket.openReadChannel()
        val header = ByteArray(HEADER_SIZE)
        readChannel.readFully(header, 0, HEADER_SIZE)
        return header
    }

    private suspend fun writeResponse(clientSocket: Socket, response: ByteArray) {
        val writeChannel = clientSocket.openWriteChannel(autoFlush = true)
        writeChannel.writeFully(response)
    }

    private suspend fun writeResponseAndCloseOutput(clientSocket: Socket, response: ByteArray) {
        val writeChannel = clientSocket.openWriteChannel(autoFlush = true)
        writeChannel.writeFully(response)
        @Suppress("DEPRECATION")
        writeChannel.close()
    }

    private fun makeRequest(): ByteArray = makeMessage(totalSize = HEADER_SIZE) { request ->
        request[0] = 0x12
    }

    private fun makeMessage(totalSize: Int, customize: (ByteArray) -> Unit = {}): ByteArray {
        val message = ByteArray(totalSize.coerceAtLeast(HEADER_SIZE))
        writeUInt32Le(message, totalSize)
        customize(message)
        return message
    }

    private fun writeUInt32Le(buffer: ByteArray, value: Int) {
        buffer[SIZE_OFFSET] = (value and BYTE_MASK).toByte()
        buffer[SIZE_OFFSET + 1] = ((value shr 8) and BYTE_MASK).toByte()
        buffer[SIZE_OFFSET + 2] = ((value shr 16) and BYTE_MASK).toByte()
        buffer[SIZE_OFFSET + 3] = ((value shr 24) and BYTE_MASK).toByte()
    }

    private inline fun <reified T : Throwable> assertFailure(result: Result<ByteArray>): T {
        val exception = result.exceptionOrNull()
        assertTrue(exception is T)
        return exception
    }

    private companion object {
        private const val LOCALHOST = "127.0.0.1"
        private const val HEADER_SIZE = 18
        private const val SIZE_OFFSET = 4
        private const val BYTE_MASK = 0xff
        private const val DEFAULT_TEST_TIMEOUT_MILLIS = 2_000
        private const val DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    }
}
