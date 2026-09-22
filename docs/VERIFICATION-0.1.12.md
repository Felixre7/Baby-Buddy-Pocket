# Baby Buddy Pocket 0.1.12 verification

Verified 2026-09-21. Version **0.1.12 / code 13**.

## Timer recovery and controls

The reported rejection was HTTP 400 on `start`: "Date/time can not be in the future." The app previously sent the phone's wall clock unchanged. Sync now uses the authenticated server's HTTP Date header and monotonic elapsed time to cap future timer timestamps conservatively behind server time. A wholly offline session retains its duration when shifted; a confirmed shared start is preserved. Past timestamps remain unchanged. Missing Date headers leave the existing timestamps unchanged, so a misconfigured proxy/server can still require investigation.

A definitely rejected start can now become a completed activity when stopped. Previously saved stops waiting on rejected starts recover on launch; running starts rejected specifically for a future timestamp become eligible for a safe retry. Ambiguous writes remain reviewable and are never retried automatically. Atomic queue claiming prevents a stale sync snapshot from uploading a start after local cancellation or completion. Finishing uses the confirmed start even if confirmation arrives just before the Stop tap.

Today shows running timers with Stop & save, Edit times, and Cancel timer. Finished/cancelled work and sync explanations stay in Settings. Cancellation persists offline; a confirmed shared timer is removed through guarded, retryable cleanup without saving an activity. A cancelled start with an unknown outcome retains its review state. Database schema 4 adds cancellation intent without deleting existing data.

## Local problem reports

Settings and the connection screen offer Report a problem, with preview, Copy, Share through Android's chooser, GitHub issues link, and Clear. Reports include app/OS/device/layout information, aggregate pending counts, and allowlisted technical events. Exception messages, raw responses, credentials, server URLs, child/record identifiers, and record contents are excluded. HTTP status, recognized validation field names, exception types, and limited app stack frames remain useful for diagnosis.

The app-private history is capped at 100 events and 64 KiB; events older than seven days are pruned when accessed. Disconnect clears it. Logging failures cannot change the outcome of sync or a saved record. No new runtime dependency, automatic uploader, analytics service, background scheduler, or custom uncaught-crash handler was added. This release captures sync/timer events and caught failures, not every crash or ANR.

## Build validation

- Debug, instrumentation, unsigned release APK, and release AAB builds pass.
- **48 JVM tests pass**, including synthetic future-time rejection, duration preservation, shared-start handling, missing Date headers, diagnostics privacy, storage bounds, restart, expiry, and unavailable storage.
- Debug/release lint: **zero errors, six existing warnings**.
- Google bundletool 1.18.3 validates the unsigned AAB. APK metadata confirms the existing application ID, minimum API 26, target API 36, and version code 13. The debug signing certificate is unchanged.

## Final APK emulator checks

Every configuration below passed **154 assertions** on the APK hash recorded below, with networking disabled and synthetic data only.

| API | Profile | Configuration |
| --- | --- | --- |
| 26 | Nexus One | Android 8, x86, small screen, light |
| 28 | Pixel 3 XL | Android 9, x86_64, light |
| 36 | Pixel 9 Pro | Android 16, x86_64, light |
| 36 | Small phone | 480 × 800 at 240 dpi, 320 dp width, dark, 200% text |
| 37 | Pixel 10 Pro | Android 17, x86_64, gesture navigation |

Coverage includes rejected starts before/after Stop, saved-stop migration from schema 3, restart, uncertain delivery, durable cancellation, late start confirmation, stale queue claiming, synthetic server cleanup, UI navigation/forms/rotation, timer controls, report redaction, actual clipboard contents, and intercepted system-share chooser dispatch. No message was sent. Synthetic timer and report screenshots were inspected on older and current profiles. Crash buffers were empty.

An earlier small-screen test tapped Android's temporary clipboard overlay rather than the app button. The test now waits for the overlay to disappear before the next real touch. That configuration passed on rerun with the unchanged final app APK; the other four passed before this test-only delay was added. This suite does not assert that a third-party sharing app delivered a report.

## Scope and limitations

One authorized, read-only OPTIONS request confirmed that the configured server exposes writable timer fields and an HTTP Date header. No family records were read or written during this investigation, and no live timer was created, finished, or deleted. Synthetic tests reproduce the rejection; actual phone/server recovery still needs user confirmation. Database migration testing is distinct from an end-to-end installation upgrade with a real account. The debug APK can update the matching 0.1.10/0.1.11 test installation without uninstalling.

The broader 0.1.11 compatibility and QR matrix remains historical evidence; it is not a claim that every configuration or camera test was rerun for 0.1.12. Nothing was published to Google Play.

## Artifacts

- Debug APK: `artifacts/Baby-Buddy-Pocket-0.1.12-debug.apk` (484,189 bytes).
- APK SHA-256: `1421c4816a6bb4612771ce8252b03a6b8ae66df29f8d1d2d2735b26c07f1ad89`.
- Source ZIP: `artifacts/Baby-Buddy-Pocket-0.1.12-source.zip`.
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.12-unsigned.aab`.
- Local emulator results and synthetic screenshots: `artifacts/v0.1.12-final/`.
