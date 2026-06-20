package kz.mybrain.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

private const val SIZE_OFFSET = 4
private const val BYTE_MASK = 0xffL
private const val UINT32_MASK = 0xffffffffL
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
private const val OFFSET_1 = 1
private const val OFFSET_2 = 2
private const val OFFSET_3 = 3


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
     * - Тайм-аут применяется к подключению и чтению.
     * - При ошибке возвращается типизированный [OfdNetworkClientException].
     */
    override suspend fun sendAndReceive(endpoint: OfdEndpoint, request: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                if (request.size < headerSize) {
                    return@withContext Result.failure(
                        OfdProtocolViolation(
                            trilingualMessage(
                                "Размер запроса ${request.size} меньше размера заголовка $headerSize",
                                "Сұраныс өлшемі ${request.size} тақырып өлшемінен $headerSize кіші",
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
                        trilingualMessage(
                            "Нет ответа от сервера за ${timeoutMillis}мс",
                            "Серверден ${timeoutMillis}мс ішінде жауап болмады",
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
                        trilingualMessage(
                            "Транспортная ошибка: ${e::class.simpleName}: ${e.message ?: "нет подробностей"}",
                            "Көліктік қате: ${e::class.simpleName}: ${e.message ?: "мәліметтер жоқ"}",
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
        val headerBytes = readExactBytes(input, headerSize, "заголовок", "тақырып", "header")
        val totalSize = readUInt32Le(headerBytes, SIZE_OFFSET)
        if (totalSize < headerSize.toLong()) {
            throw OfdProtocolViolation(
                trilingualMessage(
                    "Размер сообщения $totalSize меньше размера заголовка $headerSize",
                    "Хабарлама өлшемі $totalSize тақырып өлшемінен $headerSize кіші",
                    "Message size $totalSize is smaller than header size $headerSize"
                )
            )
        }
        if (totalSize > Int.MAX_VALUE) {
            throw OfdProtocolViolation(
                trilingualMessage(
                    "Размер сообщения $totalSize превышает Int.MAX_VALUE",
                    "Хабарлама өлшемі $totalSize Int.MAX_VALUE мәнінен асады",
                    "Message size $totalSize exceeds Int.MAX_VALUE"
                )
            )
        }

        val payloadSize = totalSize.toInt() - headerSize
        val payloadBytes = readExactBytes(input, payloadSize, "тело", "деректер", "payload")

        val fullMessage = ByteArray(totalSize.toInt())
        headerBytes.copyInto(fullMessage)
        payloadBytes.copyInto(fullMessage, headerBytes.size)
        return fullMessage
    }

    /**
     * Читает ровно [count] байт или бросает [OfdProtocolViolation], если EOF наступил раньше.
     */
    private fun readExactBytes(
        input: InputStream,
        count: Int,
        partNameRu: String,
        partNameKk: String,
        partNameEn: String
    ): ByteArray {
        val buffer = ByteArray(count)
        var readTotal = 0
        while (readTotal < count) {
            val read = input.read(buffer, readTotal, count - readTotal)
            if (read == -1) {
                throw OfdProtocolViolation(
                    trilingualMessage(
                        "Сервер закрыл соединение до полного чтения: $partNameRu",
                        "Сервер толық оқылғанға дейін қосылымды жауып тастады: $partNameKk",
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
    @Suppress("SameParameterValue")
    private fun readUInt32Le(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and BYTE_MASK
        val b1 = (buffer[offset + OFFSET_1].toLong() and BYTE_MASK) shl SHIFT_8
        val b2 = (buffer[offset + OFFSET_2].toLong() and BYTE_MASK) shl SHIFT_16
        val b3 = (buffer[offset + OFFSET_3].toLong() and BYTE_MASK) shl SHIFT_24
        return (b0 or b1 or b2 or b3) and UINT32_MASK
    }
}
