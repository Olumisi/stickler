# Stickler

Android app (phone + Wear OS) that keeps a planned task in front of the user from its scheduled time until they mark it Done. Full spec: `docs/SPEC.md` — read it before planning any feature. If a request conflicts with the spec, say so before coding.

## Stack
- Kotlin, Jetpack Compose (phone), Compose for Wear OS + Tiles (watch)
- Room (local DB), AlarmManager exact alarms, NotificationCompat
- Wearable Data Layer API for phone ↔ watch sync
- MVVM + repository, Hilt for DI
- No backend, no analytics, no network calls in v1 (keeps the Play Data safety form simple)

## Modules
- `:core` — task model, Room DB, reminder engine (scheduling, escalation, snooze logic)
- `:app` — phone UI and notifications
- `:wear` — watch UI, ongoing activity, tile

## SDK levels
- minSdk 31 (Android 12), targetSdk/compileSdk 37 (Android 17)
- `:wear` minSdk 33 (Wear OS 4); it can't go below `:core`'s 31, and no Wear OS release uses API 31/32
- SDK levels live in `gradle/libs.versions.toml` (`compileSdk`, `targetSdk`, `minSdk`, `wearMinSdk`)
- Before Phase 1, check Android 17 behavior changes that affect notifications, alarms and foreground services
- Always branch on `Build.VERSION.SDK_INT` for version-specific behaviour (see spec: "Behaviour by Android version")

## Commands
CLI builds need a JDK: if `JAVA_HOME` isn't set, use Android Studio's bundled one (`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`).
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

## Rules
- Reminder behaviour (escalation ladder, snooze limits, re-post on dismiss, Missed and the automatic handover to the next task) lives in `:core` and must have unit tests. Don't change it without updating tests and the spec.
- Never remove or bypass a permission check (`canScheduleExactAlarms()`, `canUseFullScreenIntent()`, `POST_NOTIFICATIONS`). Degrade gracefully when a permission is missing.
- Don't add `USE_EXACT_ALARM` or `SYSTEM_ALERT_WINDOW` to the manifest (Play policy decision in spec).
- Store times in UTC; convert to local time only for display.
- Snoozes per task come from the single app-wide setting (default 2). No daily cap.
- Work in small slices: one feature, tests passing, then commit with a clear message.
- Ask before adding a new dependency.

## Workflow
1. Plan first (plan mode) and wait for approval.
2. Implement one slice.
3. Run build + tests; fix failures.
4. Summarize what changed and what to test manually on the phone.
