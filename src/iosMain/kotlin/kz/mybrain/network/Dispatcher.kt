package kz.mybrain.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Реализация диспетчера ввода-вывода для iOS на базе стандартного диспетчера Dispatchers.Default.
 */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
