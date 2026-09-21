# the Android prototype 0.1.5 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.5 / code 6**.

## Changes and decisions

- Required form fields appear before optional fields, including start/end when no server timer supplies them. Remember only choices/toggles per child/activity; amounts, measurements, notes, and dates are fresh. Demo activity preferences are separate from family preferences.
- Log activity opens a rounded icon-card grid. One Settings list has visibility checkboxes and Move up/down controls. Order applies to the grid, quick actions, supported summaries, filters, and trends. Hidden activities disappear from these surfaces without changing server records or stopping their sync. Running timers and pending-entry review remain accessible.
- Configure feeding, sleep, tummy time, or pumping before starting a phone-local timer. Stop & save captures the end time immediately, even offline. SQLite schema 2 adds one active-timer table; stopping atomically removes its row and enqueues the normal completed activity. Repeated stops cannot enqueue another copy. Manual logging, editing timer times, and explicit discard remain available. One running timer per child/activity prevents accidental duplicate starts.
- Existing shared server timers still use Finish & log; new generic server-timer creation is replaced by the typed phone flow. Retained the server timer icon/consume support for shared timers and older queued entries.
- Removed the unit-confirmation banner, button, configured flag, and obsolete unknown-unit branch. Labels default to metric, with existing label preferences preserved. **No numeric conversion.** Fixed the sync arrowhead.
- No new dependencies, services, alarms, or background workers. One small activity-preference helper and one database table support the new behavior; net production Java change is **+390 lines**, including formatting/comments/blanks, versus the saved 0.1.4 source. This is feature work, not a code-size reduction.

The user explicitly selected remembered choices/toggles only, Settings move controls, and configuring before starting. Phone-local timers and label-only units were recommended as the simpler approach and used as stated defaults while follow-up preference questions remained unanswered. Separate display/server unit conversion and shared newly running timers are **not** implemented.

## Upstream findings

[Baby Buddy's models](https://github.com/babybuddy/babybuddy/blob/master/core/models.py) store measurement numbers without an enforced metric representation. The [API serializers](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py) do not expose measurement-unit metadata or normalize them. Existing numeric records therefore cannot safely be assumed metric. The settings describe labels, not conversion.

The [timer API](https://docs.baby-buddy.net/api/#timer-field) consumes a shared timer when the server receives the activity. The current serializer sets its end using server time, so offline one-tap stopping is implemented by capturing explicit start/end locally and uploading an ordinary activity instead. Running phone timers are not shared across caregivers.

## Verification

- Debug, unsigned release, and instrumentation APK builds pass. **39 JVM tests**, zero failures/errors. **0 lint errors**, eight existing warnings.
- New unit coverage checks required-first ordering, conditional start/end requirements, and excluding amounts/text/dates from remembered choices.
- Pixel 9 Pro / AOSP Android 16 API 36, networking disabled: **48 assertions pass in light mode** and **48 in dark mode at 1.3× text**. Navigation, grid, manual logging, rotation, system Back, configured timer start/one-tap stop, activity Settings movement/hiding across all four surfaces, remembered feeding choices, and a fresh amount field are covered.
- Native SQLite checks cover active-timer persistence across close/reopen, preserved options and captured offline stop time, atomic move to outbox, duplicate-stop rejection, and disconnect cleanup. An injected database insertion failure proves rollback preserves the active timer and creates no partial queued entry.
- Existing cache/outbox restart checks, consumed shared-timer cleanup, preference/child/demo isolation, and Keystore encryption pass.
- **0.1.4 → final 0.1.5 APK update: five preservation checks pass** for the encrypted synthetic token, URL, preference, cached marker, and unsent entry. The old schema upgrades without clearing them.
- Inspected actual screenshots of the grid, required-first feeding form, phone timer, and scrolled Settings controls, including dark mode/larger text. Scanner resource strings are preserved.
- Signature verified; certificate SHA-256 matches earlier releases: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.

An early UI-test assertion treated Android's empty-field hint as a value. The screenshot showed the amount blank; the assertion now checks `isShowingHintText`, and both final runs pass. Initial build issues were corrected before the successful production build. Later changes were tests/documentation only.

## Limits and artifacts

- No family-server requests, new live-server writes, or physical-phone tests in this batch. Existing sync transport and uncertain-POST handling are reused; the real server can still reject an entry after an offline save. New timers were tested with synthetic data and native storage, not a new live feeding/sleep upload.
- Phone timer start/end uses the device wall clock, which should be accurate. Sessions longer than 24 hours need Edit times; the timer remains available if saving fails. No timer notifications. Demo entries/timers reset on process restart.
- This forward database migration is tested; installing an older app over schema 2 is not supported. Install the new APK as an update without uninstalling. Proton Drive's physical installer flow remains phone testing.
- APK: the locally archived debug APK.
- APK SHA-256: `c24c51247d1770211ef9881cfc045de06ac35cd677cd04bab42066907586684f`.
- Source: the locally archived source ZIP.
- Logs and screenshots: `artifacts/v0.1.5/` (excluded from the source archive).
