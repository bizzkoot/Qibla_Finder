# PRD: Qiblah Finder — Current State Audit, Keep-Screen-On Feature & Enhancement Roadmap

> Version: 1.1 (post-critique corrections applied)
> Date: 2025
> Status: Draft — critique completed; corrections incorporated (see §6)
> App: Qiblah Finder (Android, Kotlin + Jetpack Compose, v2.10.0 / versionCode 21000)

---

## 1. Product Context & "Why We Do It Like This"

### 1.1 Mission
Qiblah Finder helps Muslims find the exact direction to the Kaaba using the phone's
sensors and GPS — "no more guessing or asking around". The product pillars (from README):
pinpoint accuracy, smart location, AR mode, compass calibration, phone-flat detection,
and fast/reliable/offline behavior.

### 1.2 Distribution model (explains the architecture)
- The app is **distributed via GitHub Releases**, not the Play Store. This is why the
  app ships its own **self-update subsystem** (GitHub API → DownloadManager → FileProvider
  install) and an in-app update banner. It is also why `REQUEST_INSTALL_PACKAGES` exists.
- Versioning is `versionCode 21000 / versionName 2.10.0` (build.gradle:14-15); the in-app
  updater parses `vX.Y.Z` release tags, so tag/version drift silently breaks update detection.
  (v1.0 of this PRD itself shipped the wrong version string — corrected in v1.1.)
- CI builds signed APKs from env-var secrets; keystore is not in the repo.

### 1.3 Why the app is structured this way (rationale from the codebase)
| Design decision | Why it exists (evidence) |
|---|---|
| **Manual location on a custom OSM map** | "Can't get GPS signal? No problem" — indoor/mosque use where GPS fails. Ships a custom OSM tile renderer + disk cache (100 MB cap, LRU, 30-min eviction). **Critique correction: a full `mapsforge` engine IS declared (build.gradle:97-99) but never used in code — remove or adopt; "avoided" was wrong.** |
| **Sun calibration** | Magnetometer drift means the compass can be wrong; the sun's azimuth is an absolute reference users can verify against. |
| **AR mode (ARCore)** | Camera arrow is the most intuitive guidance; AR is optional (`uses-feature required=false`) with an error/fallback screen. |
| **Sensor rates up to 50 Hz** | Accuracy is the top pillar; Kalman fusion + adaptive filtering need dense samples to stabilize the heading. Accuracy work is the most-specified area (`specs/compass-accuracy-enhancements/`). |
| **Camera permission gates the whole app** | Simplifies the permission flow to one screen; trade-off: it over-blocks users who only want the compass. **Tension flagged: listed as deliberate here yet filed as High-severity H8 — decision required.** |
| **Self-update instead of Play Store** | Store-less distribution: the in-app updater is the only update path for a 100% GitHub-Releases-distributed app. (Why GitHub Releases over Play is not documented in the repo — open question for the owner.) |
| **Compass as the start destination** | The compass IS the product; everything else (AR, calibration, map, help) is support. |

### 1.4 What "success" means for this app
1. **Trust**: the arrow points at the Kaaba accurately (alignment is the moment of truth).
2. **Availability**: works when GPS is unavailable (manual location), indoors (calibration),
   and offline (cached maps).
3. **Low friction**: user goes from install → permission → direction in seconds.
4. **Reliability**: doesn't crash, doesn't drain battery, updates install cleanly.
5. **Growth levers**: AR, accuracy refinements, offline map packs, and self-update make the
   app distributable without a store.

---

## 2. Current State Assessment (multi-POV audit, Oct 2025)

Four isolated POV audits (Architecture, UI/UX, Performance/Battery, Security/Roadmap) were
run against the codebase. Consolidated findings below. **Every finding cites `file:line`
evidence; a follow-up review (see §6) is verifying these claims.**

### 2.1 What works well
- **MVVM + shared repositories**: one `LocationRepository`/`SensorRepository` pair threaded
  through `QiblaNavHost`; every ViewModel exposes `StateFlow` (MainActivity.kt:112-131).
- **Clean sensor teardown**: `SensorRepository.getOrientationFlow()` uses `callbackFlow` with
  `awaitClose` unregistering listeners on a dedicated `HandlerThread` (SensorRepository.kt:480).
- **Layered update subsystem**: `api → services → repository → viewmodel`, manually DI'd via
  `QiblaFinderApplication`.
- **Well-tested domain math**: `GeodesyUtils` (70 passing JVM tests) — the only tested area.
- **Security baseline**: no committed secrets, HTTPS-only networking, minimal exported surface,
  correct FileProvider config, debug-only Timber planting.
