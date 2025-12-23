package kz.mybrain.network

/**
 * TCP-адрес для подключения.
 */
data class OfdEndpoint(val host: String, val port: Int)

/**
 * Сетевой клиент, который отправляет полный запрос и возвращает полный ответ.
 *
 * Контракт:
 * - запрос: заголовок + тело
 * - ответ: заголовок + тело
 * - каждый вызов использует отдельный сокет
 * - запрос должен быть не короче размера заголовка
 */
interface OfdNetworkClient {
    /**
     * Отправляет [request] на [endpoint] и возвращает полный ответ.
     *
     * В случае успеха результат содержит [ByteArray] с заголовком и телом,
     * в случае ошибки — [OfdNetworkClientException].
     */
    suspend fun sendAndReceive(endpoint: OfdEndpoint, request: ByteArray): Result<ByteArray>
}
