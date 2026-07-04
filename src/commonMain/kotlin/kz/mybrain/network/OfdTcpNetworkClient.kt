package kz.mybrain.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kz.mybrain.network.logging.getLogger
import kotlin.time.Duration.Companion.milliseconds

private const val SIZE_OFFSET = 4
private const val BYTE_MASK = 0xffL
private const val UINT32_MASK = 0xffffffffL
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
private const val OFFSET_1 = 1
private const val OFFSET_2 = 2
private const val OFFSET_3 = 3
private const val UINT32_BYTE_COUNT = 4
private const val DEFAULT_HEADER_SIZE = 18
private const val DEFAULT_TIMEOUT_MILLIS = 7_000
private const val BYTES_IN_MEBIBYTE = 1024 * 1024
private const val DEFAULT_MAX_RESPONSE_BYTES = 5 * BYTES_IN_MEBIBYTE

/**
 * TCP-клиент сетевого уровня для взаимодействия с ОФД, поддерживающим протокол CPCR.
 *
 * Реализует отправку запроса и чтение одного полного ответа по протоколу CPCR.
 *
 * Особенности работы протокола:
 * 1. Соединение открывается для отправки одного сообщения и закрывается после приема ответа.
 * 2. Общий размер сообщения записан в заголовке по смещению [SIZE_OFFSET] (4-й байт)
 *    в формате little-endian uint32.
 * 3. Размер заголовка по умолчанию равен 18 байтам.
 * 4. Внешняя отмена coroutine пробрасывается вызывающему коду и не преобразуется в [Result.failure].
 * 5. Ответы больше [maxResponseBytes] отклоняются до выделения буфера под тело сообщения.
 */
