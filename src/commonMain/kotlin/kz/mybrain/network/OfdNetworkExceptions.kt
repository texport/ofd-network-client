package kz.mybrain.network

import kotlinx.io.IOException

/**
 * Базовый класс ошибок сетевого клиента.
 */
sealed class OfdNetworkClientException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Сторона, на которой возникла или наиболее вероятно возникла проблема.
 */
enum class OfdFailureSide {
    CLIENT,
    SERVER,
    NETWORK,
    UNKNOWN,
}

/**
 * Машинно-читаемая причина тайм-аута.
 */
enum class OfdTimeoutReason {
    NO_RESPONSE,
}

/**
 * Машинно-читаемая причина нарушения протокола.
 */
enum class OfdProtocolViolationReason {
    REQUEST_TOO_SHORT,
    RESPONSE_HEADER_INCOMPLETE,
    RESPONSE_PAYLOAD_INCOMPLETE,
    RESPONSE_SIZE_TOO_SMALL,
    RESPONSE_SIZE_TOO_LARGE,
}

/**
 * Машинно-читаемая причина транспортной ошибки.
 */
enum class OfdTransportFailureReason {
    IO_FAILURE,
    UNEXPECTED_FAILURE,
}

/**
 * Ответ не получен за время тайм-аута.
 */
class OfdTimeoutNoResponse(
    message: String,
    cause: Throwable? = null,
    val reason: OfdTimeoutReason = OfdTimeoutReason.NO_RESPONSE,
    val side: OfdFailureSide = OfdFailureSide.NETWORK,
) :
    OfdNetworkClientException(message, cause)

/**
 * Нарушение протокола со стороны сервера (недочитанные данные, неверный размер и т.д.).
 */
open class OfdProtocolViolation(
    message: String,
    val reason: OfdProtocolViolationReason,
    val side: OfdFailureSide,
) : OfdNetworkClientException(message)

/**
 * Запрос клиента меньше минимального размера заголовка.
 */
class OfdRequestTooShort(message: String) :
    OfdProtocolViolation(message, OfdProtocolViolationReason.REQUEST_TOO_SHORT, OfdFailureSide.CLIENT)

/**
 * Сервер закрыл соединение до полного чтения заголовка ответа.
 */
class OfdResponseHeaderIncomplete(message: String) :
    OfdProtocolViolation(message, OfdProtocolViolationReason.RESPONSE_HEADER_INCOMPLETE, OfdFailureSide.SERVER)

/**
 * Сервер закрыл соединение до полного чтения тела ответа.
 */
class OfdResponsePayloadIncomplete(message: String) :
    OfdProtocolViolation(message, OfdProtocolViolationReason.RESPONSE_PAYLOAD_INCOMPLETE, OfdFailureSide.SERVER)

/**
 * Размер ответа, указанный в заголовке, меньше размера заголовка.
 */
class OfdResponseSizeTooSmall(message: String) :
    OfdProtocolViolation(message, OfdProtocolViolationReason.RESPONSE_SIZE_TOO_SMALL, OfdFailureSide.SERVER)

/**
 * Размер ответа, указанный в заголовке, превышает локальный лимит клиента.
 */
class OfdResponseSizeTooLarge(message: String) :
    OfdProtocolViolation(message, OfdProtocolViolationReason.RESPONSE_SIZE_TOO_LARGE, OfdFailureSide.SERVER)

/**
 * Транспортная ошибка ввода-вывода, не попавшая в другие категории.
 */
class OfdTransportFailure(
    message: String,
    cause: Throwable? = null,
    val reason: OfdTransportFailureReason = OfdTransportFailureReason.IO_FAILURE,
    val side: OfdFailureSide = OfdFailureSide.NETWORK,
) :
    OfdNetworkClientException(message, cause)

/**
 * Формирует сообщение об ошибке на русском, казахском и английском.
 */
internal fun trilingualMessage(ru: String, kk: String, en: String): String = "RU: $ru | KK: $kk | EN: $en"
