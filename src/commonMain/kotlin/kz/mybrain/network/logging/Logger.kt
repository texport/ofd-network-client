package kz.mybrain.network.logging

import kotlin.reflect.KClass

/**
 * Кроссплатформенный логер для логирования сетевых запросов в KMP-библиотеке.
 */
@Suppress("unused")
expect class Logger {
    fun info(message: String)
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
    fun error(message: String, throwable: Throwable?)
}

/**
 * Фабричный метод получения экземпляра логера по типу класса.
 */
expect fun getLogger(clazz: KClass<*>): Logger
