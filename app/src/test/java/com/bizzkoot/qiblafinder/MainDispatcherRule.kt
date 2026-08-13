package com.bizzkoot.qiblafinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test rule that replaces [Dispatchers.Main] with an eager [UnconfinedTestDispatcher]
 * for the duration of a test. ViewModels launch on [androidx.lifecycle.viewModelScope],
 * which uses `Dispatchers.Main.immediate`; without this rule those coroutines would be
 * queued on Robolectric's main looper and never run (the test thread blocks in
 * runBlocking and nothing idles the looper). An unconfined dispatcher runs the
 * ViewModel's init collections synchronously on the calling thread, making tests
 * deterministic without having to pump the main looper manually.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
