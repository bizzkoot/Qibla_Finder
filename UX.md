# Qibla Application: User Experience (UX) & UI Design

## 1. Overview

This document outlines the user experience (UX) principles, user interface (UI) design, and interaction flows for the Qibla application. The primary goal is to provide a clean, intuitive, and trustworthy interface that is easy to use for all users, regardless of technical proficiency.

## 2. Design Principles

*   **Clarity:** The UI will be uncluttered, with a clear visual hierarchy. Information will be presented in a simple, digestible format.
*   **Trust:** The app will be transparent about sensor status and location accuracy, building user confidence.
*   **Efficiency:** The primary function—finding the Qibla—will be immediate and obvious from the moment the app is opened.
*   **Accessibility:** The design will consider users with visual impairments by using high-contrast colors and clear typography.

## 3. User Flows

### A. First-Time User & Permissions

1.  **Launch:** User opens the app for the first time.
2.  **Permissions:** A system dialog requests `ACCESS_FINE_LOCATION`.
    *   **If Granted:** The app proceeds to the Main Compass View.
    *   **If Denied:** A screen explains why location is essential and provides a button to go to system settings to grant the permission.
    *   **If "Approximate" Granted:** The app proceeds but displays a persistent banner warning of reduced accuracy.

### B. Main Interaction Flow

1.  **Open App:** The Main Compass View is displayed.
2.  **Find Qibla:** The compass needle immediately points to the Qibla.
3.  **Check Status:** The status bar shows the current location and compass accuracy.
4.  **Handle Interference:** If magnetic interference is detected, a clear warning is shown, and the user is guided to move away from metal or calibrate.
5.  **Switch to AR:** User taps an icon to switch to the AR View for visual verification.
6.  **Switch to Sun Calibration:** User taps an icon to switch to the Sun Calibration view for high-precision alignment.

## 4. UI Components & Screens

### A. Main Compass View

This is the primary screen of the application.

*   **Compass Rose:** A visually appealing compass that rotates as the phone moves.
*   **Qibla Needle:** A distinct, animated arrow that always points towards the Qibla's bearing.
*   **Status Bar:** A non-intrusive bar at the top or bottom displaying:
    *   `🛰️ GPS (±5m)` or `📶 WiFi (±30m)`
    *   `✅ Calibrated` or `⚠️ Interference` or `🔄 Calibrating...`
*   **Buttons:** Icons to switch to AR and Sun Calibration views.

### B. Augmented Reality (AR) View

*   **Camera Feed:** A live view from the device's camera.
*   **3D Arrow/Kaaba:** A virtual object (e.g., a floating arrow or a 3D model of the Kaaba) is rendered in the world space, pointing directly to the Qibla.
*   **Guidance Text:** Simple text overlay like "Align with the arrow to find the Qibla."

### C. Sun Calibration View ☀️

*   **Camera Feed:** A live view from the device's camera.
*   **Sun Graphic:** An on-screen overlay (e.g., a circle) that the user must align with the real sun.
*   **Instructional Text:** "Point your camera at the sun and align it with the circle."
*   **Confirmation:** A visual confirmation (e.g., the circle turns green) when alignment is successful, and the compass error is calculated and applied.

## 5. Jetpack Compose Composable Details

The UI will be built using the following key Composables:

*   **`QiblaApp()`:** The root Composable that handles navigation between the different screens.
*   **`CompassScreen()`:**
    *   Uses a `Canvas` Composable to draw the custom compass rose and the Qibla needle.
    *   The needle's rotation will be animated smoothly using `animateFloatAsState`.
*   **`StatusBar()`:** A `Row` Composable containing `Icon` and `Text` to display the real-time status.
*   **`ARScreen()`:**
    *   Integrates an `AndroidView` to host the ARCore `ArSceneView`.
*   **`SunCalibrationScreen()`:**
    *   Uses an `AndroidView` to host the CameraX `PreviewView`.
    *   A `Box` layout is used to overlay the alignment graphic on top of the camera feed.
*   **`PermissionRequestScreen()`:** A simple `Column` with `Text` and a `Button` to guide the user to settings.

### D. Manual Location Adjustment View

*   **Map View:** A top-down map displaying the user's current location (if available). This could be implemented using a library like Google Maps or Mapbox.
*   **Draggable Pin:** A pin that the user can drag to adjust their location.
*   **Accuracy Circle:** A circle centered on the pin, indicating the estimated accuracy radius. The radius will be visually represented (e.g., in meters).
*   **Confirm Button:** A button to confirm the selected location.

The Composable for this view would be:

*   **`ManualLocationScreen()`:**
    *   Uses an `AndroidView` to host the map view.
    *   Implements a `DraggablePin` Composable that allows the user to drag the pin on the map.
    *   Displays an `AccuracyCircle` Composable around the pin.