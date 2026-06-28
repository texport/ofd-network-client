package kz.mybrain.network.logging

import kotlin.reflect.KClass

/**
 * Реализация логера на Apple/iOS платформе через вывод в стандартный поток (stdout).
 */
@Suppress("unused")
actual class Logger(private val tag: String) {
    actual fun info(message: String) {
        println("INFO [$tag]: $message")
    }
    actual fun info(message: String, vararg args: Any?) {
        println("INFO [$tag]: ${format(message, *args)}")
    }
    actual fun warn(message: String, vararg args: Any?) {
        println("WARN [$tag]: ${format(message, *args)}")
    }
    actual fun error(message: String, throwable: Throwable?) {
        println("ERROR [$tag]: $message")
        throwable?.printStackTrace()
    }

    private fun format(message: String, vararg args: Any?): String {
        var result = message
        for (arg in args) {
            result = result.replaceFirst("{}", arg?.toString() ?: "null")
        }
        return result
    }
}

/**
 * Реализация фабричного метода получения логера для iOS.
 */
actual fun getLogger(clazz: KClass<*>): Logger {
    return Logger(clazz.simpleName ?: "UnknownClass")
}
