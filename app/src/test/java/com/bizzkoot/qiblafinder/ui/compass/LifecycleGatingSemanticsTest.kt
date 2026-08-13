package com.bizzkoot.qiblafinder.ui.compass

import com.bizzkoot.qiblafinder.model.CompassStatus
import com.bizzkoot.qiblafinder.model.OrientationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Validates the H4 lifecycle-gating pattern used by CompassViewModel:
 * `screenVisible.flatMapLatest { if (visible) sensorFlow() else emptyFlow() }`
 * fed into a `combine`, mirrors the real SensorRepository callbackFlow shape
 * (registers on collect, never emits Initializing on (re)start, unregisters in
 * awaitClose). Proves that:
 *  - hiding stops sensor collection and freezes the last UI state (no emissions),
 *  - location updates still flow through while hidden (combine re-emits with the
 *    cached orientation), and
 *  - returning re-registers sensors and resumes with an Available reading —
 *    never an Initializing flash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleGatingSemanticsTest {

    private fun fakeOrientationFlow(
        sensorRegistrations: Ref,
        sensorUnregistrations: Ref,
        headingCounter: Ref
    ): Flow<OrientationState> = callbackFlow {
        sensorRegistrations.value++
        val job = launch {
            while (true) {
                delay(1)
                headingCounter.value++
                trySend(
                    OrientationState.Available(
                        trueHeading = headingCounter.value.toFloat(),
                        compassStatus = CompassStatus.OK
                    )
                )
            }
        }
        awaitClose {
            sensorUnregistrations.value++
            job.cancel()
        }
    }

    @Test
    fun gatingStopsSensorsWhenHiddenAndResumesWithoutInitializingFlash() = runBlocking {
        val sensorRegistrations = Ref(0)
        val sensorUnregistrations = Ref(0)
        val headingCounter = Ref(0)

        val screenVisible = MutableStateFlow(true)
        val locationFlow = MutableStateFlow("loc-1")

        val gatedOrientationFlow = screenVisible.flatMapLatest { visible ->
            if (visible) fakeOrientationFlow(sensorRegistrations, sensorUnregistrations, headingCounter) else emptyFlow()
        }

        val uiStates = mutableListOf<Pair<String, OrientationState>>()
        val collectJob = launch {
            combine(locationFlow, gatedOrientationFlow) { loc, orient ->
                loc to orient
            }.collect { uiStates.add(it) }
        }

        // Visible: sensors registered and the UI reaches Available (no Initializing emitted).
        waitUntil { uiStates.isNotEmpty() }
        assertTrue(uiStates.last().second is OrientationState.Available)
        assertFalse("Gated flow must never emit Initializing on start", uiStates.last().second is OrientationState.Initializing)
        assertEquals(1, sensorRegistrations.value)
        assertEquals(0, sensorUnregistrations.value)

        // Hide: sensors unregistered and the UI freezes (combine caches last values -> no emissions).
        screenVisible.value = false
        waitUntil { sensorUnregistrations.value == 1 }
        val countBeforeHide = uiStates.size
        val lastBeforeHide = uiStates.last()
        delay(30)
        assertEquals("No UI emissions while hidden", countBeforeHide, uiStates.size)
        assertEquals(lastBeforeHide, uiStates.last())

        // Location updates still flow through while hidden, re-emitting with the cached heading.
        locationFlow.value = "loc-2"
        waitUntil { uiStates.any { it.first == "loc-2" } }
        assertEquals(lastBeforeHide.second, uiStates.last().second)
        assertEquals(1, sensorRegistrations.value)
        assertEquals(1, sensorUnregistrations.value)

        // Return: sensors re-register and the first re-emitted state is Available, not Initializing.
        screenVisible.value = true
        waitUntil { sensorRegistrations.value == 2 }
        waitUntil { uiStates.last() != lastBeforeHide }
        assertTrue(uiStates.last().second is OrientationState.Available)
        assertFalse("No Initializing flash on return", uiStates.last().second is OrientationState.Initializing)
        assertEquals("Sensor collection stopped while hidden", 1, sensorUnregistrations.value)

        collectJob.cancel()
    }

    private suspend fun waitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Condition not met within ${timeoutMs}ms")
            delay(5)
        }
    }

    private class Ref(var value: Int)
}
