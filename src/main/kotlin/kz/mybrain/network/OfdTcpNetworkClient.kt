package kz.mybrain.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * TCP-реализация, которая читает один полный ответ и закрывает сокет.
 *
 * Ожидается, что общий размер сообщения записан в заголовке по смещению 4
 * как little-endian uint32. Размер заголовка по умолчанию — 18 байт.
 */
class OfdTcpNetworkClient(
    private val headerSize: Int = 18,
    private val timeoutMillis: Int = 7_000,
) : OfdNetworkClient {

    /**
     * Подключается к endpoint, отправляет [request], читает полный ответ и закрывает сокет.
     *
     * - Сокет всегда закрывается в finally.
     * - Таймаут применяется к подключению и чтению.
     * - При ошибке возвращается типизированный [OfdNetworkClientException].
     */
    override suspend fun sendAndReceive(endpoint: OfdEndpoint, request: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                if (request.size < headerSize) {
                    return@withContext Result.failure(
                        OfdProtocolViolation(
                            bilingualMessage(
                                "Размер запроса ${request.size} меньше размера заголовка $headerSize",
                                "Request size ${request.size} is smaller than header size $headerSize"
                            )
                        )
                    )
                }

                val socket = Socket()
                try {
                    socket.soTimeout = timeoutMillis
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMillis)

                    val output = socket.getOutputStream()
                    output.write(request)
                    output.flush()

                    val input = socket.getInputStream()
                    val response = readFullMessage(input)
                    Result.success(response)
                } finally {
                    try {
                        socket.close()
                    } catch (_: IOException) {
                    }
                }
            } catch (e: SocketTimeoutException) {
                Result.failure(
                    OfdTimeoutNoResponse(
                        bilingualMessage(
                            "Нет ответа от сервера за ${timeoutMillis}мс",
                            "No response from server in ${timeoutMillis}ms"
                        ),
                        e
                    )
                )
            } catch (e: OfdProtocolViolation) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(
                    OfdTransportFailure(
                        bilingualMessage(
                            "Транспортная ошибка: ${e::class.simpleName}: ${e.message ?: "нет подробностей"}",
                            "Transport failure: ${e::class.simpleName}: ${e.message ?: "no details"}"
                        ),
                        e
                    )
                )
            }
        }
    }

    /**
     * Читает полный ответ по размеру, указанному в заголовке.
     */
    private fun readFullMessage(input: InputStream): ByteArray {
        val headerBytes = readExactBytes(input, headerSize, "заголовок", "header")
        val totalSize = readUInt32Le(headerBytes, 4)
        if (totalSize < headerSize.toLong()) {
            throw OfdProtocolViolation(
                bilingualMessage(
                    "Размер сообщения $totalSize меньше размера заголовка $headerSize",
                    "Message size $totalSize is smaller than header size $headerSize"
                )
            )
        }
        if (totalSize > Int.MAX_VALUE) {
            throw OfdProtocolViolation(
                bilingualMessage(
                    "Размер сообщения $totalSize превышает Int.MAX_VALUE",
                    "Message size $totalSize exceeds Int.MAX_VALUE"
                )
            )
        }

        val payloadSize = totalSize.toInt() - headerSize
        val payloadBytes = readExactBytes(input, payloadSize, "тело", "payload")

        val fullMessage = ByteArray(totalSize.toInt())
        System.arraycopy(headerBytes, 0, fullMessage, 0, headerBytes.size)
        System.arraycopy(payloadBytes, 0, fullMessage, headerBytes.size, payloadBytes.size)
        return fullMessage
    }

    /**
     * Читает ровно [count] байт или бросает [OfdProtocolViolation], если EOF наступил раньше.
     */
    private fun readExactBytes(
        input: InputStream,
        count: Int,
        partNameRu: String,
        partNameEn: String
    ): ByteArray {
        val buffer = ByteArray(count)
        var readTotal = 0
        while (readTotal < count) {
            val read = input.read(buffer, readTotal, count - readTotal)
            if (read == -1) {
                throw OfdProtocolViolation(
                    bilingualMessage(
                        "Сервер закрыл соединение до полного чтения: $partNameRu",
                        "Server closed connection before full $partNameEn was received"
                    )
                )
            }
            readTotal += read
        }
        return buffer
    }

    /**
     * Читает little-endian uint32 из [buffer], начиная с [offset].
     */
    private fun readUInt32Le(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xff
        val b1 = (buffer[offset + 1].toLong() and 0xff) shl 8
        val b2 = (buffer[offset + 2].toLong() and 0xff) shl 16
        val b3 = (buffer[offset + 3].toLong() and 0xff) shl 24
        return (b0 or b1 or b2 or b3) and 0xffffffffL
    }
}