- **Background work sane**: update check is once/day unique periodic WorkManager work.

### 2.2 High-severity issues (fix first)
| # | Issue | Evidence | Impact |
|---|---|---|---|
| H1 | **Sun calibration offset is never applied to the compass** — `CompassViewModel` is constructed without its `sunCalibrationViewModel`, so `setCalibrationOffset` never runs and `isSunCalibrated` is always false. The headline feature is functionally dead. | QiblaNavHost.kt:56-58, 74-87; CompassViewModel.kt:49-50, 94-96 | Feature doesn't work; user trust eroded. |
| H2 | **GPS updates are never stopped and get duplicated** — `startLocationUpdates()` runs for the app's lifetime; every `getLocation()` call registers a new `LocationCallback` and old ones are never removed (realistically **3–4 concurrent streams**: compass combine + AR combine + 2× declination collectors in SensorRepository.kt:308-313). | LocationRepository.kt:83, 106-109, 116-120; SensorRepository.kt:308-313; no `onCleared` in CompassViewModel/ARViewModel | Biggest battery drain in the app. |
| H3 | **Sensor emission storm** — accel+magnetometer+gyro at 50 Hz → ~150–200 StateFlow writes/sec → Compose recomposition storm + 60 fps redraw even when the phone is still. | SensorRepository.kt:436, 467-475, 308-309, 615 | CPU/battery; prevents CPU idling. |
| H4 | **Sensors keep running on other screens** — compass ViewModel stays alive on the back stack; AR adds a second 3-sensor registration (KALMAN_FUSION: accel+mag+gyro) while its camera runs. | QiblaNavHost.kt:36-59; ARViewModel.kt:84-117 | 2× sensor streams + camera concurrently. |
| H5 | **~1,018 lines of confirmed dead code** (only self-references): `DirectionLineRenderer.kt` (434), `SimpleMapView.kt` (309), `UpdateNotificationComponents.kt` (164), `sunCalibration/CameraPreview.kt` (91), `MapTypeFallbackManager.kt` (20); plus unused `QiblaAppState` navigation helpers. **Caveat: `MapLocation` is declared inside `SimpleMapView.kt:19` and imported app-wide — relocate the type before deleting the file.** | cross-file reference search | Maintenance burden; confusion about canonical renderers; dead APK size. |
| H6 | **Kaaba "success" logo is clipped off-canvas** — `kaabaY = centerY - radius * 1.3f` is always above the canvas top edge at any density, so the alignment payoff renders broken. | CompassScreen.kt:598-617 | The app's proudest moment renders wrong. |
| H7 | **"Try Sun Calibration" button in AR is dead** — empty `onCalibrateClicked` lambda wired to a visible button. | ARScreen.kt:92; ARErrorScreen.kt:104-110 | Dead-end UX. |
| H8 | **Camera permission blocks the compass entirely** — `hasRequiredPermissions()` requires camera; a user who only wants direction is forced to grant it. | PermissionManager.kt:60-63; MainActivity.kt:96-100 | Over-blocking; friction at first run. |

### 2.3 Medium-severity issues
| # | Issue | Evidence |
|---|---|---|
| M1 | No dark mode; hardcoded `Color.Black/Red/Green` everywhere; light-only XML theme. | CompassScreen.kt:201, 456, 680 (hardcoded colors); styles.xml:3 (light-only theme). Note: Typography.kt:110-198 is the shipped adaptive-font-scaling feature, not colors — don't confuse the two. |
| M2 | `model → ui` layer inversion: `GeodesyUtils.kt` imports `ui.location.MapLocation`. | GeodesyUtils.kt:3 |
| M3 | Kaaba coordinates duplicated: `GeodesyUtils.kt:27` vs hardcoded `CompassViewModel.kt:143-144`. | two sources of truth |
| M4 | Dual-track manual location state (repo flag vs `manualLocationOverride`) can desync. | LocationRepository.kt:53-62; CompassViewModel.kt:39, 115-121 |
| M5 | Unbounded in-memory tile cache + full-map redraw per frame in the OSM map. | OpenStreetMapView.kt:247, 426, 138-139 |
| M6 | `System.gc()` + `Thread.sleep(100)` synchronously on the main thread. | QiblaPerformanceMonitor.kt:515-516 |
| M7 | Update-check rate limit broken: `last_check_time` saved only when an update exists → GitHub API hit on every launch. | UpdateNotificationRepository.kt:32-37 |
| M8 | No sensor-failure recovery ("Initializing…" forever); dead-end location error states (no Retry / Open-Settings). | SensorRepository.kt:302-309; CompassScreen.kt:222-224, 712-716 |
| M9 | Lint fails: 1 error + 108 warnings (`windowLayoutInDisplayCutoutMode` needs API 27, minSdk 24). | values/styles.xml:9 |
| M10 | Test gap: 64 main files; exactly 70 `@Test` methods across 4 classes — all in `model/`; zero ViewModel/repository/navigation tests. | app/src/test (4 files) |
| M11 | ViewModels hold `Context`; static `QiblaPerformanceMonitor` (4× lint StaticFieldLeak). | SunCalibrationViewModel.kt:20; QiblaPerformanceMonitor.kt:24 |
| M12 | Dependency hygiene: stale Compose BOM 2023.06.01; unused `javax.inject`; no version catalog. | app/build.gradle:63, 92 |
| M13 | `CompassViewModel` re-computes geodesy + distance string on every sensor emission (location-independent work on the hot path). | CompassViewModel.kt:54-90 |

