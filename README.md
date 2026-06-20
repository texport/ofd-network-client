# ofd-network-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.texport/ofd-network-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.texport/ofd-network-client)
[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/texport/ofd-network-client/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/ofd-network-client/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/ofd-network-client/actions)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

> [!IMPORTANT]
> **Disclaimer:** This is an unofficial, community-maintained library. It is not officially endorsed by, affiliated with, or sponsored by JSC "KazakhTelecom", the State Revenue Committee of the Republic of Kazakhstan, or any official OFD provider.
> 
> **Дисклеймер:** Данный проект является неофициальной библиотекой, поддерживаемой сообществом. Он не связан, не спонсируется и не утверждался АО «Казахтелеком», Комитетом государственных доходов РК или любыми другими официальными провайдерами ОФД.

---

## Documentation in English

A lightweight, high-performance, and coroutine-based Kotlin/JVM TCP client designed for data exchange with OFD (Operator of Fiscal Data) servers in Kazakhstan. 

It implements a stateless, short-lived socket connection pattern ("one connection per request/response transaction"), which is highly resilient for mobile environments and cellular networks (LTE/3G/Wi-Fi).

### Key Features
- **Stateless TCP Socket Handling**: Connects, sends the payload, reads the full response, and immediately closes the socket to prevent idle connection drops.
- **Coroutines Native**: Fully asynchronous and non-blocking, designed with `Dispatchers.IO` for seamless JVM and Android integration.
- **Header-Prefixed Length Resolution**: Automatically parses the total message size from the 18-byte CPCR header (reads a 4-byte little-endian `uint32` at offset 4) to ensure complete payload loading without partial reads.
- **Typed Error Handling**: Wraps socket errors, timeouts, and protocol violations into clean, multi-language Kotlin `Result` exceptions.

---

### Architecture & Design Principles

The library is built strictly following modern software design principles:
- **Clean Architecture (Infrastructure Layer)**: The client acts as a low-level network adapter. It only handles binary byte arrays, leaving higher-level serialization, business rules, and protocols to consumer modules (like `ofd-proto-codec`).
- **KISS (Keep It Simple, Stupid)**: Avoids complex connection pooling or persistent keep-alive mechanisms because long-lived TCP connections are highly unstable on mobile/cellular networks and lead to ghost connections or firewall drops. A single stateless transaction is extremely robust and simple.
- **SOLID**:
  - *Single Responsibility (SRP)*: The `OfdTcpNetworkClient` is solely responsible for writing a request payload to a socket, reading a sized-prefixed response, and closing the connection.
  - *Open/Closed (OCP) & Dependency Inversion (DIP)*: Consumers depend on the abstract `OfdNetworkClient` interface, which is easily mockable in tests.
  - *Interface Segregation (ISP)*: The interface has a single method: `sendAndReceive`.

---

### Exception & Error Message Model

For ease of logging and operations in bilingual or multilingual environments, the library throws detailed exceptions that help developers and support engineers diagnose connectivity issues instantly:
- **Multi-language Messages**: Exceptions contain error messages in the format `RU: [Сообщение] | KK: [Хабарлама] | EN: [Message]`. This allows directly rendering errors to cashiers/support in Russian and Kazakh, and developers in English without extra client-side translation layers.
- **Typed Exceptions**:
  - `OfdTimeoutNoResponse`: Thrown when a socket connect or read times out (e.g., when the OFD server is down, or there is no mobile connection).
  - `OfdProtocolViolation`: Thrown when the protocol is violated (e.g., when the server closes the stream prematurely, or the parsed header size is smaller than the header itself).
  - `OfdTransportFailure`: General transport issues, wrapping other unexpected `IOException`s.

---

### Installation

The library is officially published and hosted on **Maven Central**.

#### Via Maven Central (Recommended)
Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:ofd-network-client:1.0.0")
}
```

> [!TIP]
> **Local Development (Optional):** If you are contributing to the library itself and want to test changes locally from source, you can include the local directory as a Composite Build in your consumer's `settings.gradle.kts` via `includeBuild("../ofd-network-client")`.

---

### Usage Example

```kotlin
import kz.mybrain.network.OfdEndpoint
import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.network.OfdTimeoutNoResponse
import kz.mybrain.network.OfdProtocolViolation
import kz.mybrain.network.OfdTransportFailure

