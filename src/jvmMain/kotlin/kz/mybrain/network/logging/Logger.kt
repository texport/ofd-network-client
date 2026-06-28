package kz.mybrain.network.logging

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

/**
 * Реализация логера на JVM платформе на базе библиотеки SLF4J.
 */
@Suppress("unused")
actual class Logger(private val delegate: org.slf4j.Logger) {
    actual fun info(message: String) {
        delegate.info(message)
    }
    actual fun info(message: String, vararg args: Any?) {
        delegate.info(message, *args)
    }
    actual fun warn(message: String, vararg args: Any?) {
        delegate.warn(message, *args)
    }
    actual fun error(message: String, throwable: Throwable?) {
        delegate.error(message, throwable)
    }
}

/**
 * Реализация фабричного метода получения логера для JVM.
 */
actual fun getLogger(clazz: KClass<*>): Logger {
    return Logger(LoggerFactory.getLogger(clazz.java))
}
