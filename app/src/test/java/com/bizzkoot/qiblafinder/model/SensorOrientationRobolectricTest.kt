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
import org.junit.Assert.assertFalse
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
 * End-to-end regression tests for the flat-phone alert, driving the REAL
 * SensorRepository sensor pipeline through Robolectric's fake SensorManager
 * (ShadowSensorManager / ShadowSensor). This is the only way to exercise
 * getOrientationFlow() on a host JVM:
 *
 *  - "flat phone" (accelerometer z = +9.81) must emit Available(tilt≈0,
 *    isPhoneFlat=false) — no alert;
 *  - "upright phone" (accelerometer y = +9.81) must emit Available(tilt≈90,
 *    isPhoneFlat=true) — the compass/AR "lay the phone flat" alert fires on this;
 *  - the H4 lifecycle-gating pattern (cancel the collection, re-collect) must keep
 *    detecting both poses;
 *  - the repository-level serialization must ensure a second concurrent collection
 *    (the AR screen's) never overlaps the first one's sensor listeners, so the two
 *    collections cannot corrupt each other's shared filter/fusion state.
 */
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class SensorOrientationRobolectricTest {

    private lateinit var shadowSensorManager: ShadowSensorManager
    private lateinit var repository: SensorRepository
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private var timestampNs = 1_000_000_000L

    @Before
    fun setUp() {
        // Production constructs SensorRepository with the Activity context
        // (LocalContext.current in the composable), so mirror that here: an Activity
        // is a visual context that can resolve Context.getDisplay() for the
        // tilt-compensated heading math, which an Application context cannot.
        val context: Context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSensorManager = shadowOf(sensorManager)

        // Install the fake sensors the repository asks SensorManager for.
        accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        magnetometer = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        val gyroscope = ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE)
        shadowSensorManager.addSensor(accelerometer)
        shadowSensorManager.addSensor(magnetometer)
        shadowSensorManager.addSensor(gyroscope)

        val locationRepository = LocationRepository(
            context,
            mock(FusedLocationProviderClient::class.java)
        )
        repository = SensorRepository(context, locationRepository)
    }

    private fun fireEvent(sensor: Sensor, vararg values: Float) {
        // ShadowSensorManager exposes a static factory that allocates a SensorEvent
        // with a values buffer and a sensor; the instance method createSensorEvent()
        // has no args (and would produce an unusable event).
        val event = ShadowSensorManager.createSensorEvent(values.size, sensor.type)
        event.sensor = sensor
        for (i in values.indices) {
            event.values[i] = values[i]
        }
        event.timestamp = timestampNs
        timestampNs += 33_333_333L // ~30 Hz spacing
        shadowSensorManager.sendSensorEventToListeners(event)
    }

    private fun fireFlatPose() {
        // Phone flat, screen up: gravity on +Z.
        fireEvent(accelerometer, 0f, 0f, 9.81f)
        // Magnetometer values chosen so getRotationMatrix() succeeds for this pose.
        fireEvent(magnetometer, 0f, 25f, 0f)
    }

    private fun fireUprightPose() {
        // Phone upright (portrait): gravity on +Y -> tilt ≈ 90°, isPhoneFlat == true.
        fireEvent(accelerometer, 0f, 9.81f, 0f)
        // Magnetometer rotated 90° so the magnetic vector is not parallel to gravity.
        fireEvent(magnetometer, 25f, 0f, 0f)
    }

    private fun assertFlat(state: OrientationState.Available) {
        assertEquals("tilt for flat phone", 0f, state.phoneTiltAngle, 0.5f)
        assertFalse("flat phone must not be flagged as upright (isPhoneFlat)", state.isPhoneFlat)
        assertTrue("flat phone is vertical", state.isPhoneVertical)
        assertValidHeading(state.trueHeading)
    }

    private fun assertUpright(state: OrientationState.Available) {
        assertEquals("tilt for upright phone", 90f, state.phoneTiltAngle, 0.5f)
        assertTrue("upright phone must flag the alert (isPhoneFlat is INVERTED naming)", state.isPhoneFlat)
        assertFalse("upright phone is not vertical (tilt ~90)", state.isPhoneVertical)
        assertValidHeading(state.trueHeading)
    }

    private fun assertValidHeading(trueHeading: Float) {
        assertTrue("heading must be finite: $trueHeading", trueHeading.isFinite())
        assertTrue("heading must be in [0,360): $trueHeading", trueHeading >= 0f && trueHeading < 360f)
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

    @Test
    fun `flat then upright phone flips isPhoneFlat and tilt in the emitted state`() = runBlocking {
        val (job, states) = collectStates()
        waitUntil("flow registered") { shadowSensorManager.getListeners().size == 1 }

        fireFlatPose()
        waitUntil("first (flat) state emitted") { states.isNotEmpty() }
        assertFlat(states.last())

        fireUprightPose()
        waitUntil("upright state emitted") { states.any { it.isPhoneFlat } }
        assertUpright(states.last())

        // And back to flat: the alert must turn off again.
        fireFlatPose()
        waitUntil("flat state emitted again") { states.isNotEmpty() && !states.last().isPhoneFlat }
        assertFlat(states.last())

        job.cancel()
    }

    @Test
    fun `H4 style re-collection after cancellation still detects flat and upright`() = runBlocking {
        // First collection (compass visible).
        val (job1, states1) = collectStates()
        waitUntil("first collection registered") { shadowSensorManager.getListeners().size == 1 }
        fireFlatPose()
        waitUntil("first collection flat state") { states1.isNotEmpty() }
        assertFlat(states1.last())

        // screenVisible=false: the gated flow is cancelled and sensors unregister.
        job1.cancel()
        waitUntil("sensors unregistered after cancel") { shadowSensorManager.getListeners().isEmpty() }

        // screenVisible=true again: a fresh collection re-registers (H4 flatMapLatest
        // switch) and must still detect the upright alert pose.
        val (job2, states2) = collectStates()
        waitUntil("second collection registered") { shadowSensorManager.getListeners().size == 1 }
        fireUprightPose()
        waitUntil("second collection upright state") { states2.any { it.isPhoneFlat } }
        assertUpright(states2.last())

        job2.cancel()
        waitUntil("sensors unregistered after second cancel") { shadowSensorManager.getListeners().isEmpty() }
    }

    @Test
    fun `concurrent collections never overlap listeners and the second takes over cleanly`() = runBlocking {
        // Compass collection active.
        val (jobA, statesA) = collectStates()
        waitUntil("flow A registered") { shadowSensorManager.getListeners().size == 1 }
        fireFlatPose()
        waitUntil("flow A flat state") { statesA.isNotEmpty() }
        assertFlat(statesA.last())

        // AR screen starts its own collection while the compass one is still active:
        // the repository mutex must keep it from registering a second listener.
        val (jobB, statesB) = collectStates()
        delay(50) // generous window for an (incorrect) concurrent registration
        assertEquals("only one collection may own the sensor listeners at a time", 1, shadowSensorManager.getListeners().size)

        // Events still reach the active collection only.
        fireUprightPose()
        waitUntil("flow A upright state") { statesA.any { it.isPhoneFlat } }
        assertUpright(statesA.last())
        assertTrue("waiting collection must not have emitted yet", statesB.isEmpty())

        // Compass hides -> its collection is cancelled -> the waiting AR collection
        // acquires ownership and starts working. join() makes the handoff
        // deterministic: after the collector completes, A has run awaitClose
        // (unregistered + released the mutex) and B is the only contender.
        jobA.cancel()
        jobA.join()
        waitUntil("flow B acquired listeners after A cancelled") { shadowSensorManager.getListeners().size == 1 }
        fireUprightPose()
        waitUntil("flow B upright state") { statesB.isNotEmpty() }
        assertUpright(statesB.last())

        jobB.cancel()
        waitUntil("sensors unregistered at end") { shadowSensorManager.getListeners().isEmpty() }
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
}
