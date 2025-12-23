package kz.mybrain.network

import java.io.IOException

/**
 * Базовый класс ошибок сетевого клиента.
 */
sealed class OfdNetworkClientException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Ответ не получен за время таймаута.
 */
class OfdTimeoutNoResponse(message: String, cause: Throwable? = null) :
    OfdNetworkClientException(message, cause)

/**
 * Нарушение протокола со стороны сервера (недочитанные данные, неверный размер и т.д.).
 */
class OfdProtocolViolation(message: String) : OfdNetworkClientException(message)

/**
 * Транспортная ошибка ввода-вывода, не попавшая в другие категории.
 */
class OfdTransportFailure(message: String, cause: Throwable? = null) :
    OfdNetworkClientException(message, cause)

/**
 * Формирует сообщение об ошибке на русском и английском.
 */
fun bilingualMessage(ru: String, en: String): String = "RU: $ru | EN: $en"
