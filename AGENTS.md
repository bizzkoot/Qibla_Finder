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

**Ordering invariant**: tests, lint, and `assembleRelease` run **before** the version-bump commit
and tag are pushed, so a failing build can never leave an orphaned version bump on `main`.
`android.yml` no longer owns releasing; `release-drafter.yml` is the only release path and it
always creates **draft** releases.

When `[release]` is present, the workflow still auto-bumps, tags, builds, and drafts — no
human-in-the-loop steps inside the workflow:
- **The bump is driven ONLY by each commit's subject-line type prefix — commit bodies are
  never scanned.** Bump severity is read from the **subject** (`git log --format=%s`) of every
  commit in the push range (`before..after`), taking the highest severity — not just HEAD.
  - `breaking:`/`major:` (or any type with the `!` breaking marker, e.g. `feat!:` / `fix(api)!:`) → **major**;
  - `feat:`/`feature:`/`enhancement:` → **minor**;
  - everything else — `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, `perf:`, `ci:`, `build:`, `revert:` → **patch**.
  - **A `chore:`/`fix:`/`docs:` commit can never bump major or minor.** Prose in a commit body
    (or a non-type-prefix mention of "breaking"/"major") is ignored, so it cannot accidentally
    trigger a big version bump.
- It edits `versionName`/`versionCode` in `app/build.gradle`, commits `chore: bump version to X [skip ci]`,
  tags `vX.Y.Z`, builds a signed release APK, and uploads it as a **draft** release.
- Releases are created as **DRAFTs**: a human must publish the draft on GitHub. Until published,
  the in-app updater won't see the release (draft assets 404 publicly).
- `./gradlew test` and `./gradlew lintDebug` run before `assembleRelease`; if either fails the job
  fails and no release is created.
- `versionCode` must equal `major*10000 + minor*100 + patch` (currently `21003` / `"2.10.3"`).
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
- `PRD.md` is a **local-only spec-of-record** (untracked via `.git/info/exclude`; kept out of git intentionally — milestones (M) and hardening items (H1–H8) live there, but the file itself is not version-controlled). `specs/` is gitignored (local spec-driven workflow, see
  `CLAUDE.md`). `docs/technical/Architecture.md` exists but is partially stale (references a renamed `QiblaViewModel`).

## Conventions & Testing
- Kotlin, 2-space indentation; idiomatic style. Conventional Commits are required (see
  `docs/development/COMMIT_CONVENTIONS.md`).
- Unit tests: JUnit4 in `app/src/test/java`, mirroring `model/` and `ui/compass/`. Robolectric +
  Mockito available. Domain math (`GeodesyUtils`, compass filtering) is well covered; `ui/location`
  and `update/` currently have no tests, while `sunCalibration/` (calibration math + lifecycle
  gating) and location-repository GPS gating are now covered.
- CI runs `./gradlew test`; `connectedAndroidTest` is `continue-on-error` (no device in CI).
