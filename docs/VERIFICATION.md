# Verification — 0.1.0

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

For the newer QR scanner/icon build, see [0.1.1 verification](VERIFICATION-0.1.1.md). This document retains the original build and live-sync evidence.

Build/UI verification: 2026-09-20. Live Railway sync verification: 2026-09-21 (America/Phoenix).

## Results

- `assembleDebug`: passed; launchable `PRE_PUBLICATION_PACKAGE.MainActivity`.
- `assembleRelease`: passed; unsigned release APK, as documented.
- `assembleDebugAndroidTest`: passed.
- `testDebugUnitTest`: **30 passed, zero failures/errors** (10 API, 11 records/forms/units, 9 sync-policy tests).
- `lintDebug`: **zero errors, eight warnings**. Three warn about newer target/tool/test-library versions; five concern small icon-path allocations during drawing. No lint baseline or global suppression was added.
- Platform instrumentation on an **Android 16 / API 36 x86_64 Pixel 7 emulator**: **20 assertions passed** in light mode at normal font scale, and again in dark mode at **1.3× font scale**.
- Repeated on the **Pixel 9 Pro** emulator profile (`pixel_9_pro`, AVD `local Pixel 9 Pro test profile`): Android 16 / API 36 AOSP x86_64, **1280 × 2856, 480 dpi**. **20 assertions passed in each mode**: light at 1.0× text and dark at 1.3× text. Installed the delivered debug APK listed below; no app code changes were needed. This validates the emulator configuration, not physical Pixel hardware.
- Visually reviewed the actual emulator screenshots: connection page, Today, timeline, trends, settings, logging form, and rotated form. No HTML mockups were substituted for Android verification.
- The debug APK's v2 signature verifies with Android SDK `apksigner`. Package metadata verifies version `0.1.0`, code `1`, minimum SDK `26`, target SDK `36`, label **the Android prototype**.

The optional Windows build helper was exercised successfully. It resolves this host's Java Unix-domain socket startup error without changing global environment settings.

## Live Railway sync

The delivered APK was tested against the user's Railway deployment on the Pixel 9 Pro / API 36 emulator. **15 read-only assertions passed**, followed by **22 assertions in the upload verification run**. The test downloaded one child and 23 activity records across 13 collections, and discovered 13 writable form schemas.

Verified HTTPS authentication, the connected dashboard, manual refresh, encrypted token storage, complete snapshot persistence after reopening SQLite, and retention of that downloaded snapshot after a simulated network failure. The upload run created one uniquely labeled temporary note through `AppController.add` and the real outbox/`SyncEngine`, verified exactly one matching record through a separate server API read, checked its child/content and local cache, deleted only that test note, and verified its absence from the server and refreshed cache. Existing server records were not edited or deleted.

The opt-in test runner clears its staged credential input before connecting and disconnects afterward. Emulator credentials and downloaded cache were confirmed cleared. No real key or family record contents are included in source, result logs, or screenshots. The app's production code and delivered APK did not change; only test tooling and documentation changed. Sanitized results are in `artifacts/pixel-9-pro/live-read-results.txt` and `live-sync-results.txt`.

## Tested behavior

- HTTPS origin/API-path validation, unsafe pagination links rejected before another request, full-page iteration, relative-query pagination, unpaginated collections, pagination cycles, malformed JSON, token-header injection rejection.
- Full snapshot replacement only after a complete refresh; previous cache retained on failed reads; absent newer collections; users with no writable OPTIONS actions.
- Write reachability precheck, persisted uncertainty before POST, confirmed success, definite rejection, timeout, server error, and storage failure after an apparent successful upload; none of the uncertain outcomes automatically repeats a POST.
- Per-child timeline isolation, absolute timestamp ordering, midnight splitting and a DST boundary, independent unit labels, unconfirmed server units, field choices, tags, numeric validation, future dates, duration order/limit, timer substitution.
- On Android: SQLite outbox/payload persistence across database reopen, uncertain-state persistence, accepted record transaction, consumed timer removal, local discard preserving server data, local disconnect clearing cache/outbox, and Android Keystore encryption/decryption.
- Actual UI navigation and creation of a synthetic note, draft preservation through rotation, and the saved note appearing in the timeline.

## Boundaries

The live integration check covers the user's current Railway deployment over the emulator's network. Other server versions, permissions, and physical-phone networks are not established by this run. The ordinary Android smoke test still uses synthetic local data; JVM API/sync edge cases use synthetic transport responses.

The remaining user acceptance check is to confirm the server's measurement units and compare the displayed histories with the web interface. Only a note was uploaded during live testing; feeding/sleep/timer writes and all schema-generated forms were not individually exercised against production. Do not put a real token in source or this document.

Cross-device timer races, production-sized histories, custom reverse proxies, and all older Android releases have not been tested end to end. Minimum Android 8.0 compatibility is based on compilation/lint and API selection, not a device run on every supported OS. HTTP-only and untrusted/self-signed TLS deployments are intentionally unsupported.

Widgets, notification/background timers, scheduled background sync, biometric app locking, photos, and editing/deleting existing server records are outside this version. Refer to the README for sync semantics and the role of the server web UI.

## Artifact

- Debug APK: the locally archived debug APK (copy of Gradle output).
- Size: **80,953 bytes**.
- SHA-256: `ed9c359cbd1a96574fff792707cbf31a55ddd9f6d2fc7421e53e72131295c976`.
- Source archive: the locally archived source ZIP; excludes SDKs, caches, signing keys, machine-specific SDK configuration, reference downloads, screenshots, and generated builds.
- Final screenshots: `artifacts/screenshots/screenshots/final-light-*.png` and `final-dark-large-*.png`.
- Pixel 9 Pro screenshots: `artifacts/pixel-9-pro/screenshots/pixel9-light-*.png` and `pixel9-dark-large-*.png` (seven per mode). Reviewed the connection page, dashboard, timeline, trends, settings, and portrait/landscape note form; no layout issue requiring a change was found in those views. At larger text sizes, longer pages scroll normally.
- Pixel 9 Pro raw test results and device settings: `artifacts/pixel-9-pro/light-results.txt`, `dark-large-results.txt`, and `device.txt`.

Build outputs are ignored by Git. A future rebuild may produce a different hash; the hash above identifies the delivered artifact, not a promise of byte-for-byte reproducibility across toolchains or signing keys.
