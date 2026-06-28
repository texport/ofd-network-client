# ofd-network-client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.texport/ofd-network-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.texport/ofd-network-client)
[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/texport/ofd-network-client/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/ofd-network-client/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/ofd-network-client/actions)
[![Coverage Status](https://img.shields.io/badge/Coverage-100%25-success.svg)](#)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

> [!IMPORTANT]
> **Disclaimer:** This is an unofficial, community-maintained library. It is not officially endorsed by, affiliated with, or sponsored by JSC "KazakhTelecom", the State Revenue Committee of the Republic of Kazakhstan, or any official OFD provider.
> 
> **Дисклеймер:** Данный проект является неофициальной библиотекой, поддерживаемой сообществом. Он не связан, не спонсируется и не утверждался АО «Казахтелеком», Комитетом государственных доходов РК или любыми другими официальными провайдерами ОФД.

---

## Documentation in English

A lightweight, high-performance, and coroutine-based Kotlin Multiplatform (KMP) TCP client designed for data exchange with OFD (Operator of Fiscal Data) servers in Kazakhstan. 

It implements a stateless, short-lived socket connection pattern ("one connection per request/response transaction"), which is highly resilient for mobile environments and cellular networks (LTE/3G/Wi-Fi).

### Key Features
- **Kotlin Multiplatform Support**: Runs seamlessly on JVM, Android, and Apple/iOS Native platforms.
- **Stateless TCP Socket Handling**: Connects, sends the payload, reads the full response, and immediately closes the socket to prevent idle connection drops.
- **Ktor Sockets Core**: Powered by `io.ktor:ktor-network` for fully asynchronous and non-blocking I/O.
- **100% Test Coverage**: Verified by a comprehensive integration test suite mocking real TCP socket loops.
- **Pure KMP Logging**: Integrates expect/actual Logger abstraction (delegating to SLF4J on JVM and console logging on iOS).

---

### Installation

Add the dependency to your shared `commonMain` source set inside `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.texport:ofd-network-client:1.1.0")
            }
        }
    }
}
```

---

## Документация на русском языке

Легковесный и высокопроизводительный асинхронный TCP-клиент на Kotlin Multiplatform (KMP) для обмена данными с серверами ОФД (Операторов фискальных данных) Республики Казахстан.

Библиотека реализует модель короткоживущих сокетов («один сокет на одну транзакцию запрос-ответ»), что является наиболее отказоустойчивым решением для мобильных устройств и сотовых сетей (LTE/3G/Wi-Fi), где постоянные TCP-соединения часто обрываются или блокируются файрволами.

### Преимущества
- **Поддержка Kotlin Multiplatform**: Поддерживает работу на JVM, Android и нативных Apple/iOS платформах.
- **Короткоживущие сокеты**: Клиент устанавливает соединение, отправляет пакет, считывает полный ответ и сразу же закрывает сокет, предотвращая утечку ресурсов и удержание «битых» портов.
- **Ядро Ktor Sockets**: Полностью асинхронный неблокирующий API на базе библиотеки `io.ktor:ktor-network`.
- **100% покрытие тестами**: Интеграционные тесты симулируют полный цикл работы TCP сокета и полностью покрывают все ветки логики.
- **Чистый KMP Logger**: Внедрена expect/actual кроссплатформенная абстракция логирования (SLF4J для JVM, консольный лог для iOS).

---

### Подключение библиотеки

Добавьте зависимость в ваш общий набор исходников `commonMain` в `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.texport:ofd-network-client:1.1.0")
            }
        }
    }
}
```
