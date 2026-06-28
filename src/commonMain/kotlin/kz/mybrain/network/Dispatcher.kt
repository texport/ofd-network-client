package kz.mybrain.network

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Ожидаемый фоновый диспетчер для выполнения сетевого ввода-вывода (I/O).
 *
 * На JVM платформе мапится на пул потоков `Dispatchers.IO`, а на iOS на `Dispatchers.Default`
 * для предотвращения блокирования главного потока UI.
 */
internal expect val ioDispatcher: CoroutineDispatcher
