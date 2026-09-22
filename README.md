# Baby Buddy Pocket

**Requires access to an existing Baby Buddy server. Server hosting is not included.**

A small, independent native Android companion for your own [Baby Buddy](https://github.com/babybuddy/babybuddy) server. Rounded cards, a calm palette, and a shared view of your baby's day.

**Android 8.0+ · Java 17 · one standalone QR decoder dependency · GPLv3 licensed**

Source: [Felixre7/Baby-Buddy-Pocket](https://github.com/Felixre7/Baby-Buddy-Pocket). Store copy: [Google Play listing draft](docs/PLAY-STORE-LISTING.md).

**0.1.10 starts a new Android identity (`com.babybuddypocket.app`).** It installs separately from earlier prototypes and does not migrate their local data. Sync pending records in the earlier app before switching, then connect the new app to the same server. Keep the earlier app until its pending records and timers are resolved.

## Design priorities

Useful functionality and simplicity come first. Favor maintainability, responsiveness, easy self-builds, and few dependencies over feature count. Challenge additions that create disproportionate complexity and propose simpler options; preserve correctness and data integrity.

## Get started

Need a server? Use the [Baby Buddy Railway template](https://railway.com/deploy/baby-buddy-self-hosted-baby-tracker--baby-buddy).

1. Build or install the debug APK using the instructions below.
2. Open **Baby Buddy Pocket** and tap **Scan Baby Buddy QR code**. In your server's browser interface, open **Add a device** and scan its login QR code. Review the filled server address, then tap **Connect to Baby Buddy**. Camera permission is requested only when scanning; frames are decoded locally and never saved or uploaded. Browser session cookies in the QR code are ignored. Manual HTTPS address/API-token entry remains available without camera permission. Find your token in **Baby Buddy → User settings → API key**. A Railway address such as `https://your-server.up.railway.app` works; a server hosted under a path such as `https://example.com/baby/` is also supported. Do not include a login page or query string.
3. Wait for the first sync. Children are managed on the server; use the child button to switch between them.
4. **Settings → Measurement labels** defaults to metric. If your records use other units, change the labels to match. Baby Buddy stores plain numbers without enforcing or reporting their units; the app does not convert existing values or uploads. There is no confirmation banner.

Use **Try offline demo** to explore synthetic sample data and practice logging without a network connection or credentials. Demo entries stay in memory and reset when the app process restarts. Leave demo in Settings before connecting your server. The former public demo was removed in 0.1.3 to keep the app simple; installations left in that mode switch to local demo and discard only the retired public-demo cache.

Your server must expose Baby Buddy's normal token-authenticated API. HTTP-only servers, self-signed certificates not trusted by Android, browser-only authentication proxies, and redirects from a stale server address are not supported. No server changes or extra service are required for a normal HTTPS Baby Buddy deployment.

## What it does

- **Today:** child selection, daily feeding/diaper counts, sleep/tummy-time duration, recent records, and quick logging.
- **Timeline:** date-grouped activity, search across notes/tags/details, activity filters, details, and paged rendering of locally cached history. With one activity selected, Log activity opens its form directly; All activities or a read-only activity opens the picker.
- **Trends:** seven-day feeding/diaper counts and sleep/tummy-time duration, plus recent measurements. Days use the device's time zone; duration entries are split across midnight.
- **Logging:** an icon-card grid replaces the menu. Required fields appear first. Choices and toggles are remembered per child/activity after saving; amounts, measurements, notes, and dates start fresh. Forms follow the server's OPTIONS schema and permissions, including choices, date/time pickers, booleans, numeric values, notes, and comma-separated tags. Supported collections include feeding, sleep, changes, tummy time, pumping, notes, weight, height, head circumference, temperature, BMI, and medications when exposed by the server.
- **Timers:** feeding, sleep, tummy time, and pumping default to **Start a timer now**. Configure the entry, start it, then use **Stop & save** on Today. New running timers are shared with your Baby Buddy server after sync; another authorized caregiver can see and finish them. Timers created elsewhere offer **Finish & log…**. Manual start/end entry remains available. Existing phone-only timers from older versions remain finishable without being silently published.
- **Offline timers:** start and stop work without connectivity after the initial server setup. The app saves timestamps and choices in SQLite, surviving app/device restarts. A session completed before its start was sent uploads as one ordinary completed activity. For an already shared timer, the app uploads the completed activity with the captured times, then removes the timer through a durable, retryable cleanup step. Another offline session can start while an earlier log waits. Demo timers remain synthetic and reset with the demo.
- **Activities:** Settings has visibility checkboxes with Move up/down buttons stacked on the right. Order applies to the logging grid, quick actions, summaries, filters, and trends. Hidden activities are omitted from those screens; their server records remain untouched and continue syncing. Active timers and pending-entry review remain accessible so hiding an activity cannot strand unfinished work. Demo preferences are separate from family activity preferences.
- **Offline:** read downloaded records and save new entries locally. Inspect queued, rejected, or uncertain entries in **Settings → Pending entries**. Individual activity cards show a small status symbol on Today, Timeline, and recent measurements: a check for synced, a circle for saved on this phone, and an exclamation mark for attention. Demo records use a diamond. The symbols have accessibility descriptions and long-press tooltips. Tap a pending card to open that specific entry and its correction controls.
- **Light/dark appearance:** follows Android's system theme. Icons are original drawings in `Ui.java` using Android Canvas; launcher icons are bundled XML vectors. No downloaded fonts, external icon assets, or icon libraries.
- **Timer controls:** running timers offer Stop & save, Edit times for configured timers, and Cancel timer. Stopped/cancelled timers leave Today; pending work and errors remain in Settings. Cancellation of a shared timer waits for sync and does not create an activity. A start with an unknown server result still needs review before retrying.
- **One timer per child:** a known running timer blocks a new timer for that child across all activities and offers View running timer. Other children can have their own timers; manual logs stay available. An unassigned server timer is treated conservatively as a possible conflict.
- **Problem reports:** Settings and the connection screen offer Report a problem. Preview, copy, share, or clear a small local technical history. It records error types, HTTP status, selected validation field names, known rejection categories, code frames and timer/sync events; reports add app/device/display information and aggregate queue counts. It excludes record contents, tokens, server addresses, raw exception/server messages and screenshots. Nothing is uploaded automatically; sharing uses your chosen app. GitHub issues are public. This is focused troubleshooting, not automatic crash/ANR capture.

This version creates and edits activities and reads shared data. Pending activity copies can be corrected or deleted on the phone. Delete already-synced records, manage children, and upload/view photos through the Baby Buddy web interface. There are no widgets, notification timers, biometric app lock, or scheduled background sync. It is an independent client, not an official Baby Buddy or Baby Buddy Companion release.

## Sync behavior

Sync runs on opening/resuming the app, once per minute while foregrounded, after saving an entry, and when you tap **Sync now** in Settings. Sync status, errors, and pending-entry review are also in Settings. A form being edited pauses the periodic refresh. Networking uses one process-scoped worker so rotation does not create a second sync.

The app reads every page of each supported collection before replacing its local snapshot. A failed refresh keeps the previous snapshot. A successful upload is stored locally immediately, even if the following download fails. New entries saved during a sync are picked up by a subsequent sync. Server-side edits and deletions appear on the next successful refresh.

Timer reliability takes priority over exact synchronization timing. Recording explicit stop times also prevents delayed uploads from creating overlapping or over-24-hour sessions that Baby Buddy would reject. Timer cleanup is queued only after the activity upload is confirmed; a lost cleanup reply can be retried safely, including a 404 for an already removed timer. Uploads with uncertain responses still require review, rather than blind retries. A shared timer that was already removed or restarted is checked before uploading its activity. Delayed cleanup also checks for a changed timer and leaves it intact for review. Saving the activity and deleting the timer are two API operations, not a server-side transaction: simultaneous caregiver actions can still conflict and require review. Cleanup requires permission to delete that timer; failed cleanup leaves the saved activity intact and a pending item in Settings. Use the server web interface to resolve conflicts or correct a forgotten session longer than 24 hours. Keep the app open or resume it to sync; there is no background scheduler.

Pending entries have explicit states:

| State | Meaning | Next step |
| --- | --- | --- |
| Queued | Saved locally; not sent yet | Sync when connected |
| Rejected | Server returned a definite client error | Read the plain explanation; edit the pending activity, delete its local copy, or retry after fixing permissions |
| Review | The server may have received the entry, but delivery is uncertain | Check the server/timeline before explicitly retrying; discard the local copy if it already exists |

A newly rejected or uncertain entry raises a brief notice with **Review entry**. Settings also shows entries needing attention. Common overlap, future-time, time-order, duration, missing-value and invalid-choice rejections have plain explanations; unfamiliar failures point to **Report a problem**. Raw server responses are not displayed or included in reports. **Edit activity** reopens a queued or definitively rejected activity with its original fields; Save changes replaces that pending entry and queues it for sync. Cancel leaves it unchanged. Uncertain uploads and timer operations cannot be edited this way. **Delete pending entry** removes only the local pending copy; it never deletes a server activity, and an associated shared timer remains on the server. Shared-timer corrections preserve the original timer identity for guarded cleanup. Routine saves say **Activity saved**; Settings retains synchronization details.

**Historical editing** uses the same Edit activity form as pending corrections, including original choices, times, notes, measurements, and tags. It uses the cached writable form schema and the server enforces change permissions. Edits save offline in the existing SQLite queue and survive restart. A pending edit replaces the activity's displayed card; daily totals/trends still use confirmed server data. Deleting a pending edit restores the confirmed activity in the display and never deletes it from the server.

Sync reads the current activity before sending only changed fields with PATCH. Unrelated caregiver changes are preserved. A change to the same fields, a reassigned child, or a removed record stops for review instead of silently replacing/recreating the activity. Delete that pending edit, sync, and edit the latest record to resolve a conflict. An uncertain update is never automatically retried; an explicit retry can recognize an edit already applied without sending it again. These read/write requests are separate: another client can still change the same field between the check and PATCH. This is a conflict check, not an atomic server lock. Schema 6 stores the original activity with each pending edit; older queues remain intact. No new dependency is required.

Baby Buddy does not provide a general idempotency guarantee for new records. The app therefore persists an uncertain state **before** sending a POST. Timeouts, ambiguous server errors, and interrupted uploads do not automatically retry that entry. This can require manual review even if a request never left the phone, but avoids silently repeating a possibly successful write. Pending records appear in the timeline and are excluded from confirmed daily totals.

Initial sync downloads all history. Very large servers may take longer. There is a defensive maximum of 1,000 pages per collection and 16 MiB per response; exceeding it fails the refresh and preserves the prior cache. A full refresh is not a transaction across all server collections, so concurrent server changes can appear in the following refresh.

Baby Buddy rejects future timer timestamps, including small phone/server clock differences. Before uploading timers and duration records, the app uses the HTTPS response's `Date` header and a conservative one-second margin to cap future times. For a completed session without a confirmed shared timer, both times move together to preserve duration; a shared timer keeps its confirmed start. Already-past times remain unchanged. A missing/incorrect server Date header can still require checking server/phone clocks. Local times stay durable until the server confirms the upload.

Version 0.1.12 recovers definitely rejected timer starts: a saved stop becomes a normal completed log, and an active start rejected with the reported future-time validation can retry on the next app launch. Unknown-delivery states are never automatically retried. Install updates without uninstalling so queued activity is preserved.

Before publishing each new timer, sync checks the server's current timers. A failed check leaves the local start queued without sending it. A conflicting start stays local and reviewable; cancel it to use the shared timer, or stop and save only if it represents a separate session. A timer already stopped/cancelled locally frees the local slot; its server cleanup completes before the next shared start. Two clients can still race between checking and creating, because these are separate requests. If multiple timers are found, Today shows a conflict notice and preserves them for an explicit choice; the app never guesses which timer to merge or delete. Finishing also checks that the shared timer still exists and has not changed, but concurrent saves cannot be made fully atomic by this client. Server validation errors and uncertain writes remain in Settings. Different activity types and manual logs are not automatically treated as duplicate records.

Diagnostics use the existing worker and app-private, backup-excluded storage, bounded to 100 events and 64 KiB. Events older than seven days are pruned when diagnostics are read or written; clearing diagnostics or disconnecting clears the file. No scheduled cleanup or reporting service is added.

## Build it yourself

Compatibility results are recorded in [the emulator matrix](docs/COMPATIBILITY.md). For store signing, the app bundle, and remaining publication work, see [Google Play release preparation](docs/PLAY-RELEASE.md).

Install **JDK 17** and **Android SDK Platform 36**. Android Studio can install the SDK/platform/build tools for you. No Node, npm, Kotlin plugin, NDK, account, API key in source, or backend build is needed.

### Android Studio

1. Open this folder as a project.
2. Set **Gradle JDK** to **JDK 17** in Android Studio settings. Newer Android Studio versions may bundle a JDK too new for the pinned Gradle version; use the IDE's Download JDK option if needed.
3. Let Gradle sync and install SDK 36 if prompted.
4. Choose **Build → Generate App Bundles or APKs → Generate APKs**, or run the `assembleDebug` Gradle task.

### Command line

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your Android SDK, or create an ignored `local.properties` with `sdk.dir` pointing to that SDK.

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

```sh
# Linux / macOS (sh also works if the executable bit was lost in a ZIP)
sh ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The wrapper downloads the pinned Gradle distribution and verifies its SHA-256. The first build needs network access to Google Maven and Maven Central.

Output: `app/build/outputs/apk/debug/app-debug.apk`. This APK is signed with your machine's Android debug key and is suitable for personal testing/sideloading. To install using ADB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android may ask you to allow installations from the app opening the APK. Self-builds signed with different keys cannot update one another. Keep a consistent signing key; uninstalling to change keys deletes local/unsynced data.

### Updating an installed APK

Downloading the APK through Proton Drive or another file-transfer service does not change installation behavior. Open the new APK and accept Android's **Update** prompt; do not uninstall first. An in-place update preserves settings, the saved connection, cached records, and pending entries. The application ID and signing certificate must match, and the internal `versionCode` must be equal or higher. Reinstalling the same version is allowed; each delivered build gets an increased code and a distinct filename to make downloads easy to identify. Version 0.1.16 uses code 17. The update rules below apply only when the application ID matches; 0.1.10 cannot replace pre-publication prototypes.

The distributed test APKs currently use this build machine's Android debug signing key. Keep that key private and preserve it for future compatible test updates. A self-build on another machine normally uses a different debug key. Changing to a different release key later requires a deliberate migration; it is not an interchangeable update. See [Android's update requirements](https://developer.android.com/google/play/app-updates).

For a distributable release, use Android Studio's **Generate Signed App Bundle / APK** flow and keep the signing key private and backed up. `assembleRelease` produces an **unsigned** APK unless you configure release signing. No signing secrets belong in this repository.

### Windows socket/loopback workaround

Some Windows desktop environments virtualize Java's temporary Unix-domain socket paths and cause `Unable to establish loopback connection` before Gradle compiles anything. The included optional helper uses a short project-local socket directory for the build process:

```powershell
.\scripts\build.ps1
```

This does not change machine-wide environment variables. Normal installations can use the standard Gradle command above.

## Tests

For routine changes, begin with focused checks and a Pixel 9 Pro emulator on the latest stable Android image. Deliver the test APK for phone testing after those checks, then continue relevant older-Android and other layout/device checks in parallel. Report the actual Android/API tested and any later compatibility findings separately; the full matrix need not delay the first test APK.

JVM tests exercise API pagination/origin restrictions, snapshot failure behavior, POST uncertainty and retries, child isolation, DST/midnight duration calculations, units, and form validation. JUnit 4.13.2 and `org.json` 20240303 are **test-only**; neither is packaged into the APK.

The Android smoke runner uses Android's built-in Instrumentation API. Use a **clean emulator**, not a connected personal installation: it tests local SQLite storage and Keystore, enters demo, navigates the real screens, opens a form, rotates, and captures screenshots. It refuses to run if server credentials are present.

```sh
sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s EMULATOR_SERIAL shell am instrument -w com.babybuddypocket.app.test/com.babybuddypocket.app.SmokeInstrumentation
```

Check for the runner's `PASS` result; ADB's exit code alone does not prove the instrumentation passed. Screenshots are saved to the emulator's `/sdcard/Android/data/com.babybuddypocket.app/files/screenshots/`. Use `-e prefix dark-` before the component name to distinguish an additional screenshot run.

An opt-in live integration runner is also available; see [live sync testing](docs/LIVE-SYNC-TESTING.md). The default smoke run uses synthetic offline data and never contacts a server.

## Contributing and feedback

Use the [bug report form](https://github.com/Felixre7/Baby-Buddy-Pocket/issues/new/choose) for problems and [Discussions](https://github.com/Felixre7/Baby-Buddy-Pocket/discussions) for feature ideas and usage questions. Read [CONTRIBUTING.md](CONTRIBUTING.md) before starting a change, including the [AI usage policy](CONTRIBUTING.md#ai-usage-policy): AI-assisted implementation is welcome when reviewed and tested; reports and feature proposals must be written in your own words. Any language is welcome. Keep credentials and family records out of public posts.

## License

Copyright (c) 2026 Baby Buddy Pocket contributors.

Baby Buddy Pocket is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License, version 3 only** (`SPDX-License-Identifier: GPL-3.0-only`). See [LICENSE](LICENSE) for the full terms. This applies to the project's original code, artwork, and documentation unless a file states otherwise.

The software is distributed without any warranty, including the implied warranties of merchantability or fitness for a particular purpose. Distributed covered modifications must remain under GPLv3, with corresponding source provided as the license requires. Commercial use is allowed.

APK and bundle builds include the project LICENSE and NOTICE automatically. Distribute each binary with access to its matching corresponding source; see the [release instructions](docs/PLAY-RELEASE.md#license-and-corresponding-source).

Third-party components retain their own licenses and notices, including Apache-2.0 for ZXing and the Gradle wrapper. Earlier copies released under MIT retain their MIT permissions; this change does not revoke them.

## Privacy and architecture

- Network calls go directly to your configured HTTPS API. Authentication is `Authorization: Token …`. Requests cannot follow a pagination link to a different origin/API path; redirects are refused.
- Android Keystore encrypts the saved token with AES-GCM. The record cache and pending queue use app-private SQLite; the database is not separately encrypted.
- Cloud backup and device-transfer rules exclude app data. No analytics, ads, or telemetry. The sole third-party runtime library is [ZXing core 3.5.4](https://github.com/zxing/zxing), an Apache-2.0 QR decoder with no runtime transitive dependencies. Its license and notices are bundled in the APK. Camera preview uses Android's framework APIs; scanning requires no Play Services or separate scanner app.
- Disconnect clears this device's credentials, cache, preferences, and pending entries after confirmation. Server records remain.
- `ApiClient` handles HTTP/pagination; `SyncEngine` handles download/upload policy; `LocalStore` owns transactions and the queue; `AppController` owns the worker/lifecycle state; `Records`, `Units`, and `FormValues` provide testable data logic; `MainActivity`, `RecordForm`, and `Ui` build platform views.

Visual inspiration: [LiftLog](https://github.com/LiamMorrow/LiftLog) and [Baby Buddy Companion](https://babybuddy.app/). The Android implementation and artwork are original. The standard Gradle wrapper retains its upstream Apache-2.0 headers/license notices.

See [0.1.16 verification](docs/VERIFICATION-0.1.16.md) for filtered logging and softer filter styling, [0.1.15 verification](docs/VERIFICATION-0.1.15.md) for historical editing and card status, [0.1.14 verification](docs/VERIFICATION-0.1.14.md) for plain rejection explanations and pending-activity corrections, [0.1.13 verification](docs/VERIFICATION-0.1.13.md) for one-timer-per-child safeguards and caregiver conflicts, [0.1.12 verification](docs/VERIFICATION-0.1.12.md) for timer clock/recovery and local diagnostics, [0.1.10 verification](docs/VERIFICATION-0.1.10.md) for the new Android identity, [0.1.9 verification](docs/VERIFICATION-0.1.9.md) for the Railway setup link, [0.1.8 verification](docs/VERIFICATION-0.1.8.md) for the rename and update checks, [0.1.7 verification](docs/VERIFICATION-0.1.7.md) for shared/offline timer checks, [the project handoff](docs/PROJECT-HANDOFF.md) for the original design choices, [0.1.6 verification](docs/VERIFICATION-0.1.6.md) for timer-default/Settings checks, [0.1.5 verification](docs/VERIFICATION-0.1.5.md) for activity customization and phone timers, [0.1.4 verification](docs/VERIFICATION-0.1.4.md) for cleanup checks, [0.1.3 verification](docs/VERIFICATION-0.1.3.md) for local-demo simplification and upgrade checks, [0.1.1 verification](docs/VERIFICATION-0.1.1.md) for QR/icon/update testing, and [live-sync verification](docs/VERIFICATION.md) for integration scope and limits.