suspend fun sendFiscalDocument(requestData: ByteArray) {
    // 1. Initialize client (default header size = 18 bytes, timeout = 7 seconds)
    val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 7000)
    val endpoint = OfdEndpoint(host = "37.150.215.187", port = 7777)
    
    // 2. Perform non-blocking request/response exchange
    val result = client.sendAndReceive(endpoint, requestData)
    
    // 3. Handle result using functional API
    result.onSuccess { responseBytes ->
        println("Successfully received ${responseBytes.size} bytes from OFD")
    }.onFailure { throwable ->
        when (throwable) {
            is OfdTimeoutNoResponse -> {
                // Connection or response timed out (e.g. OFD offline or no cell signal)
                // Queue the receipt locally to retry later
            }
            is OfdProtocolViolation -> {
                // Wrong header format, bad size field, or unexpected socket EOF
                // Log critical protocol error
            }
            is OfdTransportFailure -> {
                // Network socket errors (e.g., Connection reset, Host unreachable)
            }
        }
        // Will print: "RU: [Текст] | KK: [Мәтін] | EN: [Text]"
        System.err.println("Error message: ${throwable.message}")
    }
}
```

---

## Документация на русском языке

Легковесный и высокопроизводительный асинхронный TCP-клиент на Kotlin/JVM для обмена данными с серверами ОФД (Операторов фискальных данных) Республики Казахстан.

Библиотека реализует модель короткоживущих сокетов («один сокет на одну транзакцию запрос-ответ»), что является наиболее отказоустойчивым решением для мобильных устройств и сотовых сетей (LTE/3G/Wi-Fi), где постоянные TCP-соединения часто обрываются или блокируются файрволами.

### Преимущества
- **Короткоживущие сокеты**: Клиент устанавливает соединение, отправляет пакет, считывает полный ответ и сразу же закрывает сокет, предотвращая утечку ресурсов и удержание «битых» портов.
- **Поддержка Корутин**: Полностью асинхронный неблокирующий API, выполняющий I/O-операции в контексте `Dispatchers.IO`.
- **Чтение по размеру из заголовка**: Автоматически считывает размер сообщения (4 байта little-endian `uint32` по смещению 4 в 18-байтовом заголовке протокола CPCR), дочитывая пакет до конца и исключая неполный прием данных.
- **Типизированная обработка ошибок**: Локализует сетевые ошибки, таймауты и нарушения протокола ОФД в виде многоязычных (RU/KK/EN) исключений внутри Kotlin-контейнера `Result`.

---

### Архитектура и принципы проектирования

Проект разработан в строгом соответствии с ключевыми инженерными практиками:
- **Clean Architecture (Слой инфраструктуры)**: Клиент изолирован от бизнес-правил и логики сериализации (таких как protobuf/JSON). Он принимает и возвращает сырые массивы байт (`ByteArray`), что делает его полностью независимым и легко переиспользуемым.
- **KISS (Keep It Simple, Stupid)**: Отказ от сложных пулов соединений и постоянных сокетов («keep-alive»). На сотовых сетях длинные сессии приводят к фантомным зависаниям и обрывам. Атомарная транзакция «подключение-запрос-ответ-закрытие» гарантирует максимальную надежность и простоту.
- **SOLID**:
  - *Single Responsibility (SRP)*: Класс `OfdTcpNetworkClient` сфокусирован только на работе с сокетом и разборе заголовка.
  - *Open/Closed (OCP) & Dependency Inversion (DIP)*: Основной код зависит от интерфейса `OfdNetworkClient`, что позволяет легко заменить TCP-реализацию на HTTP или Mock в тестах.
  - *Interface Segregation (ISP)*: Интерфейс предоставляет единственный лаконичный метод `sendAndReceive`.

---

### Модель ошибок и исключений

Для удобства логирования и эксплуатации в многоязычной среде (например, на территории Республики Казахстан), библиотека поддерживает генерацию сообщений об ошибках сразу на нескольких языках:
- **Многоязычные сообщения**: Сообщение исключения имеет вид `RU: [Текст ошибки] | KK: [Қате мәтіні] | EN: [Error message]`. Это позволяет выводить текст ошибки кассиру (на казахском или русском) или в интерфейс техподдержки без ручного маппинга и написания словарей перевода на стороне клиента.
- **Типизированные исключения**:
  - `OfdTimeoutNoResponse`: Вызывается при превышении таймаута подключения или чтения (сервер ОФД недоступен или отсутствует сотовая связь).
  - `OfdProtocolViolation`: Вызывается при нарушении структуры протокола (размер сообщения в заголовке некорректен, сокет закрыт сервером раньше времени).
  - `OfdTransportFailure`: Общая сетевая ошибка ввода-вывода (сброс соединения сокетом, отсутствие маршрута до хоста).

---

### Подключение библиотеки

### Подключение библиотеки

Библиотека официально опубликована и доступна в репозитории **Maven Central**.

#### Через Maven Central (Рекомендуемый способ)
Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:ofd-network-client:1.0.0")
}
```

> [!TIP]
> **Локальная разработка (Опционально):** Если вы дорабатываете саму библиотеку и хотите тестировать изменения локально из исходников, вы можете временно подключить её как Composite Build в `settings.gradle.kts` вашего основного проекта с помощью `includeBuild("../ofd-network-client")`.

---

### Пример использования

```kotlin
import kz.mybrain.network.OfdEndpoint
import kz.mybrain.network.OfdTcpNetworkClient
import kz.mybrain.network.OfdTimeoutNoResponse
import kz.mybrain.network.OfdProtocolViolation
import kz.mybrain.network.OfdTransportFailure

suspend fun sendFiscalDocument(requestData: ByteArray) {
    // 1. Инициализируем клиент (заголовок = 18 байт, таймаут = 7 секунд)
    val client = OfdTcpNetworkClient(headerSize = 18, timeoutMillis = 7000)
    val endpoint = OfdEndpoint(host = "37.150.215.187", port = 7777)
    
    // 2. Отправляем и ждем ответ в неблокирующем корутин-контексте
    val result = client.sendAndReceive(endpoint, requestData)
    
    // 3. Обрабатываем результат
    result.onSuccess { responseBytes ->
        println("Успешно получено ${responseBytes.size} байт от ОФД")
    }.onFailure { throwable ->
        when (throwable) {
            is OfdTimeoutNoResponse -> {
                // Сервер ОФД не ответил за время таймаута (нет сети или сервер перегружен)
                // Сохраняем чек в локальную очередь для повтора
            }
            is OfdProtocolViolation -> {
                // Нарушен протокол (сервер закрыл поток раньше времени или неверный размер)
            }
            is OfdTransportFailure -> {
                // Системные ошибки ввода-вывода (нет маршрута к хосту, сброшено сокетом)
            }
        }
        // Выведет: "RU: [Текст] | KK: [Мәтін] | EN: [Text]"
        System.err.println("Описание ошибки: ${throwable.message}")
    }
}
```
