---
up: "[[MOCs/projects/_projects]]"
tags:
  - project
  - android
  - baby-buddy
status: prototype-built
updated: 2026-09-21
---

# Baby Buddy Pocket — Android Baby Buddy companion

> **Current status after resuming:** Version 0.1.0 is implemented and builds successfully. The native UI, schema-driven logging, local storage, outbox, server sync, timers, trends, demo mode, and documentation are present. Debug and unsigned release builds pass; 30 JVM tests pass; Android 16 smoke tests pass on both Pixel 7 and Pixel 9 Pro emulator profiles in light mode and dark mode at 1.3× font scale (20 assertions per run). See [README](../README.md) and [verification](VERIFICATION.md). Live Railway sync passed on 2026-09-21: 15 read checks, then 22 checks including one temporary note upload, independent readback, deletion, and cache refresh. Emulator credentials and family data were cleared afterward. Connection credentials are entered in the app. The remainder of this note preserves the original design and the initial pause snapshot, rather than describing the final tree.

> **0.1.1 update:** Added the Baby Buddy Add a device QR scanner and adaptive/themed launcher icon. One standalone runtime dependency, ZXing core 3.5.4, handles local QR decoding. Camera-to-form scanning, permission refusal, same-version replacement, and upgrade data preservation were tested on the Pixel 9 Pro emulator; 37 JVM tests and light/dark smoke checks pass. See [0.1.1 verification](VERIFICATION-0.1.1.md). The status below retains the original architecture and 0.1.0 history.

> **0.1.2 update:** Added a read-only public Baby Buddy demo with automatic demo-account login, JSON API-key discovery, a separate persistent cache, explicit refresh, and an offline practice option. Standalone ZXing remains the only runtime dependency, as explicitly selected by the user. See [0.1.2 verification](VERIFICATION-0.1.2.md).

> **0.1.3 update:** Removed the public demo in favor of local synthetic practice data. Functionality, simplicity, maintainability, and speed take priority over feature count. Push back on suggestions that add disproportionate complexity and offer simpler options. See [0.1.3 verification](VERIFICATION-0.1.3.md); the public-demo entry above is historical.

> **0.1.4 update:** Removed unused timeline metadata, an unused drawing option, a test-only constructor shortcut, and an obsolete local QR diagnostic. Retained indirect timer-icon callers and necessary 0.1.2 upgrade cleanup. See [0.1.4 verification](VERIFICATION-0.1.4.md).

> **0.1.5 update:** Added required-first forms, per-child remembered choices/toggles, the icon-card logging grid, Settings visibility/order controls, and a corrected sync arrow. Typed phone timers persist in SQLite and atomically become ordinary queued records on Stop & save; manual logging and existing shared-server timer finishing remain. Metric labels are the default with no conversion or confirmation banner: upstream stores untyped numbers. These local-timer/label defaults were recommended to avoid unnecessary complexity; the three explicitly confirmed choices were remembered choices/toggles only, Settings move controls, and configuring before starting. See [0.1.5 verification](VERIFICATION-0.1.5.md).

> **0.1.6 update:** New duration forms default to starting a timer; switching it off retains manual logging, and editing/finishing an existing timer does not start another. Activity move controls are stacked on the right. Sync status/errors/manual sync/pending review are grouped in Settings; the custom refresh icon and duplicate controls were removed. Confirmed from upstream API documentation and existing app code that server timers created by other clients can appear and be finished here with suitable permissions. Newly started phone timers remain local. See [0.1.6 verification](VERIFICATION-0.1.6.md).

> **0.1.7 update:** The user explicitly chose shared server timers and prioritized reliable offline operation over timing precision. New starts use the existing durable queue. Entirely offline sessions collapse to a normal completed log; confirmed shared timers save captured start/end times before durable timer cleanup. No new dependency or background service. Schema 3 retains existing local timers and outbox entries. See [0.1.7 verification](VERIFICATION-0.1.7.md).

