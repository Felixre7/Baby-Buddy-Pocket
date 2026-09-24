# Baby Buddy Pocket 0.1.19 verification

Version **0.1.19 / code 20**, 2026-09-22.

## Implemented behavior

- Running timers have a quiet Android notification with an elapsed-time counter and a tap back to Today for the timer's child. Local/shared representations are deduplicated; different children retain separate notifications. Stop/Cancel removes the display. Android renders the counter, with no ticking service, scheduler, new dependency, or added background network polling.
- Android 13+ asks for notification permission when a real timer is available. Refusal does not prevent logging. Settings opens Android's notification controls. A private notification/public lock-screen version hides child and activity details when Android redacts sensitive content.
- Reboot/app-update receivers restore notifications from saved state after credential storage is available. Remote completion is reconciled by the existing foreground sync. Android can dismiss/suppress notifications; force-stop can prevent restoration until the app is reopened. The durable timer is independent of notification visibility.
- Activity details offer Delete activity with deliberate confirmation of the shared, irreversible effect. The existing outbox stores the original record and ID without a schema migration. Sync compares server values before DELETE; changed records require review, missing records clear the request, and lost replies/interrupted writes remain uncertain without automatic retry.
- Pending deletion cards stay directly reviewable. Discarding the request does not restore a removed server record. An existing pending edit/deletion blocks another change to the same record. Confirmed totals remain based on server data until reconciliation. No Undo or Repeat feature was added.
- A server read followed by DELETE is not an atomic cross-device lock. No client-only guarantee against simultaneous caregiver changes is claimed.

## Validation

- **73 JVM tests pass**, including nine deletion regressions: original identity, already-missing records, caregiver/child conflicts, offline preflight, permissions, uncertain HTTP failures, lost replies, failed local commits, and pending-card presentation.
- Debug/instrumentation/release APK and release bundle builds pass with JDK 17. Debug lint: **zero errors, six existing warnings**.
- **Pixel 9 Pro profile, Android 16/API 36:** focused storage/UI suite passes **117 assertions**. Covers SQLite reopen, one pending action per record, deletion confirmation and discard, offline requests, direct conflict review, chronometer start time, quiet/private notification properties, notification taps, multiple children, and stop/cancel cleanup. Actual notification and deletion screenshots reviewed.
- Notification lifecycle checks pass on Android 16/API 36: permission refusal (6 assertions), process restoration with original start time (4), offline finish (3), and completed-log restart (3). A normal app launch followed by an actual emulator reboot delivered the boot receiver and restored the notification, confirmed in system notification/broadcast state. An ordinary background process kill left the notification present.
- Initial UI-test failures were harness issues: the new permission dialog needed handling, Android adds an automatic group summary to active notifications, and the lifecycle fixture needed the app's normalized URL. A boot check started instrumentation before broadcast delivery and caused Android to skip that receiver; the normal-launch reboot check above passed.
- After initial APK delivery, the full Pixel 9 Pro Android 16/API 36 suite passed 327 assertions and the small 320 dp/dark/200% text suite passed 117. Screenshot review then found clipped activity-detail action buttons despite those passing checks. The corrected layout and visible-label regression coverage are delivered separately in 0.1.20; use the newer build. These original 0.1.19 artifacts remain unchanged.
- **Android 17/API 37 runtime validation is blocked on this computer.** Tried stable-channel 37.0 and 37.2 REL images and several graphics settings; the emulator's SurfaceFlinger repeatedly aborts with hasReadColorBufferDma before stable UI startup. This is not an app test pass. The earlier 0.1.18 API 37 results belong to that earlier build and machine.
- APK signature verified. GPL/project and ZXing license/notice assets are present in the APK and AAB; no native libraries are bundled. No family-server records were read or changed. Physical-phone and older-Android checks remain open.

## Artifacts and signing limitation

- Test APK: artifacts/Baby-Buddy-Pocket-0.1.19-new-key-test.apk.
- SHA-256: f6d820f0a74f3bef272e0acb70fc911b47679d9a0b18337dd4db62bf4a2ca7fc.
- Unsigned APK: artifacts/Baby-Buddy-Pocket-0.1.19-unsigned.apk.
- Unsigned bundle: artifacts/Baby-Buddy-Pocket-0.1.19-unsigned.aab.
- Matching source: artifacts/Baby-Buddy-Pocket-0.1.19-source.zip, including ZXing core 3.5.4 source.
- Test evidence: artifacts/v0.1.19/.

**The test APK uses this computer's new debug key and cannot update the previously delivered installation.** Its certificate SHA-256 is 9c83e6e34257b011486e5f52a92a3f3081d8dd79faf8bf6295917d2260c5d333. The prior signing key is not available here; an update-compatible APK still requires that original key. Do not uninstall an existing installation to bypass a signature mismatch, because that removes local pending work. The application ID remains com.babybuddypocket.app; no migration or identity change was introduced.

Use the new-key APK only on a separate clean test installation. Keep its corresponding source with it. No Google Play publication or remote source push was performed.

## Focused reproduction

Use a clean emulator; the runner rejects personal credentials.

    adb -s EMULATOR_SERIAL shell am instrument -w -e features true com.babybuddypocket.app.test/com.babybuddypocket.app.SmokeInstrumentation

The timerLifecycle phases seed-denied, restore, finish, and verify-finished use a guarded .invalid synthetic connection. The first phase needs notification permission reset on Android 13+. Re-enable permission before restore. Perform reboot/ordinary process-kill observation outside instrumentation, since Android force-stops the instrumented app at test boundaries. Never use these fixtures on a personal installation.