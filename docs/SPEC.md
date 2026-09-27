# Stickler — Android Spec

Sep 25, 2026 · @Misi

## Overview

Stickler keeps a planned task in front of you from its scheduled time until you mark it done. Reminders that fire once and vanish are easy to swipe away; this one persists on the phone and the watch.

- **Problem:** Good intentions planned ahead get forgotten once the day gets busy.
- **Target user (v1):** One person with an Android phone and, optionally, a Wear OS watch.
- **Core promise:** A task that is due stays visible and keeps nudging until you tap Done or Missed, or the next task becomes due.
- **Platform order:** Android phone + Wear OS first; iPhone + Apple Watch later.

## MVP scope

Version 1 is a single-user, offline app: plan a day, get persistent reminders, mark tasks done.

| In scope (v1) | Out of scope (later) |
| --- | --- |
| Create a task list for a time window (e.g. today) | Recurring tasks and templates |
| Set a time (and optional duration) per task | Accounts, login, cloud sync |
| Persistent reminder at each task's time | iPhone and Apple Watch |
| Escalating nudges until Done or Missed | Sharing tasks with family |
| Limited, visible snooze | Streaks, analytics, AI suggestions |
| Wear OS: see current task, tap Done or Missed | Calendar import |
| End-of-day review: done vs missed, with snooze counts | Location-based reminders |

## User stories and core flows

Six flows cover v1: plan, fire, escalate, resolve, hand over, review.

1. **Plan the day.** As a user, I add tasks with a title and time for a period (default: today), so my intentions are captured ahead.
   - Quick add: title + time picker; optional note and duration.
   - Reorder or edit any task before its time.
2. **Reminder fires.** At the task's time, the phone shows a persistent reminder and the watch vibrates.
   - The reminder cannot be swiped away; only Done, Missed or Snooze clears it.
3. **Escalation.** If I don't act, nudges repeat and get stronger.
   - Repeat interval and max intensity are user settings.
4. **Resolve.** I tap Done, or Missed if I can't achieve it (phone, lock screen, or watch), and the reminder clears everywhere.
   - Snooze pushes it by a set amount, up to a limit, and counts against the task.
   - If I tap Missed by mistake, I can undo it for 10 seconds.
5. **Hand over.** If I haven't resolved a task by the time the next task is due, the earlier task is marked Missed automatically and the new task's reminder takes over.
6. **Review.** At day's end I see what was done (including done late) and missed (and whether I marked it or it was handed over), plus how often I snoozed, and can roll unfinished tasks to tomorrow.

## Reminder behaviour design

A due task stays present on every surface, and a swipe-away comes back, until Done. Android no longer lets apps make a notification truly unswipeable, so persistence is built from re-posting and escalation rather than a lock.

| Surface | What the user sees | How it persists |
| --- | --- | --- |
| Phone notification | Task title, time since due, Done, Missed and Snooze buttons | Ongoing notification; if swiped away, it is re-posted after 60 s |
| Lock screen | Same notification, top of list | Ongoing notifications cannot be swiped while the phone is locked |
| Status bar chip (Android 16+) | Short task label | Requested as a Live Update; the system may decline (see constraints) |
| In-app "Now" screen | Full-screen current task with a large Done button and a Missed option | Opens on notification tap; the app's home screen when a task is due |
| Watch | Vibration, task card with Done and Missed | Wear OS ongoing activity plus a tile; ongoing notifications stay non-dismissible on the watch |
| Full-screen takeover | Alarm-style screen over the lock screen | Opt-in only, if the user grants the full-screen permission |

**Escalation ladder (defaults, user-adjustable):**

1. At the task time: notification plus one watch vibration.
2. Every 5 minutes while unresolved: re-alert on phone and watch.
3. After 3 ignored re-alerts: stronger pattern (sound plus long vibration).
4. The task stays active until Done or Missed, or until the next task becomes due; then it is marked Missed automatically and the next task's reminder takes over. The plan's last task stays active until the plan window ends, then is marked Missed.

**Snooze rules:** 10 minutes per snooze; one app-wide setting sets how many snoozes each task gets (default 2, user-adjustable in Settings); no daily cap. Every snooze is logged and shown in the review. If the next task becomes due while this one is snoozed, the handover rule applies and it is marked Missed.

**Missed rules:** Tapping Missed clears the reminder everywhere, shows a 10-second Undo, and records the reason: marked by the user, handed over when the next task became due, or plan window ended. Tasks due at the same time don't hand over to each other; only a later task triggers the handover. If you finish a missed task afterwards, you can change it to Done from the Today list until the end of that day (local midnight); the review shows it as done late.