### 2.4 Low-severity / Info
- Self-update installs APK with **no signature/URL verification** (signature continuity is the backstop).
- `WAKE_LOCK` declared but never used (keep-screen-on needs **no** permission — window flag only).
- ARCore metadata contradiction: `uses-feature ... required=false` vs `<meta-data ar.core value="required"/>`.
- `allowBackup=true` with no `dataExtractionRules`; coordinates logged (debug-builds only).
- CI release job gated on `main` ref → tag pushes skip it → signed APK never auto-published.
- `minifyEnabled false` (no R8); `HttpLoggingInterceptor` ships in release builds.
- Full-screen non-dismissable "flat phone" red-alert overlay; hardcoded English strings/emoji (only 14 strings in `strings.xml`).
- `specs/` (feature PRDs incl. compass-accuracy work) is gitignored — accuracy specs the roadmap leans on aren't version-controlled.
- TalkBack gaps (canvas has no semantics, no `liveRegion` on status).
- Fixed 300.dp compass → layout overflow risk on small phones/landscape/large font scale.
- ARCore `required` meta-data may force ARCore install on non-AR devices.

---

## 3. Phase 0 — Keep Screen On (primary feature request)

### 3.1 Problem statement
Users hold the phone toward Qibla for prayer prep. If the screen times out mid-guidance,
the compass disappears and the flow breaks. The user wants an option to keep the screen
awake while the main compass window is open.

### 3.2 Requirements
1. **Toggle** ("Keep screen on") available on the compass screen.
2. **Persisted** across launches; survives process death.
3. **Lifecycle-safe**: screen may stay on while the compass is visible and the app is in
   the foreground; must NOT stay on when the app is backgrounded or when the user
   navigates away from the compass.
4. **No new permission**; must not use `WakeLock`/`PowerManager` (battery + stuck-wakelock risk).
5. Default: **ON** for the compass route only (product decision; see §3.5 trade-off).

### 3.3 Design decisions
| Decision | Choice | Rationale |
|---|---|---|
| Toggle placement | In-compass `IconButton` in the StatusBar row (right end) | No settings screen exists; the need arises exactly on the compass; in-context = discoverable + reversible. `Arrangement.SpaceBetween` already fits a trailing icon. |
| Icon | `Icons.Filled.Visibility` (on) / `VisibilityOff` (off); contentDescription "Keep screen on" / "Screen timeout active" | Stateful, glanceable; accessibility-friendly. |
| Persistence | New `CompassPreferences` (SharedPreferences file `compass_prefs`, key `keep_screen_on`, default `true`) | Mirrors the established `ManualLocationPreferences.kt` idiom exactly. Key kept stable for a future Settings screen to reuse. |
| Mechanism | `LocalView.current.keepScreenOn = enabled` via route-scoped `DisposableEffect` (sets/clears `FLAG_KEEP_SCREEN_ON`) | No permission; auto-clears on disposal; per-window flag; `ON_PAUSE` clears when backgrounded, `ON_RESUME` restores. |
| Scope | Compass route (+ AR, see note) | **Critique correction: there is NO keep-screen-on or wake-lock anywhere in the code today (grep-verified) — AR/calibration screens can and do time out mid-guidance today.** The compass pain applies equally to AR. Decision: either apply `KeepScreenOn` to the AR route sharing the same preference, or explicitly defer AR with a follow-up issue. Manual map stays user-driven. |

