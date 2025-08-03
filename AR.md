# Qibla Application: Augmented Reality (AR) Implementation

## 1. Purpose & Goal

The Augmented Reality (AR) view provides a highly intuitive and foolproof method for users to find the Qibla. By overlaying a virtual indicator in the real world, it removes the cognitive load of interpreting a 2D compass and makes the direction tangible. This feature serves as a powerful verification tool, especially when the device's compass sensor is unreliable.

The primary goal is to render a stable, world-anchored 3D object that points accurately toward the Qibla, independent of the device's immediate orientation once initialized.

## 2. Technology Stack

*   **Core Library:** ARCore
*   **3D Rendering:** Sceneform (or a lightweight alternative like Filament if Sceneform proves too heavy)
*   **UI Integration:** `AndroidView` Composable to embed the AR view within the Jetpack Compose UI structure.

## 3. AR Implementation Flow

1.  **Initialization:**
    *   When the user navigates to the AR view, the app checks for ARCore availability and requests camera permissions if not already granted.
    *   An `ArFragment` or a custom `ArSceneView` is initialized.

2.  **Session Creation:**
    *   An ARCore session is created. The session is configured for world tracking.

3.  **Anchor Placement:**
    *   The core of the AR experience is placing an `Anchor` in the 3D world. This anchor represents the direction of the Qibla.
    *   The anchor's position and rotation are determined by the Qibla bearing calculated from the user's location.
    *   **Strategy:** The anchor will be placed a set distance (e.g., 1-2 meters) in front of the user, rotated to match the Qibla bearing relative to True North. The initial orientation is derived from the `ROTATION_VECTOR` sensor, but once placed, the anchor's position is managed by ARCore's world tracking and is no longer dependent on the magnetometer.

4.  **Rendering the 3D Object:**
    *   A `Renderable` (a 3D model) is loaded. This could be a simple, stylized arrow or a more detailed model of the Kaaba.
    *   An `AnchorNode` is created at the anchor's position.
    *   A `TransformableNode` containing the `Renderable` is attached to the `AnchorNode`. This allows the object to be placed in the scene.

5.  **User Guidance:**
    *   A simple text overlay will guide the user: "Look for the arrow to find the Qibla."
    *   The UI will encourage the user to slowly scan their surroundings to locate the virtual object.

## 4. Error & State Handling

*   **ARCore Not Supported:** If the device does not support ARCore, the "AR View" button will be disabled or hidden. A toast message can inform the user.
*   **Camera Permission Denied:** If camera permission is denied, a message will be displayed explaining the necessity of the camera for the AR feature, with a button to go to settings.
*   **Poor Tracking:** ARCore provides tracking state updates. If tracking quality is poor (e.g., the user is moving too fast, or the environment has no feature points), a message will be displayed: "Move your phone slowly to scan the area."

## 5. Performance Considerations

*   **Model Complexity:** The 3D model will be low-poly to ensure smooth rendering on a wide range of devices.
*   **Lifecycle Management:** The ARCore session will be carefully managed to pause when the app is in the background and resume when it returns to the foreground, conserving battery. The session will be closed when the user navigates away from the AR view.