class OfdTcpNetworkClient(
    private val headerSize: Int = DEFAULT_HEADER_SIZE,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) : OfdNetworkClient {

    init {
        require(headerSize >= SIZE_OFFSET + UINT32_BYTE_COUNT) {
            "headerSize must be at least ${SIZE_OFFSET + UINT32_BYTE_COUNT} bytes " +
                "to contain uint32 size at offset $SIZE_OFFSET"
        }
        require(timeoutMillis > 0) {
            "timeoutMillis must be positive"
        }
        require(maxResponseBytes >= headerSize) {
            "maxResponseBytes must be greater than or equal to headerSize"
        }
    }

    private val logger = getLogger(OfdTcpNetworkClient::class)

    /**
     * Подключается к указанному [endpoint], отправляет байты [request],
     * вычитывает полный пакет ответа от ОФД и завершает соединение.
     *
     * @param endpoint адрес хоста и порт сервера ОФД.
     * @param request байтовый пакет запроса к отправке.
     * @return [Result] с байтовым массивом полного сообщения (заголовок + тело) или сетевой ошибкой.
     */
    override suspend fun sendAndReceive(endpoint: OfdEndpoint, request: ByteArray): Result<ByteArray> {
        logger.info(
            "Отправка запроса к ОФД ({}:{}). Размер буфера: {} байт",
            endpoint.host,
            endpoint.port,
            request.size
        )

        return try {
            validateRequest(request)?.let { return Result.failure(it) }

            withTimeout(timeoutMillis.milliseconds) {
                val selectorManager = SelectorManager(ioDispatcher)
                try {
                    logger.info("Установка TCP соединения с {}:{}", endpoint.host, endpoint.port)
                    val socket = aSocket(selectorManager).tcp().connect(endpoint.host, endpoint.port)
                    try {
                        val writeChannel = socket.openWriteChannel(autoFlush = true)
                        logger.info("Отправка {} байт в сетевой сокет...", request.size)
                        writeChannel.writeFully(request)

                        val readChannel = socket.openReadChannel()
                        val headerBytes = readHeader(readChannel)
                        val totalSize = readUInt32Le(headerBytes)

                        logger.info("Прочитан заголовок. Заявленный размер всего сообщения от ОФД: {} байт", totalSize)
                        validateTotalSize(totalSize)

                        val payloadSize = totalSize.toInt() - headerSize
                        val payloadBytes = readPayload(readChannel, payloadSize)
                        val fullMessage = assembleMessage(headerBytes, payloadBytes, totalSize.toInt())

                        logger.info(
                            "Успешно получено и собрано полное сообщение от ОФД размером {} байт",
                            fullMessage.size
                        )
                        Result.success(fullMessage)
                    } finally {
                        socket.close()
                    }
                } finally {
                    selectorManager.close()
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.error("Превышено время ожидания ответа от ОФД (лимит: $timeoutMillis мс)", e)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: OfdProtocolViolation) {
            Result.failure(e)
        } catch (e: IOException) {
            logger.error("Транспортное исключение при работе с сокетом ОФД", e)
            Result.failure(e.toOfdTransportFailure())
        } catch (e: Throwable) {
            logger.error("Непредвиденная ошибка в сетевом клиенте ОФД", e)
            Result.failure(e.toUnexpectedOfdTransportFailure())
        }
    }

    private fun validateRequest(request: ByteArray): OfdProtocolViolation? {
        if (request.size >= headerSize) {
            return null
        }

        logger.warn("Размер запроса {} байт меньше минимального размера заголовка {}", request.size, headerSize)
        return OfdRequestTooShort(
            trilingualMessage(
                "Размер запроса ${request.size} меньше размера заголовка $headerSize",
                "Сұраныс өлшемі ${request.size} тақырып өлшемінен $headerSize кіші",
                "Request size ${request.size} is smaller than header size $headerSize"
            )
        )
    }

    private suspend fun readHeader(readChannel: ByteReadChannel): ByteArray {
        val headerBytes = ByteArray(headerSize)
        try {
            logger.info("Чтение заголовка размером {} байт из сокета...", headerSize)
            readChannel.readFully(headerBytes, 0, headerSize)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.warn("Соединение закрыто ОФД до завершения чтения заголовка")
            throw OfdResponseHeaderIncomplete(
                trilingualMessage(
                    "Сервер закрыл соединение до полного чтения: заголовок",
                    "Сервер толық оқылғанға дейін қосылымды жауып тастады: тақырып",
                    "Server closed connection before full header was received"
                )
            )
        }
        return headerBytes
    }

    private fun validateTotalSize(totalSize: Long) {
        if (totalSize < headerSize.toLong()) {
            logger.warn(
                "Указанный в заголовке размер {} меньше размера самого заголовка {}",
                totalSize,
                headerSize
            )
            throw OfdResponseSizeTooSmall(
                trilingualMessage(
                    "Размер сообщения $totalSize меньше размера заголовка $headerSize",
                    "Хабарлама өлшемі $totalSize тақырып өлшемінен $headerSize кіші",
                    "Message size $totalSize is smaller than header size $headerSize"
                )
            )
        }

        if (totalSize > maxResponseBytes.toLong()) {
            logger.warn("Превышен лимит размера сообщения: {} байт", totalSize)
            throw OfdResponseSizeTooLarge(
                trilingualMessage(
                    "Размер сообщения $totalSize превышает лимит $maxResponseBytes",
                    "Хабарлама өлшемі $totalSize $maxResponseBytes шегінен асады",
                    "Message size $totalSize exceeds limit $maxResponseBytes"
                )
            )
        }
    }

    private suspend fun readPayload(readChannel: ByteReadChannel, payloadSize: Int): ByteArray {
        val payloadBytes = ByteArray(payloadSize)
        if (payloadSize == 0) {
            return payloadBytes
        }

        try {
            logger.info("Чтение полезной нагрузки размером {} байт из сокета...", payloadSize)
            readChannel.readFully(payloadBytes, 0, payloadSize)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.warn("Соединение разорвано ОФД в процессе чтения полезной нагрузки")
            throw OfdResponsePayloadIncomplete(
                trilingualMessage(
                    "Сервер закрыл соединение до полного чтения: тело",
                    "Сервер толық оқылғанға дейін қосылымды жауып тастады: деректер",
                    "Server closed connection before full payload was received"
                )
            )
        }
        return payloadBytes
    }

    private fun assembleMessage(headerBytes: ByteArray, payloadBytes: ByteArray, totalSize: Int): ByteArray {
        val fullMessage = ByteArray(totalSize)
        headerBytes.copyInto(fullMessage)
        payloadBytes.copyInto(fullMessage, headerBytes.size)
        return fullMessage
    }

    /**
     * Преобразует 4 байта из буфера в unsigned 32-bit integer по схеме Little Endian.
     */
    private fun readUInt32Le(buffer: ByteArray): Long {
        val b0 = buffer[SIZE_OFFSET].toLong() and BYTE_MASK
        val b1 = (buffer[SIZE_OFFSET + OFFSET_1].toLong() and BYTE_MASK) shl SHIFT_8
        val b2 = (buffer[SIZE_OFFSET + OFFSET_2].toLong() and BYTE_MASK) shl SHIFT_16
        val b3 = (buffer[SIZE_OFFSET + OFFSET_3].toLong() and BYTE_MASK) shl SHIFT_24
        return (b0 or b1 or b2 or b3) and UINT32_MASK
    }
}

internal fun IOException.toOfdTransportFailure(): OfdTransportFailure {
    return OfdTransportFailure(
        trilingualMessage(
            "Транспортная ошибка: ${this::class.simpleName}: ${message ?: "нет подробностей"}",
            "Көліктік қате: ${this::class.simpleName}: ${message ?: "мәліметтер жоқ"}",
            "Transport failure: ${this::class.simpleName}: ${message ?: "no details"}"
        ),
        this
    )
}

internal fun Throwable.toUnexpectedOfdTransportFailure(): OfdTransportFailure {
    return OfdTransportFailure(
        trilingualMessage(
            "Неизвестная ошибка: ${this::class.simpleName}: ${message ?: "нет подробностей"}",
            "Белгісіз қате: ${this::class.simpleName}: ${message ?: "мәліметтер жоқ"}",
            "Unknown failure: ${this::class.simpleName}: ${message ?: "no details"}"
        ),
        this,
        OfdTransportFailureReason.UNEXPECTED_FAILURE,
        OfdFailureSide.UNKNOWN
    )
}
