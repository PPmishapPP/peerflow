package ru.peerflow.demo.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Подменяет `Dispatchers.Main` на тестовый.
 *
 * `viewModelScope` жёстко привязан к главному диспетчеру, а в JVM-тестах
 * Android-цикла сообщений нет: без подмены любой `launch` упадёт с
 * `Module with the Main dispatcher had failed to initialize`.
 *
 * [UnconfinedTestDispatcher] вместо стандартного — чтобы `launch` выполнялся
 * сразу: тогда в тесте не нужны `advanceUntilIdle()` после каждого вызова.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