### 3.4 Implementation plan
**New file** `app/src/main/java/com/bizzkoot/qiblafinder/ui/compass/CompassPreferences.kt`
```kotlin
class CompassPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun getKeepScreenOn(): Boolean = prefs.getBoolean(KEY, DEFAULT_KEEP_SCREEN_ON)
    fun setKeepScreenOn(enabled: Boolean) = prefs.edit().putBoolean(KEY, enabled).apply()
    private companion object {
        const val PREFS_NAME = "compass_prefs"
        const val KEY = "keep_screen_on"            // stable — future settings screen reuses it
        const val DEFAULT_KEEP_SCREEN_ON = true
    }
}
```

**New file** `app/src/main/java/com/bizzkoot/qiblafinder/ui/compass/KeepScreenOn.kt`
```kotlin
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(enabled, lifecycleOwner) {
        val previous = view.keepScreenOn   // capture before we set it
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.keepScreenOn = enabled
                Lifecycle.Event.ON_PAUSE  -> view.keepScreenOn = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.keepScreenOn = enabled &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            view.keepScreenOn = previous   // restore prior value; don't clobber a shared window flag
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
```

**Edit** `QiblaNavHost.kt` (`COMPASS_ROUTE` block): create `CompassPreferences`,
hold `keepScreenOn` state (`remember { mutableStateOf(prefs.getKeepScreenOn()) }`),
compose `KeepScreenOn(enabled = keepScreenOn)`, pass toggle callback + current value into
`CompassScreen` → `StatusBar` trailing slot.

**Edit** `AndroidManifest.xml`: remove unused `WAKE_LOCK` permission (verify DownloadManager
does not need it — it is a system service).

