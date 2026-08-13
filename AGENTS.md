# Repository Guidelines

Android app (Kotlin + Jetpack Compose, Material 3) that finds the direction to the Kaaba
using GPS, compass, AR, and sun calibration. Single `app` module, distributed via GitHub
Releases (no Play Store) with a built-in self-update subsystem.

## Build, Test, and Lint Commands
- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew test` (all) or `./gradlew :app:testDebugUnitTest` (debug module)
- Single test: `./gradlew :app:testDebugUnitTest --tests "com.bizzkoot.qiblafinder.model.GeodesyUtilsTest"`
- `./gradlew :app:lintDebug` (or `./gradlew lint`)
- `bash install-debug.sh`: install debug APK on a connected device (needs `adb`)
- Toolchain: Gradle 8.13, AGP 8.13.0, Kotlin 1.9.0, Groovy DSL (`build.gradle`, not `.kts`),
  JDK 17 (pinned in CI). compileSdk/targetSdk 34, minSdk 24. `RepositoriesMode.FAIL_ON_PROJECT_REPOS`
  — you cannot add repositories in a module build file.

## Release Automation — Read Before Committing
> **Never `git push` to any remote unless the user explicitly asks you to.** A single push
> can trigger CI, a version bump, and a public release — always wait for explicit instruction.

The Release Drafter workflow (`.github/workflows/release-drafter.yml`) triggers **only** on a push
to `main` whose HEAD commit message contains the literal string `[release]`. Any other push
(including plain `chore: bump...` commits) runs the workflow and exits green without releasing.
The old `pull_request` trigger was removed.

When `[release]` is present, the workflow still auto-bumps, tags, builds, and drafts — no
human-in-the-loop steps inside the workflow:
- `feat`/`feature`/`enhancement` → minor bump; `fix`/`bug`/`patch` or any other commit → patch;
  `breaking`/`major` → major bump. Format: `type(scope): description`.
- The bump type is computed from the **full commit range of the push** (`before..after`), taking the
  highest severity across all commits — not just the HEAD commit.
- It edits `versionName`/`versionCode` in `app/build.gradle`, commits `chore: bump version to X [skip ci]`,
  tags `vX.Y.Z`, builds a signed release APK, and uploads it as a **draft** release.
- Releases are created as **DRAFTs**: a human must publish the draft on GitHub. Until published,
  the in-app updater won't see the release (draft assets 404 publicly).
- `./gradlew test` and `./gradlew lintDebug` run before `assembleRelease`; if either fails the job
  fails and no release is created.
- `versionCode` must equal `major*10000 + minor*100 + patch` (currently `21002` / `"2.10.2"`).
  The in-app updater parses `vX.Y.Z` release tags, so versionName/tag drift silently breaks update detection.
- `assembleRelease` requires `app/qiblafinder-release-key.jks` (gitignored; recreated in CI from
  `SIGNING_KEY_BASE64`) plus env vars `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Locally it
  fails without these — use `assembleDebug` for day-to-day work.
- Never commit secrets; `.env` is gitignored and only for local development.

## Architecture & Structure
- MVVM with shared repositories: `model/LocationRepository` and `model/SensorRepository` are threaded
  through `navigation/QiblaNavHost`; manual DI in `QiblaFinderApplication` (no Hilt/Koin). ViewModels
  expose `StateFlow`.
- Feature packages: `model/` (domain + repos + geodesy/sensor math), `ui/{compass,location,ar,calibration,sunCalibration,permissions,troubleshooting}`,
  `sunCalibration/`, `update/`, `navigation/`, `permissions/`, `utils/`.
- `ui/location` is a **fully custom OSM tile renderer** (Compose Canvas) — `TileUrlProvider.kt`,
  `OpenStreetMapTileManager.kt`. The `org.mapsforge` deps in `app/build.gradle:108-111` are declared
  but unused; do not treat them as the map engine.
- `PRD.md` is the tracked spec-of-record: it lists milestones (M) and hardening items (H1–H8) that are
  marked implemented via `docs(prd):` commits. `specs/` is gitignored (local spec-driven workflow, see
  `CLAUDE.md`). `docs/technical/Architecture.md` exists but is partially stale (references a renamed `QiblaViewModel`).

## Conventions & Testing
- Kotlin, 2-space indentation; idiomatic style. Conventional Commits are required (see
  `docs/development/COMMIT_CONVENTIONS.md`).
- Unit tests: JUnit4 in `app/src/test/java`, mirroring `model/` and `ui/compass/`. Robolectric +
  Mockito available. Domain math (`GeodesyUtils`, compass filtering) is well covered; `ui/location`,
  `update/`, and `sunCalibration/` currently have no tests.
- CI runs `./gradlew test`; `connectedAndroidTest` is `continue-on-error` (no device in CI).
