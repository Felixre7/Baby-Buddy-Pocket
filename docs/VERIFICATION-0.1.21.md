# Baby Buddy Pocket 0.1.21 verification

Version **0.1.21 / code 22**, 2026-09-24.

## Changes

- On supported Android 16+ devices, locally started timers request a native Live Update chip. Android renders elapsed time from the saved start, with no static critical text overriding the chronometer. Caregiver-only timers retain ordinary notifications.
- Existing low-importance channel, private lock-screen content, tap destination, local/shared deduplication and multi-child notifications remain. The existing settings shortcut opens app notification controls on Android 16+ and channel controls on older versions.
- Explicit immutable dismissal intents target the non-exported timer receiver. Promotion suppression is persisted for the local timer's ID/start/mode, survives publication and restart, and is pruned when the timer ends or the account disconnects. No database migration, background service, new dependency, polling or SDK change.
- Shared relative-age formatting uses just now below one minute, completed minutes below one hour, hours and completed minutes until 24 hours, then completed days. Whole hours include 0m. No 12-hour cutoff or rounding up. Original timestamp selection and refresh behavior are unchanged.

## Validation at packaging

- **74 JVM tests pass**, including deterministic age boundaries, future timestamps and no rounding up.
- Debug and instrumentation APK builds pass with JDK 17. Lint: **zero errors, four warnings**.
- Pixel 9 Pro profile, **Android 16/API 36**: focused suite passes **131 assertions**. Tests cover age labels, local-only promotion requests, persistent dismissal, identity through server publication, shared fallback, multiple children, stop/cancel cleanup and existing deletion flows.
- Same APK, **320 dp, dark theme, 200% text, Android 16/API 36**: focused suite passes **131 assertions**. Light/dark history screenshots and actual final-character visibility reviewed.
- The base API 36 image does not grant promotion in this configuration: these runs verify ordinary-notification fallback, not a visible chip. Home screenshots show the ordinary timer icon.
- Android 16/API 36 lifecycle passes: notification refusal (6 assertions), restored original timer start (4), dismissal callback/persistence (3), dismissal surviving a separate app process (5), offline finish (3), finished-log restart (3). After an actual emulator reboot, the timer notification was present before instrumentation ran again.
- Android 17/API 37.2 REL and Android 16/API 36.1 REL emulator images could not maintain stable System UI: SurfaceFlinger/mapper.ranchu fail with hasReadColorBufferDma. Graphics-feature retries did not resolve it. These are environment failures, not app-test passes. Physical chip verification is pending at this packaging checkpoint.
- The phone-build helper verifies the same debug certificate as the user's installed 0.1.20: **9c83e6e34257b011486e5f52a92a3f3081d8dd79faf8bf6295917d2260c5d333**. An in-place update is supported; no uninstall or identity change is needed.

## Artifacts and limits

- Installable signed APK: artifacts/Baby-Buddy-Pocket-0.1.21-debug.apk.
- APK SHA-256: 9812222cc518b30e03f7c51873900472c76bdae71a67bbe03f69e577b1203c23.
- Matching source: artifacts/Baby-Buddy-Pocket-0.1.21-source.zip, including ZXing core 3.5.4 source.
- Evidence: artifacts/v0.1.21/.
- Android controls chip visibility, layout and the number of chips shown. Notification permission and Live Update settings can suppress the display; the saved timer remains usable. Remote changes continue to depend on existing foreground sync.
- No personal-server records were used by automated tests. Physical-phone chip/long-duration/privacy confirmation and older-Android checks are not yet complete at this checkpoint.
- This source includes the preceding 0.1.19/0.1.20 timer notification, synced deletion, accessibility and signing-workflow changes.

## Follow-up validation after APK delivery

- The identical signed APK passes **341 full regression assertions** on Pixel 9 Pro, Android 16/API 36.
- **Android 8.0/API 26** passes **130 focused assertions**, covering ordinary-notification fallback and the changed ages alongside deletion regressions. The initial run checked cancellation before Android completed its asynchronous notification update. Adding the existing UI-settling pause to two test assertions resolved it; no production code or APK changed.
- APK SHA-256 remains the value above. Bundled GPL/project and ZXing license notices were verified. Matching source includes the updated test harness and this report.
- Visible Live Update chip advancement and physical lock-screen appearance remain unverified. At this checkpoint the physical phone was USB-unauthorized; installation was subsequently verified below. Newer emulator images have graphics/boot failures with the installed engine; an isolated checksum-verified emulator 36.6.11 retry with Android 16/API 36.1 reproduced the same SurfaceFlinger/mapper.ranchu assertion even with 4 GB memory. The device reached ADB but did not provide stable System UI. These emulator attempts did not verify the visible chip.

## Physical installation follow-up

- The same signed APK was installed with ADB install -r over the existing app on the authorized Pixel 9 Pro. ADB returned Success, and the installed package reports versionName 0.1.21 and versionCode 22.
- The physical phone reports **Android 17/API 37**. No uninstall or data-clear operation was used. This confirms update/signing compatibility, not visual chip advancement or lock-screen behavior; those checks remain open.
## Reproduction

Only on isolated synthetic test installations:

    adb -s EMULATOR_SERIAL shell am instrument -w -e features true com.babybuddypocket.app.test/com.babybuddypocket.app.SmokeInstrumentation

The timerLifecycle phases seed-denied, restore, dismiss, restore-dismissed, finish and verify-finished use a guarded .invalid fixture. Reset notification permission before seed-denied and grant it before restore. Observe boot restoration outside instrumentation to avoid its force-stop behavior.
