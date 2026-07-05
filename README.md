# ofd-network-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.texport/ofd-network-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.texport/ofd-network-client)
[![Version](https://img.shields.io/badge/version-1.2.0-blue.svg)](https://github.com/texport/ofd-network-client/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/ofd-network-client/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/ofd-network-client/actions)
[![Coverage Status](https://img.shields.io/badge/Coverage-100%25-success.svg)](#)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

> [!IMPORTANT]
> **Disclaimer:** This is an unofficial, community-maintained library. It is not officially endorsed by, affiliated with, or sponsored by any official OFD provider or government authority.
> 
> **Дисклеймер:** Данный проект является неофициальной библиотекой, поддерживаемой сообществом. Он не связан, не спонсируется и не утверждался официальными провайдерами ОФД или государственными органами.

---

## Documentation in English

A lightweight, high-performance, and coroutine-based Kotlin Multiplatform (KMP) TCP client designed for data exchange with OFD (Operator of Fiscal Data) servers that support the CPCR protocol.

It implements a stateless, short-lived socket connection pattern ("one connection per request/response transaction"), which is highly resilient for mobile environments and cellular networks (LTE/3G/Wi-Fi).

### Key Features
- **Kotlin Multiplatform Support**: Runs seamlessly on JVM, Android, and Apple/iOS Native platforms.
- **Stateless TCP Socket Handling**: Connects, sends the payload, reads the full response, and immediately closes the socket to prevent idle connection drops.
- **Ktor Sockets Core**: Powered by `io.ktor:ktor-network` for fully asynchronous and non-blocking I/O.
- **100% Test Coverage**: Verified by a comprehensive integration test suite mocking real TCP socket loops.
- **Pure KMP Logging**: Integrates expect/actual Logger abstraction (delegating to SLF4J on JVM and console logging on iOS).

### Behavior Contract

- Each `sendAndReceive` call opens exactly one TCP socket, sends the complete request, reads one complete response, and closes the socket.
- The request must be at least as large as `headerSize`.
- The response size is read from the header at byte offset `4` as an unsigned little-endian 32-bit integer.
- A response smaller than `headerSize` or larger than `maxResponseBytes` fails with `OfdProtocolViolation`.
- A read/connect timeout fails with `OfdTimeoutNoResponse`.
- Transport-level I/O failures fail with `OfdTransportFailure`.
- External coroutine cancellation is propagated to the caller and is not converted into `Result.failure`.
- The default `maxResponseBytes` is 5 MiB.

```kotlin
val client = OfdTcpNetworkClient(
    headerSize = 18,
    timeoutMillis = 7_000,
    maxResponseBytes = 5 * 1024 * 1024,
)

val result = client.sendAndReceive(
    endpoint = OfdEndpoint(host = "ofd.example.kz", port = 12345),
    request = requestBytes,
)
```

### Error Classification

All library failures inherit from `OfdNetworkClientException`.

| Error | Side | Meaning |
| --- | --- | --- |
| `OfdRequestTooShort` | Client | Local request is smaller than `headerSize`. |
| `OfdResponseHeaderIncomplete` | Server | Server closed the connection before a full response header was read. |
| `OfdResponsePayloadIncomplete` | Server | Server closed the connection before the declared payload was read. |
| `OfdResponseSizeTooSmall` | Server | Response header declared a size smaller than `headerSize`. |
| `OfdResponseSizeTooLarge` | Server | Response header declared a size larger than local `maxResponseBytes`. |
| `OfdTimeoutNoResponse` | Network | No complete response was received within `timeoutMillis`. |
| `OfdTransportFailure` | Network/Unknown | Socket I/O or unexpected platform/runtime failure. |

`OfdProtocolViolation`, `OfdTimeoutNoResponse`, and `OfdTransportFailure` also expose machine-readable `reason` and `side` properties.

### Getting Started / Integration

#### Kotlin Multiplatform & Android

Add the dependency to your shared `commonMain` source set inside `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.texport:ofd-network-client:1.2.0")
            }
        }
    }
}
```

#### Apple Swift Package Manager (SPM)

You can integrate this library directly into your iOS project using Xcode's Swift Package Manager:
1. In Xcode, select **File ➔ Add Package Dependencies...**
2. Enter the repository URL: `https://github.com/texport/ofd-network-client.git`
3. Set the version rules to **Up to Next Major** starting with `1.2.0`.

---

### Architecture Boundary & Limits

The `ofd-network-client` operates as a low-level network transport library and enforces a clear boundary between pure connection/protocol logic and application-level responsibilities:

- **Inside the Library (Responsibilities):**
  - TCP connection lifecycle management (connect/disconnect).
  - Short-lived TCP sockets (single transaction request-response loop).
  - CPCR packet header parsing (extracting message length at byte offset `4`).
  - Validation of message bounds (`maxResponseBytes`, `headerSize`) to prevent Out Of Memory (OOM) failures before allocating response buffers.
  - Multiplatform expect/actual definitions for I/O dispatching and SLF4J/native logging.
- **Outside the Library (Delegated to Consumers):**
  - Persistent command buffer/database storage (no offline queue database is bundled).
  - Thread synchronization, locking, lease structures, and scheduling across multiple client nodes.
  - Retry policies, exponential backoffs, and sync coordination logic (delegated to libraries like `superkassa-offline-queue`).

---

## Документация на русском языке

Легковесный и высокопроизводительный асинхронный TCP-клиент на Kotlin Multiplatform (KMP) для обмена данными с серверами ОФД (операторов фискальных данных), поддерживающими протокол CPCR.

Библиотека реализует модель короткоживущих сокетов («один сокет на одну транзакцию запрос-ответ»), что является наиболее отказоустойчивым решением для мобильных устройств и сотовых сетей (LTE/3G/Wi-Fi), где постоянные TCP-соединения часто обрываются или блокируются файрволами.

### Преимущества
- **Поддержка Kotlin Multiplatform**: Поддерживает работу на JVM, Android и нативных Apple/iOS платформах.
- **Короткоживущие сокеты**: Клиент устанавливает соединение, отправляет пакет, считывает полный ответ и сразу же закрывает сокет, предотвращая утечку ресурсов и удержание «битых» портов.
- **Ядро Ktor Sockets**: Полностью асинхронный неблокирующий API на базе библиотеки `io.ktor:ktor-network`.
- **100% покрытие тестами**: Интеграционные тесты симулируют полный цикл работы TCP сокета и полностью покрывают все ветки логики.
- **Чистый KMP Logger**: Внедрена expect/actual кроссплатформенная абстракция логирования (SLF4J для JVM, консольный лог для iOS).

### Контракт поведения

- Каждый вызов `sendAndReceive` открывает ровно один TCP-сокет, отправляет полный запрос, читает один полный ответ и закрывает сокет.
- Запрос должен быть не меньше `headerSize`.
- Размер ответа читается из заголовка по смещению `4` как unsigned little-endian 32-bit integer.
- Ответ меньше `headerSize` или больше `maxResponseBytes` завершается ошибкой `OfdProtocolViolation`.
- Тайм-аут подключения или чтения завершается ошибкой `OfdTimeoutNoResponse`.
- Транспортные I/O ошибки завершаются ошибкой `OfdTransportFailure`.
- Внешняя отмена coroutine пробрасывается вызывающему коду и не преобразуется в `Result.failure`.
- Значение `maxResponseBytes` по умолчанию равно 5 MiB.

```kotlin
val client = OfdTcpNetworkClient(
    headerSize = 18,
    timeoutMillis = 7_000,
    maxResponseBytes = 5 * 1024 * 1024,
)

val result = client.sendAndReceive(
    endpoint = OfdEndpoint(host = "ofd.example.kz", port = 12345),
    request = requestBytes,
)
```

### Классификация ошибок

Все ошибки библиотеки наследуются от `OfdNetworkClientException`.

| Ошибка | Сторона | Значение |
| --- | --- | --- |
| `OfdRequestTooShort` | Client | Локальный запрос меньше `headerSize`. |
| `OfdResponseHeaderIncomplete` | Server | Сервер закрыл соединение до полного чтения заголовка ответа. |
| `OfdResponsePayloadIncomplete` | Server | Сервер закрыл соединение до полного чтения заявленного тела ответа. |
| `OfdResponseSizeTooSmall` | Server | Заголовок ответа объявил размер меньше `headerSize`. |
| `OfdResponseSizeTooLarge` | Server | Заголовок ответа объявил размер больше локального `maxResponseBytes`. |
| `OfdTimeoutNoResponse` | Network | Полный ответ не получен за `timeoutMillis`. |
| `OfdTransportFailure` | Network/Unknown | Socket I/O ошибка или неожиданная platform/runtime ошибка. |

`OfdProtocolViolation`, `OfdTimeoutNoResponse` и `OfdTransportFailure` также содержат машинно-читаемые свойства `reason` и `side`.

### Интеграция и подключение

#### Kotlin Multiplatform и Android

Добавьте зависимость в ваш общий набор исходников `commonMain` в `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.texport:ofd-network-client:1.2.0")
            }
        }
    }
}
```

#### Apple Swift Package Manager (SPM)

Вы можете подключить библиотеку непосредственно в iOS-приложение с помощью Swift Package Manager в Xcode:
1. Выберите в Xcode: **File ➔ Add Package Dependencies...**
2. Введите URL репозитория: `https://github.com/texport/ofd-network-client.git`
3. Установите правило версии **Up to Next Major** начиная с `1.2.0`.

---

### Архитектурные границы и ограничения

Библиотека `ofd-network-client` выполняет роль низкоуровневого сетевого транспорта и четко разделяет ответственность между чистой логикой подключения/протокола и прикладным уровнем:

- **Внутри библиотеки (Зона ответственности):**
  - Управление жизненным циклом TCP-подключения (установка и закрытие соединения).
  - Короткоживущие сокеты (один запрос-ответ на транзакцию).
  - Чтение и парсинг заголовка CPCR (извлечение размера сообщения по смещению `4`).
  - Проверка корректности размера пакета (`maxResponseBytes`, `headerSize`) для предотвращения переполнения памяти (OOM) до выделения буфера под тело ответа.
  - Expect/actual определения для логирования и I/O диспетчеров.
- **Вне библиотеки (Делегировано вызывающему коду):**
  - Постоянное буферное хранилище / база данных (база данных очереди не поставляется с библиотекой).
  - Синхронизация потоков, блокировки аренды чеков (lease locks) и планирование выполнения на нескольких узлах (нодах).
  - Политика повторных попыток (retries), экспоненциальная задержка (backoff) и логика координации синхронизации (делегировано внешним библиотекам, таким как `superkassa-offline-queue`).
```
