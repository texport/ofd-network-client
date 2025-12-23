# OFD Network Client

Минимальная Kotlin-библиотека для TCP обмена в формате request/response. Это
NetworkClient для работы с ОФД и доставки сообщений, сформированных по протоколу
передачи данных ККМ, разрабатываемому КГД Республики Казахстан. Клиент отправляет
один запрос (уже содержащий header + payload), читает полный ответ на основании
поля размера в заголовке и закрывает сокет.

## Возможности
- Один метод: отправить запрос и получить полный ответ
- Корректно читает сообщения больше 1024 байт
- Таймаут 7 секунд с отдельной ошибкой
- Потокобезопасность: каждый вызов использует отдельный сокет

## Предположения протокола
- Запрос и ответ — это `header + payload`
- Размер заголовка — 18 байт
- Общий размер сообщения хранится в заголовке по смещению 4 (little-endian uint32)
- Клиент инициирует соединение и закрывает сокет после ответа

## Подключение
Добавьте зависимость, если этот модуль используется как отдельная библиотека:
```kotlin
dependencies {
    implementation("kz.mybrain:ofd-network-client:<version>")
}
```

Если вы используете код прямо в этом проекте, дополнительное подключение не нужно.

## Использование
Клиент получает на вход готовый байтовый запрос (header + payload) и возвращает
полный ответ (header + payload) через `Result<ByteArray>`.

```kotlin
import kz.mybrain.network.OfdEndpoint
import kz.mybrain.network.OfdTcpNetworkClient

suspend fun example(request: ByteArray) {
    val client = OfdTcpNetworkClient()
    val result = client.sendAndReceive(OfdEndpoint("127.0.0.1", 12345), request)
    result.onSuccess { response ->
        // response содержит полный ответ: header + payload
    }.onFailure { error ->
        // OfdTimeoutNoResponse / OfdProtocolViolation / OfdTransportFailure
    }
}
```

## Что нужно указать в запросе
- `request` должен быть полностью сформирован: 18 байт заголовка + payload
- Длина `request` не может быть меньше 18 байт, иначе будет ошибка протокола
- В заголовке по смещению 4 должен быть записан полный размер сообщения
  (header + payload) в формате little-endian uint32

## Ошибки
- `OfdTimeoutNoResponse` — ответа нет за время таймаута
- `OfdProtocolViolation` — неполный header/payload или неверный размер
- `OfdTransportFailure` — другие ошибки ввода-вывода

## Сборка
```bash
./gradlew build
```
