# AGENTS.md

Purpose: reduce token/context usage while keeping implementation quality high for this repository.

## Project Snapshot

- Type: Android app (Kotlin + XML), single module `app`.
- Core feature: Home Assistant sensor polling -> Nothing Glyph Matrix rendering.
- Runtime model: configuration in `MainActivity`, execution in foreground service.
- Current package: `it.pytonballoon810.glyphha`.

## Context-First Rules

1. Read only what is needed for the user request.
2. Prefer targeted reads over full-file reads for large files.
3. Avoid reading generated folders unless debugging build outputs.
4. Do not inspect `app/build` or `.gradle` unless the task is explicitly build-artifact related.
5. Reuse this file as the first source of truth before exploring the codebase.

## High-Value Files (Read These First)

- `app/src/main/java/it/pytonballoon810/glyphha/MainActivity.kt`
: Frontend configuration flow and service control.
- `app/src/main/java/it/pytonballoon810/glyphha/GlyphSyncForegroundService.kt`
: Background polling, notification, and render orchestration.
- `app/src/main/java/it/pytonballoon810/glyphha/GlyphController.kt`
: Glyph matrix drawing logic (progress bar, arrow, completion icon).
- `app/src/main/java/it/pytonballoon810/glyphha/HomeAssistantClient.kt`
: HA state fetch + numeric parsing.
- `app/src/main/AndroidManifest.xml`
: Permissions, service registration, app entry.
- `app/build.gradle.kts`
: Namespace, applicationId, dependencies, minSdk/targetSdk.
- `app/src/main/res/layout/activity_main.xml`
: Main UI layout and hero section.

## Production Structure (Source of Truth)

- `app/src/main/java/it/pytonballoon810/glyphha/MainActivity.kt`
: UI composition and user actions only. It should collect inputs, persist config through `SensorMappingStore`, and dispatch service intents.
- `app/src/main/java/it/pytonballoon810/glyphha/SensorMapping.kt`
: Domain model layer (use cases, display modes, sensor mapping fields).
- `app/src/main/java/it/pytonballoon810/glyphha/SensorMappingStore.kt`
: Persistence boundary. All shared preferences keys and migration logic must stay centralized here.
- `app/src/main/java/it/pytonballoon810/glyphha/GlyphSyncForegroundService.kt`
: Runtime orchestration layer. Poll loop, notification state, mapping runtime state machines, and trigger-based rendering decisions.
- `app/src/main/java/it/pytonballoon810/glyphha/HomeAssistantClient.kt`
: Home Assistant transport and state parsing boundary.
- `app/src/main/java/it/pytonballoon810/glyphha/GlyphController.kt`
: Glyph rendering primitives and icon loading behavior.
- `app/src/main/assets/icons/13/` and `app/src/main/assets/icons/25/`
: Monochrome icon masks for predefined icon types. Keep both matrix variants in sync.

## Maintainability Rules

1. Keep UI code (`MainActivity`) declarative and thin. Business logic belongs in service/controller/store helpers.
2. Avoid duplicated mapping assembly logic. Reuse shared helper functions for mapping construction and validation.
3. All persisted schema changes must include backward-compatible migration logic in `SensorMappingStore`.
4. Runtime state decisions (completion/reset/turn-off/error trigger) must be isolated in service helpers with clear naming.
5. Any new predefined icon must include both 13x13 and 25x25 asset variants and selector labels.
6. Keep strings in `res/values/strings.xml`; avoid hardcoded user-visible text in Kotlin.
7. Preserve stable intent action constants for service commands unless an explicit migration is planned.

## Validation Checklist (Required)

1. `gradlew.bat assembleDebug`
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk` (when a device is connected)
3. `adb shell monkey -p it.pytonballoon810.glyphha -c android.intent.category.LAUNCHER 1` (when a device is connected)
4. confirm process alive with `adb shell pidof it.pytonballoon810.glyphha` when launch validation is requested

## Known Constraints and Invariants

- Foreground service is required for reliable background sync.
- Persistent notification must remain active while syncing.
- ADB may require `ANDROID_ADB_SERVER_PORT=5037` on this machine.
- Glyph SDK AAR is local: `app/libs/glyph-matrix-sdk-2.0.aar`.
- `minSdk` must stay compatible with the Nothing SDK requirement.
- Runtime guard allows sync only on Nothing Phone (3)/(4) matrix profiles (25x25 or 13x13).
- Progress mode behavior:
  - centered 3px bar
  - always-visible outline
  - constrained fill
  - moving arrow marker using custom pixel mask
  - optional secondary sensor text rendered under bar
  - secondary duration-like text is normalized (e.g. `0h 1m` -> `1m`)
  - overflow text uses billboard-style scrolling only when overflow exceeds a very small threshold
  - overflow scroll target speed is approximately 400 words per minute
  - completion blink mode and reset rules handled in service logic
- Generic tracking behavior:
  - supports progress or number/text render mode
  - supports configurable turn-off and reset values to control runtime tracking enablement
  - supports optional error sensor + trigger value that renders a selected error icon when matched
- Icon behavior:
  - predefined icons are loaded from monochrome bitmap mask files in `app/src/main/assets/icons/`
  - each predefined icon has dedicated variants for 13x13 (Phone 4) and 25x25 (Phone 3) matrix sizes
- Debug tab behavior:
  - current matrix payload panel shows only latest rendered data (no history)

## Efficient Workflow

1. Confirm request scope.
2. Open only the 1-3 files most likely affected.
3. Make minimal patch edits.
4. Run `assembleDebug` once after edits.
5. If device validation is requested, run install + launch via adb.
6. Summarize changed files and behavior deltas only.
7. After a task is fully finished and validated, always commit pending changes in git with a clear message.
8. After a task is fully finished and validated, automatically attempt flash validation on a connected device using install + launch commands.

## Mandatory Automation Rules

1. Every finished implementation must be committed automatically without waiting for an extra user prompt.
2. After finishing implementation and local build validation, automatically attempt device flash validation (`adb install -r ...` + launch command) when a device is available.
3. If no device is connected, report that flash was attempted but skipped due to no detected device.

## Commands (Reference)

- Build:
  - `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows)
- Install:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Launch:
  - `adb shell monkey -p it.pytonballoon810.glyphha -c android.intent.category.LAUNCHER 1`

## Update Policy (Mandatory)

This file must be updated whenever any of the following changes:

1. Package name, app id, namespace, module layout, or folder structure.
2. Foreground/background execution model.
3. Core rendering behavior (progress bar, arrow mask, completion behavior).
4. Build/dependency requirements (SDK versions, AAR path, permissions).
5. Primary entry points or high-value files listed above.

When changing architecture or behavior, update this file in the same commit as the code change.

## Agent Output Guidelines

- Prefer concise deltas over restating full architecture.
- Reference only files that changed.
- Include validation status (built, installed, launched) if run.
- Avoid repeating prior context that is unchanged.
