# Compass Core Logic Refactoring Plan (Rev. 2)

**Author:** Gemini AI Assistant
**Date:** August 5, 2025
**Status:** Proposed Plan

## 1. Executive Summary

This document outlines a revised plan to refactor the application's core orientation (compass) logic. This plan supersedes the previous version and incorporates critical user feedback.

The primary goals are to:
1.  Fix the non-functional "Calibrate" button on the `CompassScreen` by implementing a robust, user-triggered calibration prompt.
2.  Introduce an **automatic calibration prompt** that appears when the underlying sensor reports low accuracy.
3.  Improve overall compass accuracy and stability by aligning the implementation with modern Android best practices (`Sensor.TYPE_ROTATION_VECTOR`).
4.  Simplify the `SensorRepository` codebase while preserving the critical Sun Calibration feature.

This refactoring will create a **hybrid calibration model**: an automatic system that ensures baseline accuracy, combined with a manual override that gives the user ultimate control and builds trust in the application.

## 2. Problem Analysis

The core problems remain the same as identified previously:

1.  **Broken Manual Calibration:** The "Calibrate" button is not functional because the UI state is never updated to show the `CalibrationOverlay`.
2.  **Implementation Divergence:** The code uses complex manual sensor fusion instead of the simpler, more stable `Sensor.TYPE_ROTATION_VECTOR` recommended in the project's `Technical.md`.
3.  **Unnecessary Complexity:** The manual figure-8 detection logic is unreliable and reinvents functionality already provided by the Android OS.

## 3. Proposed Solution: The Hybrid Calibration Model

### 3.1. The New User Experience

The user will have two clear ways to ensure their compass is calibrated:

*   **Automatic Prompt:** When the app is running, if the Android OS detects that the compass sensor's accuracy has degraded, the `CalibrationOverlay` will appear automatically, instructing the user to perform the figure-8 motion. It will disappear once the sensor's accuracy is restored to a high level.
*   **Manual "Calibrate" Button (User-Initiated):** The user can press the "Calibrate" button at any time. This will immediately display the same `CalibrationOverlay`, giving them a sense of control and a way to proactively ensure accuracy. The overlay will similarly disappear when the sensor reports high accuracy, or if the user chooses to dismiss it.

### 3.2. `SensorRepository` Refactoring

-   **Primary Sensor:** The repository will be refactored to use `Sensor.TYPE_ROTATION_VECTOR` as its primary source for orientation. This provides clean, hardware-fused data.
-   **Interference Detection:** It will continue to listen to `Sensor.TYPE_MAGNETIC_FIELD` for the sole purpose of the existing magnetic interference detection logic.
-   **Accuracy Reporting:** It will listen to the `onAccuracyChanged` callback. When accuracy changes, it will update its `OrientationState` with the corresponding `CompassStatus` (`NEEDS_CALIBRATION` or `OK`).
-   **Code Simplification:** All manual fusion logic, figure-8 detection variables, and the related progress-tracking methods will be removed.
-   **Sun Calibration:** The `setCalibrationOffset(offset: Double)` method will be kept to ensure the Sun Calibration feature continues to function correctly.

### 3.3. `CompassViewModel` Refactoring

-   **State Management:** The ViewModel will be the single source of truth for the calibration UI.
-   **Manual Trigger:** It will retain the `startCalibration()` function, which will now simply set a state flow (`_showCalibration.value = true`). The "Calibrate" button will call this.
-   **Dismiss Action:** It will have a `stopCalibration()` function that sets `_showCalibration.value = false`. This will be used by a dismiss button on the overlay.
-   **Automatic Trigger Logic:** It will launch a coroutine to observe the `orientationState.compassStatus` from the repository.
    -   If `compassStatus` becomes `NEEDS_CALIBRATION`, it will automatically call its own `startCalibration()` to show the overlay.
    -   If `compassStatus` becomes `OK`, it will call `stopCalibration()` to hide the overlay.

### 3.4. `CompassScreen` and `CalibrationOverlay` UI Refactoring

