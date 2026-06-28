package kz.mybrain.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Реализация диспетчера ввода-вывода для JVM на базе стандартного пула потоков Dispatchers.IO.
 */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
