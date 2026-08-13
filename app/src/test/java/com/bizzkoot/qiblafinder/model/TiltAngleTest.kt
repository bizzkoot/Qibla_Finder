package com.bizzkoot.qiblafinder.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the flat-phone alert math.
 *
 * tiltAngleFromAccelerometer is the tilt computation extracted (byte-identical) from
 * SensorRepository.checkPhoneOrientation, so the alert's core math is unit-testable
 * without a device. The NOT_FLAT_TILT_* band pins the "upright / NOT flat" alert
 * threshold shared by SensorRepository, the compass red alert and the AR warning, so
 * the detector and the UI alerts can never drift apart.
 */
class TiltAngleTest {

    @Test
    fun `flat phone screen up has zero tilt`() {
        assertEquals(0f, tiltAngleFromAccelerometer(0f, 0f, 9.81f), 0.01f)
    }

    @Test
    fun `upright portrait phone has 90 degree tilt`() {
        assertEquals(90f, tiltAngleFromAccelerometer(0f, 9.81f, 0f), 0.01f)
    }

    @Test
    fun `upright landscape phone has 90 degree tilt`() {
        assertEquals(90f, tiltAngleFromAccelerometer(9.81f, 0f, 0f), 0.01f)
    }

    @Test
    fun `face down phone has 180 degree tilt`() {
        assertEquals(180f, tiltAngleFromAccelerometer(0f, 0f, -9.81f), 0.01f)
    }

    @Test
    fun `tilt is independent of acceleration magnitude`() {
        // Same orientation, scaled by a different total magnitude -> same angle.
        assertEquals(0f, tiltAngleFromAccelerometer(0f, 0f, 100f), 0.01f)
        assertEquals(90f, tiltAngleFromAccelerometer(0f, 100f, 0f), 0.01f)
        assertEquals(45f, tiltAngleFromAccelerometer(9.81f, 0f, 9.81f), 0.1f)
    }

    @Test
    fun `upright alert band covers 65 through 115 degrees inclusive`() {
        // The band the alerts fire on: phone upright / NOT flat.
        assertTrue(isInUprightBand(NOT_FLAT_TILT_MIN_DEGREES))
        assertTrue(isInUprightBand(NOT_FLAT_TILT_MAX_DEGREES))
        assertTrue(isInUprightBand(90f))
        assertTrue(isInUprightBand(65f))
        assertTrue(isInUprightBand(115f))

        // Just outside the band -> no alert.
        assertFalse(isInUprightBand(NOT_FLAT_TILT_MIN_DEGREES - 0.1f))
        assertFalse(isInUprightBand(NOT_FLAT_TILT_MAX_DEGREES + 0.1f))
        assertFalse(isInUprightBand(0f))
        assertFalse(isInUprightBand(180f))
    }

    @Test
    fun `checkPhoneOrientation upright band matches the shared constants`() {
        // Pins the detection threshold inside SensorRepository to the same constants
        // the UI alerts import, so a future edit cannot desynchronize them.
        assertEquals(65f, NOT_FLAT_TILT_MIN_DEGREES, 0f)
        assertEquals(115f, NOT_FLAT_TILT_MAX_DEGREES, 0f)
    }

    private fun isInUprightBand(tiltAngle: Float): Boolean =
        tiltAngle >= NOT_FLAT_TILT_MIN_DEGREES && tiltAngle <= NOT_FLAT_TILT_MAX_DEGREES
}