-   **"Calibrate" Button:** The button will be kept and its `onClick` will be wired to `viewModel.startCalibration()`. It will be enabled at all times.
-   **`CalibrationOverlay`:**
    -   Its visibility will be controlled by the `viewModel.showCalibration` state flow.
    -   The `calibrationProgress` parameter will be removed, as we no longer manually calculate progress.
    -   A dismiss button will be added to the overlay, which will call `viewModel.stopCalibration()`.

## 4. Step-by-Step Implementation Plan

1.  **Refactor `SensorRepository.kt`:**
    -   Remove properties related to manual sensor fusion and figure-8 tracking.
    -   Update `getOrientationFlow()` to register `TYPE_ROTATION_VECTOR` and `TYPE_MAGNETIC_FIELD`.
    -   Implement the new `updateOrientation()` logic based on the rotation vector.
    -   In `onAccuracyChanged`, update the `OrientationState` with the new `CompassStatus`.
    -   Remove the now-obsolete `startCalibration`, `stopCalibration`, and `updateCalibrationProgress` methods.
2.  **Refactor `CompassViewModel.kt`:**
    -   Modify `startCalibration()` to simply set `_showCalibration.value = true`.
    -   Add `stopCalibration()` to set `_showCalibration.value = false`.
    -   Add a `viewModelScope.launch` block to observe `sensorRepository.orientationState` and call `start/stopCalibration` based on the `compassStatus`.
3.  **Refactor `ui/calibration/CalibrationOverlay.kt`:**
    -   Remove the `calibrationProgress` parameter.
    -   Add an `onDismiss` lambda parameter.
    -   Add a "Dismiss" button to the overlay that invokes the `onDismiss` lambda.
4.  **Refactor `ui/compass/CompassScreen.kt`:**
    -   Update the `onClick` for the "Calibrate" button to call `viewModel.startCalibration()`.
    -   Update the call to `CalibrationOverlay`, passing `viewModel::stopCalibration` to the new `onDismiss` parameter and removing the `calibrationProgress` argument.
5.  **Verification:**
    -   Execute a debug build (`./gradlew app:assembleDebug`) to ensure the code compiles and the app runs without crashing.

## 5. Detailed Implementation Plan (Rev. 3)

This revised plan provides a more granular, step-by-step guide to ensure a safe and successful refactoring.

### Phase 1: Core `SensorRepository` Refactoring

The goal of this phase is to switch the primary orientation sensor to `TYPE_ROTATION_VECTOR` and remove all legacy manual fusion and calibration logic.

1.  **Modify `SensorRepository.kt`:**
    *   **Remove Unused Properties:** Delete the following properties related to manual fusion and figure-8 tracking:
        *   `accelerometerReading`, `magnetometerReading`, `rotationMatrix`, `orientationAngles`, `remappedRotationMatrix`
        *   `lastHeading`, `calibrationStartTime`, `calibrationMoves`, `lastCalibrationMove`, `isCalibrating`, `lastCalibrationHeading`
        *   `movementHistory`, `totalRotation`, `directionChanges`, `lastMovementDirection`
        *   `hasAccelerometerData`, `hasMagnetometerData`
    *   **Update `OrientationState.Available`:** Remove the `calibrationProgress` parameter from this data class.
    *   **Refactor `getOrientationFlow()`:**
        *   Change the registered sensors from `TYPE_ACCELEROMETER` and `TYPE_MAGNETIC_FIELD` to `TYPE_ROTATION_VECTOR` and `TYPE_MAGNETIC_FIELD`.
        *   The primary listener will now be for `TYPE_ROTATION_VECTOR`.
        *   The `onSensorChanged` logic for `TYPE_ACCELEROMETER` should be removed. The logic for `TYPE_MAGNETIC_FIELD` should be kept *only* for interference detection.
    *   **Rewrite `updateOrientation()`:**
        *   This method will now be triggered by `TYPE_ROTATION_VECTOR` events.
        *   It will take the `rotationVector` from the sensor event.
        *   Use `SensorManager.getRotationMatrixFromVector()` to get the rotation matrix.
        *   Use `SensorManager.getOrientation()` to get the orientation angles.
        *   The rest of the logic (magnetic declination, smoothing, applying calibration offset) can remain similar, but will now be based on the much cleaner data from the rotation vector.
    *   **Simplify `onAccuracyChanged()`:**
        *   This method will now listen for accuracy changes on the `TYPE_ROTATION_VECTOR` sensor.
        *   When accuracy is low, it will update the `OrientationState`'s `compassStatus` to `NEEDS_CALIBRATION`. When high, it will set it to `OK`.
    *   **Delete Obsolete Methods:** Remove the following methods entirely:
        *   `startCalibration()`
        *   `stopCalibration()`
        *   `updateCalibrationProgress()`

