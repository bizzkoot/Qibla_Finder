package com.bizzkoot.qiblafinder.model

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/**
 * PRD H1 behavior tests: `SensorRepository.setCalibrationOffset()` must actually change
 * every emitted heading. Previously the offset was applied only via CompassViewModel's
 * reactive pipeline; these tests drive the REAL SensorRepository through Robolectric's
 * ShadowSensorManager and assert that firing the same stable pose before and after
 * `setCalibrationOffset()` shifts the emitted `trueHeading` by exactly the offset
 * (modulo 360), including negative offsets and offsets that wrap across 0/360.
 *
 * Asserting the SHIFT (rather than an absolute heading) is the robust contract: the
 * smoothed/fused heading for a fixed pose is constant between the two readings, so the
 * difference isolates the offset application exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CalibrationOffsetRobolectricTest {

    private lateinit var shadowSensorManager: ShadowSensorManager
    private lateinit var repository: SensorRepository
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private var timestampNs = 1_000_000_000L

    @Before
    fun setUp() {
        val context: Context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSensorManager = shadowOf(sensorManager)

        accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        magnetometer = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        shadowSensorManager.addSensor(accelerometer)
        shadowSensorManager.addSensor(magnetometer)
        shadowSensorManager.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE))

        val locationRepository = LocationRepository(
            context,
            mock(FusedLocationProviderClient::class.java)
        )
        repository = SensorRepository(context, locationRepository)
    }

    private fun fireEvent(sensor: Sensor, vararg values: Float) {
        val event = ShadowSensorManager.createSensorEvent(values.size, sensor.type)
        event.sensor = sensor
        for (i in values.indices) {
            event.values[i] = values[i]
        }
        event.timestamp = timestampNs
        timestampNs += 33_333_333L // ~30 Hz spacing
        shadowSensorManager.sendSensorEventToListeners(event)
    }

    private fun fireUprightPose() {
        // Phone upright (portrait): gravity on +Y -> tilt ≈ 90°; magnetometer values
        // chosen so getRotationMatrix() succeeds for this pose. Computed heading ≈ 0°.
        fireEvent(accelerometer, 0f, 9.81f, 0f)
        fireEvent(magnetometer, 25f, 0f, 0f)
    }

    private fun fireEastPose() {
        // Flat pose with the magnetometer rotated 90° in the device plane, so the
        // computed heading is a clearly non-zero ~270° (useful for exercising wraps
        // that must cross the 0/360 boundary).
        fireEvent(accelerometer, 0f, 0f, 9.81f)
        fireEvent(magnetometer, 25f, 0f, 0f)
    }

    private fun CoroutineScope.collectStates(): Pair<Job, MutableList<OrientationState.Available>> {
        val states = mutableListOf<OrientationState.Available>()
        val job = launch {
            repository.getOrientationFlow().collect { state ->
                if (state is OrientationState.Available) states.add(state)
            }
        }
        return job to states
    }

    private fun normalize(heading: Float): Float {
        var value = heading % 360f
        if (value < 0f) value += 360f
        return value
    }

    private suspend fun waitUntil(
        message: String,
        timeoutMs: Long = 2_000,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("$message: Condition not met within ${timeoutMs}ms")
            delay(5)
        }
    }

    /**
     * Fires enough events of a fixed pose for the smoothing + Kalman fusion to fully
     * converge, then returns the last emitted heading (== the steady-state raw heading).
     * Events are fired in small batches with a drain between them: callbackFlow's
     * 64-slot channel buffer silently drops emissions once it fills, and the collector
     * only drains while the test thread yields (delay inside waitUntil).
     */
    private suspend fun converge(
        states: MutableList<OrientationState.Available>,
        pose: () -> Unit
    ): Float {
        firePoseInDrainedBatches(states, batches = 10, perBatch = 10, pose = pose)
        return states.last().trueHeading
    }

    private suspend fun firePoseInDrainedBatches(
        states: MutableList<OrientationState.Available>,
        batches: Int,
        perBatch: Int,
        pose: () -> Unit
    ) {
        repeat(batches) { batch ->
            val target = states.size + perBatch
            repeat(perBatch) { pose() }
            waitUntil("batch ${batch + 1} drained") { states.size >= target }
        }
    }

    /**
     * Converges on [pose], applies [offset], fires one more reading and asserts the
     * emitted heading shifted by exactly [offset] modulo 360. When [expectWrap] is set,
     * the test also proves the pre-wrap value really crossed the 0/360 boundary, so the
     * wrap path (not just the mod arithmetic) is exercised.
     */
    private suspend fun assertOffsetShift(
        offset: Double,
        states: MutableList<OrientationState.Available>,
        pose: () -> Unit,
        expectWrap: Boolean
    ) {
        val preOffset = converge(states, pose)
        val convergedCount = states.size
        val raw = preOffset + offset.toFloat()

        repository.setCalibrationOffset(offset)
        pose()
        waitUntil("post-offset heading emitted") { states.size >= convergedCount + 1 }
        val postOffset = states.last().trueHeading

        if (expectWrap) {
            assertTrue(
                "test setup: $offset from heading $preOffset must wrap across 0/360 (raw=$raw)",
                raw < 0 || raw >= 360
            )
        }
        val expected = normalize(raw)
        assertEquals(
            "heading after setCalibrationOffset($offset) must equal (H + offset) mod 360; H=$preOffset",
            expected,
            postOffset,
            0.5f
        )
    }

    @Test
    fun `setCalibrationOffset with a negative offset shifts the emitted heading by exactly that offset`() = runBlocking {
        val (job, states) = collectStates()
        waitUntil("flow registered") { shadowSensorManager.getListeners().size == 1 }

        // Heading ~0°, so -25 wraps across 0 into the 330s — covers the negative sign.
        assertOffsetShift(-25.0, states, pose = { fireUprightPose() }, expectWrap = true)

        job.cancel()
        waitUntil("sensors unregistered at end") { shadowSensorManager.getListeners().isEmpty() }
    }

    @Test
    fun `setCalibrationOffset wraps the heading across 0 slash 360 instead of growing past 360`() = runBlocking {
        val (job, states) = collectStates()
        waitUntil("flow registered") { shadowSensorManager.getListeners().size == 1 }

        // Heading ~270°, so +340 pushes the raw value past 360 -> the emitted heading
        // must wrap back into [0,360), not grow past 360.
        assertOffsetShift(340.0, states, pose = { fireEastPose() }, expectWrap = true)

        job.cancel()
        waitUntil("sensors unregistered at end") { shadowSensorManager.getListeners().isEmpty() }
    }

    @Test
    fun `a stored calibration offset is applied on every subsequent reading`() = runBlocking {
        val (job, states) = collectStates()
        waitUntil("flow registered") { shadowSensorManager.getListeners().size == 1 }

        val preOffset = converge(states) { fireUprightPose() }
        repository.setCalibrationOffset(-25.0)

        firePoseInDrainedBatches(states, batches = 1, perBatch = 5) { fireUprightPose() }

        val expected = normalize(preOffset - 25f)
        // Every reading after the offset is applied, not just the first one.
        states.takeLast(5).forEach { state ->
            assertEquals("every emitted heading must carry the offset", expected, state.trueHeading, 0.5f)
        }

        job.cancel()
        waitUntil("sensors unregistered at end") { shadowSensorManager.getListeners().isEmpty() }
    }
}