> **0.1.8 update:** Renamed the launcher, welcome screen, About section, and Gradle project to Baby Buddy Pocket. The welcome screen prominently requires an existing Baby Buddy server; independent status and no included hosting are explicit. The package ID, database/credential names, and signing identity remain compatible with existing installations. Proposed repository name: `baby-buddy-pocket`. See [listing draft](PLAY-STORE-LISTING.md) and [0.1.8 verification](VERIFICATION-0.1.8.md).

> **0.1.9 update:** Added a brief Railway setup link to onboarding, README, and the Play Store draft, using the exact [Baby Buddy template](https://railway.com/deploy/baby-buddy-self-hosted-baby-tracker--baby-buddy) from the existing setup note. Opens in the external browser; no deployment automation, extra dependency, or long setup guide. See [verification](VERIFICATION-0.1.9.md).

> **0.1.10 publication update:** The user explicitly requested removing the former name from every repository path, identifier, and document, accepting a fresh install. The current Android identity is `com.babybuddypocket.app`, with new database and credential identifiers. Historical update claims above apply only to those earlier builds. Repository: [Felixre7/Baby-Buddy-Pocket](https://github.com/Felixre7/Baby-Buddy-Pocket). See [verification](VERIFICATION-0.1.10.md).

## Important design choices

1. **Android only.** Build a native Android client for an existing Baby Buddy server. The user's Railway server is already working; server deployment is outside this app's scope.
2. **Few dependencies and easy self-compilation.** The implementation direction is Java 17 with Android framework views, platform networking/JSON, SQLite, and Android Keystore. One standalone runtime library (ZXing core 3.5.4) supports QR decoding; no React Native, Expo, Firebase, or required developer service. JUnit and a JVM JSON implementation are test-only dependencies.
3. **Calm, simple visual style.** Take inspiration from the user's existing LiftLog app and the Baby Buddy Companion iPhone screenshots: pale background, rounded white cards, readable typography, generous touch targets, and compact controls. Feedings are green, sleep is purple, diapers use warm neutral colors, and tummy time uses orange. Make an original Android interface; do not copy the other apps' code or assets.
4. **Today dashboard and unified timeline.** The intended navigation is Today, Timeline, Trends, and Settings, with quick logging and child switching. Show recent activity and daily totals without a crowded interface. Include light and dark themes.
5. **The user's server owns the shared records.** Connect directly using its HTTPS address and Baby Buddy API token. No intermediary backend or analytics. Discover collections and writable form fields through the server's API root and OPTIONS responses, so supported fields and choices follow the installed Baby Buddy version and user permissions.
6. **Offline reading and visible queued writes.** Cache downloaded records locally and save new entries in an outbox before uploading. Clearly distinguish queued, rejected, and uncertain entries from confirmed server data. The server must not be presented as up to date after a failed sync.
7. **Do not silently duplicate records.** Baby Buddy POST requests are not assumed to be idempotent. If a request may have reached the server but its result is unknown, require review before retrying. Persist that state before sending, including protection against an app crash during delivery.
8. **Complete, safe reads.** Follow all pagination pages and replace the local snapshot only after the supported collections finish successfully. Keep the prior cache on failure. Restrict authenticated requests to the configured HTTPS origin and API path; do not follow redirects with the token.
9. **Keep private data private.** Store the API token encrypted with Android Keystore and disable Android backup. The local data cache is app-private SQLite, not an independently encrypted database. Never put real credentials in source, documentation, screenshots, or test fixtures. Clear account-specific local data when changing accounts or servers.
10. **Measurement labels default to metric.** Baby Buddy stores plain numbers, not normalized metric quantities. Labels must match the convention used for existing records. No conversion or confirmation nag. Daily totals use local calendar days and split sessions across midnight.
11. **Simple device customization.** Remember only choices/toggles, separately per child/activity and demo/family. One Settings list controls visibility and order; never delete server records when hiding an activity. Running timers and queued-entry review remain accessible.
12. **Shared timers with reliable offline starts/stops.** Configure before starting; Stop & save records times and choices durably. Publish running timers on sync. If a start was never sent, finish as one ordinary log. Otherwise upload the finished log before retryable removal of its server timer. This avoids server receipt-time overlap/24-hour validation failures. Ambiguous uploads require review; cleanup is idempotent. Preserve older phone-only timers during upgrades. Reliability, functionality, and simplicity take priority over exact timing. No extra dependencies or background scheduler.

Items 1–3 reflect explicit user requirements. The detailed architecture and behavior above are implementation choices selected during the first session; their implementation and validation are recorded in the versioned verification reports above.

## Goal and context

Create an Android app that displays and synchronizes the user's Baby Buddy server data and can be compiled easily by other users. The user has been using LiftLog and likes its interface. They also like the colors and simple layout in the iPhone Baby Buddy Companion screenshots.

- Public name: **Baby Buddy Pocket**.
- Source folder: `the repository root`.
- Android application ID: `com.babybuddypocket.app`.
- Initial version: `0.1.0` / version code `1`.
- Existing server notes: [[baby-buddy-railway-setup]].
- Work was paused on **2026-09-20** at the user's request for a few hours. No scheduled continuation was created.

## Initial pause snapshot — historical

**There is not yet a usable or validated app.** The folder was empty at the start. Build configuration, resources, and initial data/sync classes have been written, but the activity/UI and tests do not yet exist. No APK has been successfully built or installed, and the user's Railway instance has not been contacted or modified.

### Files written

| File | Current purpose |
| --- | --- |
| `settings.gradle`, `build.gradle`, `gradle.properties`, `app/build.gradle` | Single Android application module; AGP 8.13.2, Java 17, compile/target SDK 36, minimum SDK 26 (Android 8). |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Standard wrapper files, pinned to Gradle 8.13 with the official distribution SHA-256. |
| `app/src/main/AndroidManifest.xml` | Internet permission, HTTPS-only networking, backup disabled, launcher activity declaration. |
| `app/src/main/res/values/styles.xml`, `values-night/styles.xml` | Initial light/dark platform themes. |
| `app/src/main/res/drawable/ic_launcher.xml` | Original vector baby-face launcher artwork; not visually validated yet. |
| `app/src/main/java/com/babybuddypocket/app/ApiClient.java` | HTTPS/token requests, origin/path checks, redirects disabled, timeouts, bounded responses, and paginated reads. |
| `app/src/main/java/com/babybuddypocket/app/Records.java` | Collection names, record summaries, timestamp parsing, timeline merging, and daily duration overlap calculations. |
| `app/src/main/java/com/babybuddypocket/app/SyncEngine.java` | Reachability/auth check, queued POST handling, discovery, OPTIONS schema retrieval, and complete-snapshot refresh. |
| `app/src/main/java/com/babybuddypocket/app/LocalStore.java` | SQLite snapshot and outbox with queued/rejected/review states and transaction-based successful-write handling. |
| `app/src/main/java/com/babybuddypocket/app/Credentials.java` | AES-GCM token encryption using Android Keystore. |
| `.gitignore` | Excludes caches, builds, local SDK paths, signing keys, reference downloads, and artifacts. |
| `docs/PROJECT-HANDOFF.md` | Repository copy of this handoff. |

`local.properties` contains this machine's SDK location and is intentionally ignored. Reference screenshots and upstream API/model material were downloaded into the ignored `.reference/` directory for inspection only.

### What was verified

- Read the Baby Buddy API documentation and current upstream serializers/model definitions.
- Inspected the actual iPhone dashboard and timeline screenshots, not just the App Store description.
- Inspected the local LiftLog theme at `C:\git\LiftLog\app\src\hooks\useAppTheme.tsx` for spacing and theme context; no LiftLog code was copied into the app.
- Confirmed Android SDK 36 and Java 17 are installed on this machine.
- Downloaded Gradle 8.13 and recorded its official distribution SHA-256 in the wrapper configuration.

### Build attempt and limits

Attempted command from the source folder:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

The first sandboxed attempt failed creating the Gradle lock file under `C:\.gradle`. An approved outside-sandbox retry downloaded Gradle, but then failed with:

```text
java.io.IOException: Unable to establish loopback connection
```

This happened during Gradle startup, before application compilation. The build process has ended. No unit tests, lint checks, emulator runs, device installations, or server integration checks have passed. Toolchain compatibility and the new Java source still require a successful build.

## Next steps when resuming

1. **Finish the native UI.** `MainActivity` is referenced in the manifest but does not exist yet. Build onboarding (server address/token), child selection, Today dashboard, timeline, filtering, pending-entry review, settings, and friendly loading/empty/error states. Use Android insets, accessible labels, and large touch targets. Add an explicitly labeled sample-data mode for previewing without server credentials.
2. **Wire the data layer to the lifecycle.** Use a single worker for networking. Show cached data immediately, refresh on resume and manually, and consider a modest foreground refresh interval. Background sync is not implemented or promised. Guard against activity recreation and stale callbacks, and serialize disconnect/account switching with active sync.
3. **Implement logging forms from the cached OPTIONS schemas.** Support dates/times, choices, numeric values, booleans, notes, and tags. Preserve a form on validation failure. Handle read-only users and unsupported collections. Start with feedings, sleep, diapers, tummy time, pumping, notes, and growth measurements; medication support is conditional on the server exposing it.
4. **Finish outbox review.** Show server rejection details and uncertain-delivery warnings. Retry only by explicit user action when delivery is uncertain, and let users discard local pending entries without deleting server data. Verify that queued writes remain visible across restart and offline use.
5. **Decide timer behavior.** Timer collections can be downloaded by the scaffolding, but timer UI/start/stop behavior is not implemented. Baby Buddy supports consuming a timer by sending its ID with a duration record; this overrides start/end/child and may remove the timer. Test cross-device behavior and avoid duplicate records after ambiguous requests. Do not claim live timer support yet.
6. **Implement useful trends and daily totals.** Base calculations on confirmed records, make pending status clear, and test cross-midnight and daylight-saving boundaries. Unit preferences must match the server's actual settings.
7. **Review and test the new data layer.** Specifically cover full pagination, failed refresh preserving the cache, optional/missing endpoints, token/path/redirect safety, account isolation, outbox persistence, rejected writes, ambiguous POSTs, and app restart during delivery. Check database write failures: `LocalStore.replace()` currently does not verify the return value of `insertWithOnConflict`, which must be addressed before calling the snapshot commit reliable.
8. **Resolve build startup and verify the final tree.** Retry Gradle with appropriate local permissions; inspect the loopback error if it persists. Run compile, unit tests, and lint. `app/build.gradle` names `SmokeInstrumentation`, which does not exist yet: implement a suitable test runner or remove that declaration until tests exist. Do not treat the current scaffold as launchable.
9. **Inspect the real Android screens.** Use an emulator or explicitly authorized device install to check layout, keyboard/insets, dark mode, large fonts, and rotation. Do not substitute an HTML mockup for verification of the actual Android app.
10. **Deliver a buildable repository and APK.** Write a clear README with Android Studio and command-line build steps, setup, token retrieval, supported features, sync limits, privacy behavior, and signing instructions. Choose/document a project license before public distribution. Run final checks after the last edit. No repository commits, remote publication, or license choice have been made yet.

## References

- [Baby Buddy API documentation](https://docs.baby-buddy.net/api/)
- [Baby Buddy source](https://github.com/babybuddy/babybuddy)
- [Baby Buddy API serializers](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py)
- [Baby Buddy Companion on the App Store](https://apps.apple.com/us/app/baby-buddy-companion/id6788966667)
- [Baby Buddy Companion website and screenshots](https://babybuddy.app/)
- [LiftLog source](https://github.com/LiamMorrow/LiftLog)

## Resume prompt

> Resume the Baby Buddy Pocket Android app in the repository root. Read docs/PROJECT-HANDOFF.md first, inspect the actual files, and continue from the tested native Java app. Keep runtime dependencies minimal. Follow the documented LiftLog/Baby Buddy Companion visual direction, finish server connection and sync UI, handle offline and uncertain writes carefully, and deliver a genuinely built and tested APK with simple self-build instructions. The Railway server already exists; no deployment work is needed.