### Phase 2: `ViewModel` and UI State Management

This phase adapts the `ViewModel` to handle the new calibration flow and drive the UI state correctly.

1.  **Modify `CompassViewModel.kt`:**
    *   **Remove Progress State:** Delete the `_calibrationProgress` and `calibrationProgress` state flows.
    *   **Update `startCalibration()`:** Change the implementation to simply set `_showCalibration.value = true`.
    *   **Update `stopCalibration()`:** Change the implementation to simply set `_showCalibration.value = false`.
    *   **Implement Automatic Trigger:** Add a new `viewModelScope.launch` block that collects the `orientationState` flow from the `sensorRepository`.
        *   Inside the collector, check the `compassStatus`.
        *   If `compassStatus == CompassStatus.NEEDS_CALIBRATION`, call `startCalibration()` (which now just shows the overlay).
        *   If `compassStatus == CompassStatus.OK`, call `stopCalibration()` (which now just hides the overlay).

### Phase 3: UI Layer Refactoring

This phase updates the UI components to reflect the simplified state and new interaction model.

1.  **Modify `ui/calibration/CalibrationOverlay.kt`:**
    *   **Remove `calibrationProgress` parameter.** The overlay will no longer show a percentage.
    *   The `LinearProgressIndicator` and the percentage text should be removed. The animated figure-8 graphic can remain as a visual guide.
    *   Ensure the `onDismiss` lambda is called by the "Skip" or "Dismiss" button.
2.  **Modify `ui/compass/CompassScreen.kt`:**
    *   **Update `CalibrationOverlay` call:**
        *   Remove the `calibrationProgress` argument.
        *   The `isVisible` parameter will now be correctly driven by `viewModel.showCalibration.collectAsState()`.
        *   Pass `viewModel::stopCalibration` to the `onDismiss` parameter.
    *   **Verify "Calibrate" Button:** Ensure the `onClick` for the "Calibrate" button correctly calls `viewModel.startCalibration()`.

### Phase 4: Verification and Validation

This phase ensures the refactoring was successful and did not introduce regressions.

1.  **Compile and Run:** Execute `./gradlew app:assembleDebug` to confirm the code compiles. Run the app on an emulator or physical device.
2.  **Manual Calibration Test:**
    *   Tap the "Calibrate" button.
    *   **Expected:** The `CalibrationOverlay` appears.
    *   Tap the "Dismiss" button.
    *   **Expected:** The `CalibrationOverlay` disappears.
3.  **Automatic Calibration Test (Requires Emulator/Device with Sensor Control):**
    *   If using an emulator, set the motion sensor to a state of low accuracy.
    *   **Expected:** The `CalibrationOverlay` appears automatically.
    *   Set the sensor accuracy back to high.
    *   **Expected:** The `CalibrationOverlay` disappears automatically.
4.  **Core Functionality Test:**
    *   Verify the compass needle points correctly.
    *   Verify the Qibla direction is accurate.
    *   Verify the Sun Calibration feature still works as expected.
    *   Verify the AR view functions correctly with the new sensor data.

## 6. Verification Plan

1.  Run the command `./gradlew app:assembleDebug` to ensure the refactored code builds successfully.
2.  If the debug build is successful, I will confirm with you before proceeding with any further steps, such as creating a release build.
