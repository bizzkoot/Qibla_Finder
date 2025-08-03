# Qibla Application: Sun Calibration Feature

## 1. Purpose & Goal

The Sun Calibration feature provides a high-confidence, compass-independent method for verifying the Qibla direction. By using the sun's known astronomical position, the user can determine the precise error in the device's orientation sensors and apply a correction. This is the ultimate fallback for ensuring accuracy, especially in outdoor environments where the sun is clearly visible.

The goal is to create a simple, guided process where the user aligns their phone with the sun, allowing the app to calculate and correct for local magnetic deviation and sensor inaccuracies.

## 2. Technology Stack

*   **Camera Preview:** CameraX API for a simple, efficient camera feed.
*   **Astronomical Calculations:** A lightweight astronomical library (e.g., a port of a well-tested algorithm or a small, focused library) to calculate the sun's current azimuth and elevation.
*   **UI Integration:** Jetpack Compose, using an `AndroidView` to host the CameraX `PreviewView` and a `Box` to overlay the alignment UI.

## 3. Sun Position Calculation

This is the core logic of the feature.

1.  **Required Data:**
    *   User's current, precise location (Latitude, Longitude) from the `FusedLocationProviderClient`.
    *   Current, precise time (`System.currentTimeMillis()`).

2.  **Astronomical Formula:**
    *   The app will implement a standard algorithm to calculate the sun's topocentric coordinates (azimuth and elevation). The algorithm takes into account the user's location, date, and time.
    *   The output is the **True Azimuth** of the sun (its direction relative to True North).

    *Example Steps in the Algorithm:*
    1.  Calculate Julian Day.
    2.  Calculate sun's geocentric ecliptic coordinates.
    3.  Convert to geocentric equatorial coordinates.
    4.  Convert to topocentric horizontal coordinates (Azimuth and Elevation).

## 4. Calibration Flow & UI

1.  **Entry Point:** The user taps the "Sun Calibration" icon from the main compass screen.

2.  **Instructional UI:**
    *   The screen displays the live camera feed.
    *   An overlay graphic (e.g., a bright circle or a sun icon) is displayed on the screen.
    *   Clear text instructs the user: "Point your camera towards the sun. Align the sun with the circle on your screen."

3.  **Alignment:**
    *   The user physically moves their phone to align the on-screen graphic with the actual sun in the sky.

4.  **Capture & Calculation:**
    *   The user taps a "Calibrate" button once the sun is aligned in the graphic.
    *   At the moment of the tap, the app:
        a.  Records the phone's current heading from the `ROTATION_VECTOR` sensor (the *measured* heading).
        b.  Calculates the sun's *true* azimuth using the astronomical formula.
        c.  Calculates the **error offset**: `Error = True Sun Azimuth - Measured Heading`.

5.  **Applying the Correction:**
    *   This `Error` value is saved as a calibration offset.
    *   The app returns to the Main Compass View.
    *   The main compass logic now calculates the corrected heading for the UI: `Corrected True Heading = (Raw True Heading + Error + 360) % 360`.
    *   The status bar can be updated to show `✅ Sun Calibrated`.

## 5. Edge Cases & Error Handling

*   **Sun Not Visible:** If the sun is below the horizon (night time), the calibration button will be disabled, and a message will state "Sun is not currently visible." The app will calculate this based on the sun's elevation angle.
*   **Camera Permission Denied:** A message will explain that the camera is needed for this feature.
*   **Poor Alignment:** The app will rely on the user to perform the alignment accurately. The instructions must be very clear. Future improvements could involve image processing to auto-detect the sun, but this adds significant complexity.