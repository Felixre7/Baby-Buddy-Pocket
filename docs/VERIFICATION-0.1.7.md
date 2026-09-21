# the Android prototype 0.1.7 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.7 / code 8**.

## Changes and design

- New timers are created on Baby Buddy through the existing durable upload queue. Configured choices stay on the creator phone for one-tap **Stop & save**. Other authorized clients can see the timer; timers created elsewhere remain finishable with **Finish & log…**.
- Starting/stopping works offline after initial connection. If an entire session ends before its start is sent, one SQLite transaction replaces that start with an ordinary completed activity. This avoids unnecessary server timers. A stop during an in-flight start is retained until its confirmed server ID arrives. Uncertain starts remain reviewable and are never matched heuristically or blindly retried.
- For confirmed shared timers, save explicit start/end times, then remove the timer only after the activity upload is confirmed. Both the successful record and cleanup request are committed atomically to SQLite. Cleanup retries safely after a lost reply or 404 without uploading the activity again.
- This differs deliberately from the API's `timer` shortcut: the [serializer](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py) overwrites end with receipt time, while the [models](https://github.com/babybuddy/babybuddy/blob/master/core/models.py) validate non-overlapping periods and a maximum 24-hour duration. Replaying multiple offline stops with receipt times could reject valid sessions. Preserving captured times avoids that failure.
- Check whether a shared timer was already removed or changed before submitting its completed record. Re-check its start and child before delayed cleanup so a timer restarted by another caregiver is retained for review. Block repeated local stop taps. Prune stale local setup after another caregiver removes a timer. Keep pending operations visible in Settings and relevant timer cards.
- Schema 3 extends the existing two queues with request/link metadata. Old queued uploads retain POST semantics; existing phone-only timers stay local and can finish normally. Removed new-phone-only creation behavior and the obsolete demo discard branch; retained necessary legacy finish/edit/discard and queued-consumption compatibility.
- No dependencies, services, alarms, or background scheduler added. Net production Java change: **+379 lines** versus saved 0.1.6 source, including formatting/comments, across six existing classes. The extra code supports durable offline actions, ordered cleanup, migration, and pending UI.

## Verification

- Debug, unsigned release, and instrumentation builds pass. **39 JVM tests**, zero failures; **0 lint errors**, **7 existing warnings**.
- Final Pixel 9 Pro / AOSP Android 16 API 36 with networking disabled: **77 assertions pass in light mode** and **77 in dark mode at 1.3× text**. Final timer screenshots inspected in both themes.
- Native tests exercise real SQLite and SyncEngine with a synthetic API transport enforcing overlap and duration rules: unsent offline starts, whole offline sessions, restart persistence, repeated stops, transaction rollback on storage failure, shared timer visibility, confirmed-upload-before-cleanup ordering, lost cleanup reply/404 retry, remote timer disappearance/restart, ambiguous start responses, and stopping during an in-flight start.
- UI tests cover default/manual timer forms, one-tap stop, activity hiding/reordering, remembered choices, navigation/rotation, Settings-only sync controls, offline pending starts, sequential offline sessions, confirmed shared timers, and pending shared stops. An accessibility assertion initially read the screen before redraw; instrumentation now waits for it. Test expectations were updated for the final whole-offline-session behavior.
- Actual **0.1.6 → 0.1.7** upgrade: **7 checks pass**, preserving encrypted credentials, URL, settings, cache, queued record, and a running old phone timer that still finishes without publishing a new timer.
- APK signature verified: same certificate as prior releases, `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.

## Boundaries

No family-server writes, real iPhone interaction, physical-phone test, or camera rerun in this release. The API fixture checks the documented protocol and important failures; it is not a live server integration test. The observed iPhone behavior is consistent with server timers, but that app's implementation and the specific event were not inspected.

Activity creation and timer deletion are separate server requests, not an atomic server transaction. Simultaneous caregiver actions can still conflict; uncertain uploads require manual review. Cleanup needs timer-delete permission; a failure leaves the uploaded activity intact. A genuinely over-24-hour session or a conflict with another actual record still requires correction on the server. Pending work survives restart, but uploads run only while the app is open/resumed or Sync now is tapped. Nothing can share across devices while offline.

## Artifacts

- APK: the locally archived debug APK (463155 bytes).
- APK SHA-256: `1f2edfe64ba24cbcd8f971791d74d39a6a681da7b3dbcad41765db6ca8d3e62e`.
- Source: the locally archived source ZIP.
- Logs/screenshots: `artifacts/v0.1.7/` (excluded from source archive).

Install as an update without uninstalling to preserve the saved connection and local data.