## Android platform constraints and permissions

Four Android rules shape the design; the app supports Android 12+ (minSdk 31), targets the latest SDK, and uses newer features where the device has them.

| Area | Current rule | Design response |
| --- | --- | --- |
| Exact alarms | `SCHEDULE_EXACT_ALARM` is denied by default on fresh installs from Android 14; the user must grant it. `USE_EXACT_ALARM` is auto-granted but Play allows it only for alarm-clock and calendar-type apps. ([Android docs](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)) | Request `SCHEDULE_EXACT_ALARM` in onboarding; check `canScheduleExactAlarms()`; fall back to inexact alarms with a warning banner. |
| Swipeable notifications | From Android 14, users can dismiss notifications marked ongoing, except on the lock screen; call and media notifications are exempt. ([Android docs](https://developer.android.com/about/versions/14/behavior-changes-all)) | Re-post on dismissal (delete intent) until Done. |
| Full-screen alerts | `USE_FULL_SCREEN_INTENT` is granted by default only to calling and alarm apps; Play requires a declaration. ([Android docs](https://developer.android.com/about/versions/14/behavior-changes-14), [Play policy](https://support.google.com/googleplay/android-developer/answer/13392821)) | Takeover is opt-in: check `canUseFullScreenIntent()` and send the user to the settings screen to allow it. |
| Live Updates (Android 16+) | Promoted ongoing notifications need `POST_PROMOTED_NOTIFICATIONS` and a standard or progress-style template; they target ongoing activities, not simple reminders. ([Android Police](https://www.androidpolice.com/android-16-qpr-1-full-live-updates-support/)) | Request promotion for an active task; treat it as a bonus, never the only surface. |

Other permissions: `POST_NOTIFICATIONS` (Android 13+ runtime prompt) and `RECEIVE_BOOT_COMPLETED` (re-schedule alarms after restart). Avoid "display over other apps" (`SYSTEM_ALERT_WINDOW`) in v1: it adds Play review risk and user friction.

**Behaviour by Android version:** the code branches on `Build.VERSION.SDK_INT`, so older phones get the strongest persistence they allow and newer phones get the newer surfaces.

| Android version | Persistence behaviour | Extra features used |
| --- | --- | --- |
| 12 (API 31) | Ongoing notifications can't be swiped away; exact-alarm access requested at onboarding | Base experience |
| 13 (API 33) | Same, plus runtime `POST_NOTIFICATIONS` prompt | Notification permission flow |
| 14–15 (API 34–35) | Ongoing notifications swipeable when unlocked, so re-post on dismissal; full-screen alerts need user grant | `canUseFullScreenIntent()` check, full-screen settings link |
| 16+ (API 36) | As 14, plus promoted ongoing notification request | Live Update status bar chip |

## Architecture and tech stack

Native Kotlin in one Android Studio project with three modules; no backend in v1. The phone is the source of truth and the watch mirrors it.

| Layer | Choice | Why |
| --- | --- | --- |
| Language / UI (phone) | Kotlin + Jetpack Compose | Google's standard; best Claude Code support |
| UI (watch) | Compose for Wear OS, Tiles, complications | Required for native watch surfaces |
| Local storage | Room (SQLite) | Structured tasks and event history, offline |
| Scheduling | `AlarmManager` exact alarms + boot receiver | Fires on time even when the app is closed |
| Reminders | `NotificationCompat` with Done/Missed/Snooze actions | One reminder engine for phone and watch |
| Phone-to-watch sync | Wearable Data Layer API | Built-in, no server needed |
| Architecture pattern | MVVM + repository, Hilt for dependency injection | Testable, conventional |
| Tests | JUnit, Compose UI tests, Robolectric for alarm logic | Reminder timing must be test-covered |

**Modules:** `:core` (task model, Room, reminder engine), `:app` (phone UI), `:wear` (watch UI, tile, complication).

**Later (v2) on Azure:** sign-in with Microsoft Entra External ID, an API on Azure Functions or App Service, Azure SQL or Cosmos DB for sync across devices, and Azure Notification Hubs if server-driven reminders are needed.

## Data model

Four tables cover v1: the plan, its tasks, an event log that drives escalation and the daily review, and settings.

| Entity | Key fields | Notes |
| --- | --- | --- |
| `Plan` | id, title, startDate, endDate, createdAt | A time window, usually one day |
| `Task` | id, planId, title, note, scheduledAt, durationMin?, status, snoozeCount, missedReason?, completedAt? | status: Scheduled, Due, Snoozed, Done, Missed; missedReason: UserMarked, NextTaskDue, PlanEnded |
| `ReminderEvent` | id, taskId, type, at, device | type: Fired, Realerted, Escalated, Snoozed, Dismissed, Reposted, Done, MarkedMissed, AutoMissed, Undone, DoneLate; device: Phone or Watch |
| `Settings` | realertIntervalMin, escalateAfter, snoozeMin, maxSnoozes, fullScreenEnabled | Single row; defaults 5, 3, 10, 2, off |

Store times in UTC with the device time zone applied at display, so travel and daylight-saving changes don't shift reminders.

## Build plan with Claude

Build the riskiest part first: prove the reminder engine works on a real phone before any polished UI. Each phase ends with a gate you test on a device.

| Phase | Goal | Gate to move on |
| --- | --- | --- |
| 0. Setup | Android Studio, Claude Code, GitHub repo, 3-module project, CLAUDE.md | Empty app runs on phone and watch emulator |
| 1. Reminder spike | Exact alarm fires a notification with Done; re-post on swipe | Works with the app closed and after a reboot |
| 2. Planning | Room database, add/edit tasks, today list | Tasks survive restart; alarms reschedule on edit |
| 3. Escalation | Re-alerts, snooze limits, Missed (manual and automatic handover), event log, end-of-day review | Unit tests cover the full escalation ladder and the handover rule |
| 4. Watch | Data Layer sync, Done on watch, ongoing activity, tile | Done on watch clears the phone within seconds |
| 5. Onboarding | Permission flow, settings, optional full-screen mode | New install reaches a working reminder in under 2 minutes |
| 6. Release | Closed testing track, store listing, privacy policy, Data safety form, content rating, full-screen permission declaration | 12+ testers opted in and active for 14 continuous days; production access approved |

**How to work with Claude at each step:**

1. **Spec (this doc).** It is the source of truth; update it when decisions change.
2. **Mockups.** Ask Claude for designs of the Today, Now, and watch screens before building UI; the frontend-design skill helps with visual direction.
3. **CLAUDE.md.** At the repo root: stack, module layout, build and test commands, the escalation rules, and "never remove a permission check".
4. **Plan mode per feature.** Claude proposes a plan for one phase item; you review it, then it codes.
5. **Small slices.** One feature, run the tests, try on your phone, commit. Never "build the whole app" in one prompt.
6. **Review.** Ask a subagent to review each finished phase against this spec.
7. **Reusable skills.** Once a workflow repeats (e.g. "add a setting end-to-end"), turn it into a skill with skill-creator.

Play release is planned from day one: target API 37 (Android 17; Play requires at least API 36 for new apps from 31 August 2026), build signed app bundles with Play App Signing, and keep v1 free of data collection so the Data safety form stays simple. Start recruiting 14–15 testers during Phase 4 so closed testing can begin as soon as Phase 5 ends. ([target API rules](https://support.google.com/googleplay/android-developer/answer/11926878))

Test on your own phone from Phase 1. Some manufacturers (notably Samsung and Xiaomi) stop background alarms aggressively, so onboarding may need a "disable battery optimization" step.

## Open questions and decisions log

**Decisions made**

| Date | Decision |
| --- | --- |
| 2026-09-25 | Working name: Stickler (previously Stay-On-Task) |
| 2026-09-25 | Start with Android phone + Wear OS; iPhone later |
| 2026-09-25 | v1 is single-user and offline; Azure sync in v2 |
| 2026-09-25 | Persistence via re-posting and escalation; full-screen takeover is opt-in |
| 2026-09-25 | Minimum Android 12 (API 31); use Android 14+ and 16+ features where available |
| 2026-09-25 | Don't declare as an alarm app on Play in v1: use user-granted exact alarms and full-screen permission; revisit if a per-task "ring like an alarm" feature is added |
| 2026-09-25 | Snoozes per task set by one app-wide setting (default 2, user-adjustable); no daily snooze cap |
| 2026-09-25 | Plan for Google Play release from the start; compile and target SDK 37 (Android 17) |
| 2026-09-25 | Publish from a personal Play developer account (no registered company yet); requires the 12-tester, 14-day closed test |
| 2026-09-26 | Users can mark a task Missed; an unresolved task is marked Missed automatically when the next task becomes due (replaces "never auto-clears") |
| 2026-09-26 | A missed task can be changed to Done until the end of that day (local midnight); it is recorded as done late |

**Open questions**

- [ ] Which watch to test on (deferred; no watch yet). Leading option: a Samsung Galaxy Watch, as the most common and most customized Wear OS watch. Use the Wear OS emulator until then.
