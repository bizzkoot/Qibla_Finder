# Compass Performance & Responsiveness PRD

**Date:** 2025-10-03  
**Author:** Codex (AI assistant)  
**Stakeholders:** Product (Qibla experience), Android Engineering, QA, Customer Support  
**Status:** Draft for approval

## 1. Background & Context
The Qibla Finder app delivers compass guidance alongside sun calibration and AR features. While flagship devices (e.g., Samsung S23 Ultra) show smooth compass response, users on low-to-mid range devices report sluggish updates when rotating their phones. Accurate and timely heading feedback is critical to sustain trust in the Qibla direction.

## 2. Problem Statement
On constrained hardware, compass headings lag behind real-world rotation. The current implementation uses:
- `SensorManager.SENSOR_DELAY_UI` sampling regardless of device capabilities.
- A fixed low-pass filter (`alpha = 0.15`) that adds latency.
- Sensor callbacks delivered on the main thread, competing with Compose rendering and extensive logging.
- A 300 ms linear tween animation on every heading change that causes the needle to “chase” the actual heading.

This combination yields slow visual response and potential frame drops on lower-performance devices, threatening user trust in compass accuracy.

## 3. Goals
1. Deliver responsive compass motion on low-end and high-end Android devices without sacrificing directional accuracy.
2. Maintain or improve heading stability and eliminate perceptible lag when the device rotates.
3. Reduce UI thread load to avoid dropped frames during compass usage.
4. Preserve existing calibration workflows (sun/manual) without introducing manual performance toggles.

## 4. Non-Goals
- Introducing new UI controls for sensor responsiveness (auto-adjustments only).
- Updating AR or sun calibration pipelines beyond sensor offset propagation.
- Shipping iOS or cross-platform changes.

## 5. User Personas & Scenarios
- **Primary:** Practicing Muslim relying on Qibla compass in regions with modest Android hardware. Needs quick, trustworthy heading updates indoors and outdoors.
- **Secondary:** Support staff verifying customer complaints on demo devices. Requires consistent behavior across device tiers.

### Key Scenario
User rotates a budget Android device (~2019 chipset). The compass should track rotation within ~0.3 s of physical movement, report calibration/interference status correctly, and avoid jitter.

## 6. Requirements

### 6.1 Functional
- F1: Compass heading updates must adapt sampling rate based on device capability: request `SENSOR_DELAY_GAME` (≈20 ms) by default and gracefully degrade if unavailable or throttled.
- F2: Apply an adaptive smoothing filter that derives `alpha` from actual sensor interval and heading variance (auto mode). Target <200 ms settling time while suppressing noise.
- F3: Offload magnetometer/accelerometer fusion and declination math to a background `HandlerThread`; UI thread should receive already-processed heading updates.
- F4: Limit debug logging to aggregated intervals (≥1 s) and disable verbose canvas logs in release builds.
- F5: Update compass rendering to use a responsive animation tuned for accuracy (see §8).
- F6: Replace deprecated `WindowManager.defaultDisplay` usage with modern display APIs while keeping backward compatibility (API 24+).

### 6.2 Non-Functional
- N1: Average frame rendering time during compass use on a representative low-end device (e.g., Snapdragon 660 class) must stay below 16 ms (60 FPS target) in profiling traces.
- N2: Battery impact must remain within ±5% of current implementation during a 5-minute continuous compass session.
- N3: No regression in calibration accuracy compared to baseline (±3° tolerance).
- N4: Preserve Kotlin/Compose style guidelines and existing architecture (MVVM with repositories).

## 7. Success Metrics
- **Latency:** Time from physical rotation (sensor event) to rendered heading change decreases by ≥40% on test low-end device.
- **Stability:** Heading variance at rest remains ≤ current baseline.
- **User Sentiment:** Support ticket volume for “compass lag” drops within one release cycle.
- **Performance:** UI jank metric (<1%) recorded in Android Studio profiler during usability tests.

## 8. Proposed Solution Overview

### 8.1 Sensor Pipeline Enhancements
- Start a dedicated `HandlerThread` when `getOrientationFlow()` activates. Register all sensor listeners using this thread’s `Looper`. Ensure thread teardown in `awaitClose`.
- Request `SENSOR_DELAY_GAME` for rotation vector, magnetometer, and accelerometer. Detect vendor-imposed throttling via timestamp deltas; fall back to `SENSOR_DELAY_UI` or custom period if necessary.
- Implement adaptive smoothing: compute `deltaT` from successive event timestamps. Use an exponential moving average with `alpha = deltaT / (timeConstant + deltaT)`, where `timeConstant` dynamically ranges (e.g., 100–250 ms) based on recent heading variance. This keeps responsiveness high when movement is fast while smoothing noise when stationary.
- Maintain calibration offset and declination application exactly as today.

### 8.2 UI Thread & Logging
- Emit refined `OrientationState.Available` updates on the main thread via flow emission, without heavy logging. Restrict Timber debugging to debug builds and aggregate once per second for diagnostics.

### 8.3 Rendering & Animation Strategy
- Replace the 300 ms linear tween with `animateFloatAsState` using a critically damped spring (`spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)`), which converges quickly without overshoot.
- Skip animation when heading delta < 1° to avoid micro-jitter; update immediately when delta ≥ 90° to prevent long catch-up sequences.
- Move frequent draw-time calculations into `drawBehind` or `Canvas` local state to minimize recomposition overhead.

### 8.4 Compatibility Adjustments
- Replace deprecated rotation APIs with `context.display?.rotation` (API 30+) and fallback to `DisplayManager` for earlier versions.
- Ensure background thread lifecycle handles app backgrounding to avoid leaks.

## 9. Dependencies & Resources
- Android SDK 34+ toolchain.
- QA devices: one flagship (baseline), one budget (e.g., Moto G series).
- Engineering effort: ~3 developer-days plus QA profiling.

## 10. Risks & Mitigations
- **Thermal throttling:** Faster sensor rates may trigger vendor throttling. Mitigate by monitoring `deltaT` and backing off automatically.
- **Increased noise:** Higher responsiveness can surface sensor noise. Adaptive smoothing mitigates; QA to validate edge cases (elevators, metal surfaces).
- **Thread lifecycle leaks:** Ensure `HandlerThread.quitSafely()` on flow close.

## 11. Launch Plan
1. Implement adaptive sensor and threading changes behind a feature flag (compile-time constant) for easy rollback.
2. Update automated tests and add instrumentation benchmark for heading latency.
3. Smoke test on QA devices; collect system traces.
4. Beta rollout (internal testers) focusing on low-end hardware.
5. Full release with changelog entry highlighting smoother compass performance.

## 12. Open Questions
- None. Product approved auto-adjust strategy and background thread usage.

## 13. References
- Android Sensor Overview: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
- SensorManager API reference: https://developer.android.com/reference/android/hardware/SensorManager
- Jetpack Compose Performance Best Practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose Animation Guidelines: https://developer.android.com/jetpack/compose/animation

