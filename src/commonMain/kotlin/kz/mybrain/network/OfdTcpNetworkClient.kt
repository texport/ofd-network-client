package kz.mybrain.network

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
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

/**
 * TCP-клиент сетевого уровня для взаимодействия с ОФД (Казахтелеком).
 *
 * Реализует отправку запроса и чтение одного полного ответа по протоколу CPCR.
 *
 * Особенности работы протокола:
 * 1. Соединение открывается для отправки одного сообщения и закрывается после приема ответа.
 * 2. Общий размер сообщения записан в заголовке по смещению [SIZE_OFFSET] (4-й байт)
 *    в формате little-endian uint32.
 * 3. Размер заголовка по умолчанию равен 18 байтам.
 */
class OfdTcpNetworkClient(
    private val headerSize: Int = 18,
    private val timeoutMillis: Int = 7_000,
) : OfdNetworkClient {

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
        logger.info("Отправка запроса к ОФД ({}:{}). Размер буфера: {} байт", endpoint.host, endpoint.port, request.size)
        
        return try {
            if (request.size < headerSize) {
                logger.warn("Размер запроса {} байт меньше минимального размера заголовка {}", request.size, headerSize)
                return Result.failure(
                    OfdProtocolViolation(
                        trilingualMessage(
                            "Размер запроса ${request.size} меньше размера заголовка $headerSize",
                            "Сұраныс өлшемі ${request.size} тақырып өлшемінен $headerSize кіші",
                            "Request size ${request.size} is smaller than header size $headerSize"
                        )
                    )
                )
            }

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
                        
                        val headerBytes = ByteArray(headerSize)
                        try {
                            logger.info("Чтение заголовка размером {} байт из сокета...", headerSize)
                            readChannel.readFully(headerBytes, 0, headerSize)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logger.warn("Соединение закрыто ОФД до завершения чтения заголовка")
                            throw OfdProtocolViolation(
                                trilingualMessage(
                                    "Сервер закрыл соединение до полного чтения: заголовок",
                                    "Сервер толық оқылғанға дейін қосылымды жауып тастады: тақырып",
                                    "Server closed connection before full header was received"
                                )
                            )
                        }

                        val totalSize = readUInt32Le(headerBytes)
                        logger.info("Прочитан заголовок. Заявленный размер всего сообщения от ОФД: {} байт", totalSize)

                        if (totalSize < headerSize.toLong()) {
                            logger.warn("Указанный в заголовке размер {} меньше размера самого заголовка {}", totalSize, headerSize)
                            throw OfdProtocolViolation(
                                trilingualMessage(
                                    "Размер сообщения $totalSize меньше размера заголовка $headerSize",
                                    "Хабарлама өлшемі $totalSize тақырып өлшемінен $headerSize кіші",
                                    "Message size $totalSize is smaller than header size $headerSize"
                                )
                            )
                        }
                        if (totalSize > Int.MAX_VALUE) {
                            logger.warn("Превышен лимит размера сообщения: {} байт", totalSize)
                            throw OfdProtocolViolation(
                                trilingualMessage(
                                    "Размер сообщения $totalSize превышает Int.MAX_VALUE",
                                    "Хабарлама өлшемі $totalSize Int.MAX_VALUE мәнінен асады",
                                    "Message size $totalSize exceeds Int.MAX_VALUE"
                                )
                            )
                        }

                        val payloadSize = totalSize.toInt() - headerSize
                        val payloadBytes = ByteArray(payloadSize)
                        try {
                            logger.info("Чтение полезной нагрузки размером {} байт из сокета...", payloadSize)
                            readChannel.readFully(payloadBytes, 0, payloadSize)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logger.warn("Соединение разорвано ОФД в процессе чтения полезной нагрузки")
                            throw OfdProtocolViolation(
                                trilingualMessage(
                                    "Сервер закрыл соединение до полного чтения: тело",
                                    "Сервер толық оқылғанға дейін қосылымды жауып тастады: деректер",
                                    "Server closed connection before full payload was received"
                                )
                            )
                        }

                        val fullMessage = ByteArray(totalSize.toInt())
                        headerBytes.copyInto(fullMessage)
                        payloadBytes.copyInto(fullMessage, headerBytes.size)

                        logger.info("Успешно получено и собрано полное сообщение от ОФД размером {} байт", fullMessage.size)
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
        } catch (e: OfdProtocolViolation) {
            Result.failure(e)
        } catch (e: IOException) {
            logger.error("Транспортное исключение при работе с сокетом ОФД", e)
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
        } catch (e: Throwable) {
            logger.error("Непредвиденная ошибка в сетевом клиенте ОФД", e)
            Result.failure(
                OfdTransportFailure(
                    trilingualMessage(
                        "Неизвестная ошибка: ${e::class.simpleName}: ${e.message ?: "нет подробностей"}",
                        "Белгісіз қате: ${e::class.simpleName}: ${e.message ?: "мәліметтер жоқ"}",
                        "Unknown failure: ${e::class.simpleName}: ${e.message ?: "no details"}"
                    ),
                    e
                )
            )
        }
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