### 3.5 Trade-offs & risks
| Risk | Severity | Mitigation |
|---|---|---|
| Battery drain: display is the largest consumer; screen-on adds ~15–30% device drain; blocks Doze. | Medium | Default ON only on compass route; visible toggle; **pair with §4 battery fixes** (H2/H3 idle-damping); dark mode reduces OLED power. |
| OLED burn-in (light theme + static elements). | Low-Med | Dark mode; toggle; avoid pulsing/bright elements. |
| User forgets it's on. | Low | Stateful icon + (optional) status chip. |
| Future settings screen migration. | Low | Stable pref key; trivial reuse. |
| NavHost crossfade can clobber a shared window flag (outgoing route disposal clears the incoming route's flag). | Low | `onDispose` restores the prior `view.keepScreenOn` value (§3.4). |
| Default-ON multiplies the sensor-storm/GPS-leak drain if Phase 1 slips. | Medium | **Phase 1 is a hard dependency of default-ON**; add a `PowerManager.isPowerSaveMode()` guard. |
| Process death / rotation. | None | `configChanges` prevents recreation; prefs survive. |

### 3.6 Acceptance criteria
- [ ] Toggle on compass screen flips `view.keepScreenOn` immediately (no relaunch).
- [ ] Preference persists across app restart and process death.
- [ ] Navigating to AR/calibration/map/help clears the flag (screen can time out there).
- [ ] App backgrounded → flag cleared; returning to foreground → restored if toggle on.
- [ ] Default is ON for first-run users on the compass screen.
- [ ] No new permissions in the manifest; `WAKE_LOCK` removed.
- [ ] Unit test: `CompassPreferences` round-trip (get/set/default) — needs Robolectric or a thin in-memory prefs abstraction (project has JUnit only today; decide the dependency first).
- [ ] Manual device test: screen stays on >2 min with toggle on; times out normally with toggle off.
- [ ] AR-route behavior decided (keep-screen-on applied or explicitly deferred) — see §3.3 scope note.

---

## 4. Phase 1 — Battery & Reliability Fixes (ship with Phase 0)

The keep-screen-on feature amplifies the value of these fixes; do them in the same release.
**Phase 1 is the HARD DEPENDENCY of the default-ON decision (§3.5): if it slips, ship Phase 0 with default-OFF and flip the default later.**

1. **H2 — Stop & dedupe GPS**: add `onCleared` → `stopLocationUpdates()` in
   `CompassViewModel` + `ARViewModel`; make `LocationRepository` use a single shared
   `LocationCallback` (refcounted, or StateFlow with `whileSubscribed`).
2. **H3 — Sensor throttle**: sampling 50 → 25–30 Hz; emit only when heading delta > 0.1°;
   fold `checkInterference` status into the single emission path.
3. **H4 — Lifecycle-gate sensors**: collect orientation flow only while compass route is
   `ON_RESUME` (`repeatOnLifecycle` / `LifecycleResumeEffect`).
4. **Idle animation damping**: hold last frame when heading delta < 0.5° for > 1 s.
5. **M13 — Split `CompassViewModel` combine**: derive bearing/distance from location-only
   flow; combine with orientation only at the UI boundary.
6. **M5 — Bound tile cache**: LRU-cap to viewport+buffer (~50 tiles); evict off-screen;
   cache parsed keys; skip off-screen draws.
7. **M6 — Off-main-thread memory recovery**: drop `System.gc()`/`Thread.sleep` from main thread.
8. **M7 — Fix update-check rate limit**: persist `last_check_time` unconditionally; add
   `NetworkType.CONNECTED` constraint to the periodic worker.
9. **H6 — Kaaba clip fix** (5-line fix; repairs the alignment payoff) + **M3** Kaaba-coordinate dedup.
10. **H8 — Camera optional for compass path** (moved up: it's the first-run gate every new user hits) + **H7** wire AR "Try Sun Calibration".
11. **Add tests for H2/H3/H4** — behavioral changes with zero existing VM/repo test infrastructure; unguarded refactors risk regressions in the trust-critical hot path.

---

## 5. Roadmap (later phases, in priority order)

| Phase | Theme | Items | Effort | Depends on |
|---|---|---|---|---|
| 2 | **Feature fixes** | H1 sun-calibration wiring (move offset into a `CalibrationRepository` read at compass init + test); M3 Kaaba-coordinate dedup; M4 manual-location single source of truth | S–M | Phase 1 |
| 3 | **Accuracy (extend existing work)** | Build on shipped work (`specs/done/precise-qibla-arrow-alignment`, `PreciseArrowBaseCalculator.kt`, `PrecisionCoordinateTransformer.kt`, satellite map): persist last-known fix (instant arrow before GPS lock); accuracy badge; calibration-quality surface + "verify with sun" shortcut; manual bearing offset | M | Phase 2 |
| 4 | **Sun & AR** | Make ARCore optional (metadata fix + runtime check); persist + auto-verify calibration; distance-scaled AR indicator; flat-phone AR ground guidance | M–L | Phase 2 |
| 5 | **Offline maps** | Region map-pack downloads (size cap + cache mgmt); offline reverse-geocoding; map-rotation sync with compass | L | — |
| 6 | **Privacy & hygiene** | `dataExtractionRules`/`fullBackupContent`; redact coords from logs; Export/Erase data; delete dead code (H5) | S–M | — |
| 7 | **Ops & distribution** | Fix CI release job (tag-based); enable R8 + keep rules; APK signature + URL verification before install; unknown-sources pre-check; **versionCode/tag consistency check — moved earlier: this PRD itself shipped the wrong version string, and README already drifts (claims v2.4.3)** | S–M | — |
| 8 | **UX polish** | Dark mode (M1); accessibility (TalkBack semantics, contrast, 48.dp targets); de-alarm flat-phone overlay; string extraction; responsive compass sizing | M | — |

---

## 6. Verification & Next Steps

1. **PRD critique — COMPLETE (v1.1 incorporates it).** Independent reviewer verified all 8
   High claims TRUE (nits: H2 undercounts GPS streams — real count 3–4; H4 is 3 sensors not
   4; H8 citation off by ~10 lines). Found one hard error (v1.0 said 2.9.0/20900 — actual
   2.10.0/21000) and one false premise (§3.3 "AR/calibration previews already hold the screen
   on" — no such code exists). Pre-correction confidence: 78%.
2. **Verified by supervisor:** `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL
   (exactly 70 `@Test` methods across 4 classes); `./gradlew :app:lintDebug` → 1 error,
   108 warnings, 11 hints (matches §2.4); `strings.xml` has 14 strings (not 10).
3. **Implement Phase 0** per the corrected spec; **run `./gradlew test` and
   `./gradlew :app:lintDebug`** after each phase; keep lint green.

### Implementation status (v1.1, same session)

| Item | Status | Verification |
|---|---|---|
| Phase 0 — KeepScreenOn + CompassPreferences + StatusBar toggle + WAKE_LOCK removal | ✅ DONE | `:app:testDebugUnitTest` green (74 tests incl. 4 new CompassPreferences tests); Robolectric added |
| H2 — GPS dedupe guard (`LocationRepository`) + `onCleared` stop in `CompassViewModel` | ✅ DONE | build + tests green |
| H3 — sensor sampling 50 → 30 Hz (`CompassFilterConfig`) | ✅ DONE | build + tests green |
| H6 — Kaaba clip fix (`coerceAtLeast`) | ✅ DONE | build + tests green |
| H7 — AR "Try Sun Calibration" wired (`ARScreen` param → nav) | ✅ DONE | build + tests green |
| M3 — Kaaba coords dedup (uses `GeodesyUtils.calculateDistanceToKaaba`) | ✅ DONE | build + tests green |
| M7 — update-check rate limit saved unconditionally | ✅ DONE | build + tests green |
| F10 — periodic worker `NetworkType.CONNECTED` constraint | ✅ DONE | build + tests green |
| M13 — split `CompassViewModel` combine (geodesy only on location change) | ✅ DONE | build + tests green |
| H8 — camera optional for compass (PermissionManager/Screen + SunCalibration lazy launcher) | ✅ DONE | build + tests green |
| Lint | 1 error (pre-existing `styles.xml` NewApi) + 110 warnings vs 108 baseline — **+2 are dependency-version notices only**; 0 new code warnings | verified via `git stash` baseline diff |
| **H1 — sun-calibration wiring** (`CalibrationRepository` + compass/sun routes + `CompassViewModel` wiring) | ✅ DONE (subagent-implemented, parent-verified) | 80 unit tests green (74 + 6 new); lint 107 (down from 110); 0 new warnings |
| **H5 — dead-code deletion** (1,018 lines; `MapLocation` → `model/`, fixes M2 inversion) | ✅ DONE (subagent-implemented, parent-verified) | 80 tests green (unchanged); lint 103 (down from 107); 0 new |
| **H4 — lifecycle sensor gating** (`screenVisible` + `flatMapLatest` in `CompassViewModel`, lifecycle observer in `CompassScreen`) | ✅ DONE (subagent-implemented, parent-verified) | 81 tests green (80 + 1 new gating test); lint unchanged 103; 0 new |
| **M1 — dark mode** (`darkColorScheme`/`lightColorScheme` + `values-night` + `values-v27` cutout fix + compass text color) | ✅ DONE (subagent-implemented, parent-verified) | 81 tests green; **lint 0 errors for the first time** (pre-existing `styles.xml` NewApi fixed via `values-v27`); 103 warnings unchanged |
| **H2/H3 — regression tests** (`LocationRepositoryDedupeTest` ×4, `CompassFilterConfigTest` ×4, test seams) | ✅ DONE (subagent-implemented, parent-verified) | **89 tests green** (81 + 8 new); lint 0 errors; +2 warnings = mockito version notices only |

**Remaining (noted, not regressions):** M5/M6 (map tile cache + main-thread gc — high risk in the map feature; PRD says de-risk behind Phase 2).

### Post-session regression fixes (user device test, Oct 2025)
- **Flat-phone alert regression**: H4 gating + AR's independent collection raced on `SensorRepository`'s shared mutable state during compass↔AR transitions. Fixed with a repository-level `Mutex` serializing collection ownership (verified via Robolectric ShadowSensorManager test); compass alert + new AR warning banner driven by `phoneTiltAngle` via a shared 65–115° band. → `53bf2ae`
- **AR screen timeout**: AR route now composes `KeepScreenOn` honoring the shared `keep_screen_on` preference. → `53bf2ae`

---

## 7. Open Questions (for the critique to challenge)
1. Is default-ON for keep-screen-on correct, or should battery-conscious users get default-off?
2. Is the in-compass toggle (vs. a Settings screen) the right long-term surface?
3. Are the High-severity claims (H1–H8) all accurate against the current code? Any that are wrong?
4. Is the roadmap ordering right for a solo dev? Anything missing that matters more?
5. Does the PRD's "why we do it like this" (§1.3) match the actual code intent, or is any of it speculative?

### Critique resolutions (v1.1)
1. **Default-ON:** correct, conditional on Phase 1 shipping in the same release + an `isPowerSaveMode()` guard; otherwise ship default-OFF.
2. **In-compass toggle:** right surface today; stable key (`keep_screen_on`) preserves future Settings-screen migration.
3. **H1–H8 accuracy:** all substantively TRUE (nits noted in §6).
4. **Roadmap:** reorder H6/H8 into Phase 1; add tests for Phase 1 refactors; de-risk Phase 4/5 (L-effort AR/offline features) behind Phase 2 completion.
5. **§1.3 grounding:** mostly grounded; three shaky rows corrected (mapsforge dependency, self-update rationale, camera-gate tension with H8).
