# Production diagnostics proposal

Researched 2026-09-21 against app 0.1.11. This is a recommendation, **not implemented logging or an enabled upload service**. Prioritize useful reports, few dependencies, and easy self-builds.

## Current app behavior

There is no persistent diagnostic event log, crash-report export, custom uncaught-exception handler, or remote reporting SDK in production code. Android can emit system crash information to Logcat, but that does not automatically deliver it to this project's maintainers. The installed debug APK does not change that. Emulator instrumentation captures are development-only.

`AppController` exposes the latest sync error in memory; `LocalStore` retains failure messages for pending writes. These are user-facing recovery state, not a support report. **Do not simply export those strings:** `ApiClient` can include a JSON server-error response and `SyncEngine.friendly()` can return an exception message. Those may contain personal values, addresses, or server details. Retain useful local error display separately from any diagnostic export.

## Established approaches

| Approach | What it helps with | Tradeoff for this project |
| --- | --- | --- |
| Google Play Android vitals | Automatically collected crash/ANR groups, stack traces and device/version breakdowns | No app reporting SDK needed. Only eligible Play installs on certified devices, participating users, and sufficient anonymized data; reports update daily. Not a complete support log or a source for sideloaded APK reports. |
| User-submitted report plus issue tracker | Wrong behavior, sync failures, reproduction steps, sideloaded/self-built versions | User participation required; a small built-in export can avoid requiring ADB or technical knowledge. |
| Firebase Crashlytics | Hosted crash grouping, alerts, custom context and explicitly recorded nonfatal exceptions | Adds Firebase SDK/build configuration and an external data recipient; automatic collection is the default, but opt-in configuration exists. More convenient automation, more configuration/privacy maintenance. |
| ACRA | Open-source Android exception reporting with configurable report destination | An alternative library, not a complete hosted service by itself. Still requires choosing report content, delivery and operational support. |

Sources: [Android vitals](https://developer.android.com/topic/performance/vitals), [Play data coverage and timing](https://support.google.com/googleplay/android-developer/answer/9844486?hl=en), [crash and ANR details](https://support.google.com/googleplay/android-developer/answer/9859174), [Crashlytics](https://firebase.google.com/docs/crashlytics), [Crashlytics opt-in/custom reporting](https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android), [ACRA](https://www.acra.ch/).

These are concrete open-source examples, not a survey claiming every project follows one practice: [AntennaPod](https://antennapod.org/documentation/bugs-first-aid/bug-report) asks for system information from its Report bug screen; its [maintainer guidance](https://forum.antennapod.org/t/about-the-bug-report-category/29) also requests reproduction steps and exported logs. [Signal](https://support.signal.org/hc/en-us/articles/360007318591-Debug-Logs-and-Crash-Reports) offers a user-triggered debug-log submission flow and states that those logs exclude message contents. Copy the principle of deliberate, limited reporting; a log-hosting service is unnecessary for our first version.

## Recommended first implementation

1. Use Play vitals after publication. Review new/regressed issues after releases; prioritize affected users and device/version patterns. Google/system diagnostic sharing is distinct from the app's own sharing controls.
2. Add **Settings → Report a problem**, plus access from the initial connection screen. Show a plain-text preview with **Copy**, **Share**, **Clear diagnostics**, and a link to GitHub Issues. Use Android's standard sharing flow; never submit a GitHub issue or send email automatically. Sharing must remain usable offline with no GitHub login required to generate the report. A private support address can be offered once one is chosen.
3. Keep a bounded, app-private technical event history: proposed limits of 100 events, 64 KiB total, and seven days, whichever limit is reached first. Record discrete failure/transition events, not every tap or every successful polling request. Clear on disconnect/account change and explicit user request. No network uploader, background service, session replay or analytics dependency.
4. Best-effort capture of the most recent uncaught Java exception: store exception types and bounded code stack frames, excluding exception messages, dynamic thread names and data values. Always delegate to Android's existing uncaught-exception handler; never suppress the crash, restart in a loop, block indefinitely, or attempt network access while crashing. This will not catch every native failure, ANR, early-startup failure, low-memory kill or storage failure.
5. Add a GitHub bug-report template asking for expected/actual behavior, steps, app version/build source, Android/device details and the reviewed report. Warn that GitHub issues are public; screenshots are optional and need personal details removed. Do not ask for an API key or database export.

Use an explicit allowlist of fields at recording time rather than writing arbitrary messages and attempting to redact them afterward. The crash path must work independently of the app's database and sync queue; a diagnostic write failure must never block logging an activity or saving a timer. Ordinary diagnostic work should be bounded and performed off the UI thread. Exact storage implementation remains to be selected during implementation.

### Proposed report contents

- App version/code and build type; phone manufacturer/model, Android/API version and ABI. Maintain a release-to-commit mapping; self-build users can optionally supply their commit. No hardware serial, Android ID, advertising ID or stable installation ID.
- Screen size/density, font scale, theme and locale for layout reproduction.
- Generic operation categories such as `sync_started`, `download_failed`, `upload_awaiting_review`, `timer_save_failed`, or `camera_permission_denied`. Avoid activity-specific categories or event timestamps that reveal care routines; use relative technical durations and sequence numbers where possible.
- Failure category (timeout, DNS, TLS, HTTP, parsing, storage), HTTP status, exception type/code frames and generic phase. Exclude full request URLs, server hostname, endpoint record IDs, headers, bodies and raw server errors.
- Aggregate queued/rejected/review counts, without record IDs, payloads, notes, baby names, dates of birth, measurements, care timestamps, QR content, credentials, screenshots or cached records. Preview remains necessary: even device configuration is user information.

Example: “0.1.12 / Android 13 / Samsung model; upload timed out after 25 s; one entry awaits review” can point to our ambiguous-write handling without exposing what was logged or the family's server. It does not prove whether the server received the request; diagnosis must respect the existing no-blind-retry policy.

## Debugging workflow and limits

Match the report's version to source, group similar stack traces, reproduce the steps on the closest OS/screen configuration using synthetic data, write a targeted regression check, and ship a fix through internal testing. A stack trace identifies the code call chain and failure location; it does not necessarily explain the underlying cause. A failed sync that is caught and shown in Settings is normally not an OS crash and needs explicit local diagnostic events.

Keep exact release artifacts and, if minification is enabled later, matching R8 mapping files so line/class names remain interpretable. Native symbols would matter if native libraries are introduced; there are none now. Production reporting does not require shipping a debuggable app.

Use [Logcat](https://developer.android.com/studio/debug/logcat) with a willing technical tester for difficult cases, not as the normal customer workflow. Avoid requesting whole-device bug reports publicly. If the app cannot open at all, in-app export is unavailable; Play reports and developer-assisted logs remain fallback paths.

Android 11/API 30 adds [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo), which may help distinguish earlier process exits and retrieve available ANR traces. Consider it later only if Play/user reports leave a demonstrated gap; it is not universal capture and the app supports Android 8.

Before shipping any exporter, test bounded retention, repeated crashes, full/unwritable storage, offline sharing, account clearing, and deliberate secret/PII fixtures in server errors and exception messages. Review the final text for excluded fields and verify actual crash/next-launch export on old and current Android. Update privacy disclosures to describe local diagnostics and user-initiated sharing. No claim of automatic collection or implemented export should appear in store copy until it exists.

Recommendation: start with **Play vitals + reviewed local reports + GitHub Issues**. Defer Firebase/ACRA integration and custom hosting unless report volume or missed failures justify the extra maintenance.
