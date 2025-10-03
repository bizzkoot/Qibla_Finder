# Compass Performance TODO

- [x] Create sensor `HandlerThread` in `SensorRepository.getOrientationFlow` and register listeners with it; ensure safe teardown in `awaitClose`.
- [x] Request fast sampling (`SENSOR_DELAY_GAME`) with fallback logic based on measured timestamps.
- [x] Implement adaptive smoothing that derives `alpha` from event interval and heading variance; expose constants for tuning.
- [x] Throttle Timber logging in `SensorRepository` and `CompassGraphic`; disable per-frame logs in release builds.
- [x] Update compass animation to spring-based `animateFloatAsState`, skip animation for tiny deltas, and fast-path large jumps.
- [x] Replace deprecated display rotation API usage with modern equivalents.
- [x] Add instrumentation/unit coverage for smoothing math and large heading jumps.
- [ ] Manually profile on low-end device (or emulator) and capture trace notes for release checklist.
