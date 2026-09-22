# Baby Buddy Pocket 0.1.15 verification

Version **0.1.15 / code 16**, 2026-09-21. Includes all [0.1.14 rejection/correction improvements](VERIFICATION-0.1.14.md).

## Implemented behavior

Historical activities now offer Edit activity using the same form as pending corrections. Original choices, times, notes, measurements, and tags are filled in. Text/tags/nullable values can be explicitly cleared. Queued local activities can also be edited; timer operations and uncertain writes still require review. Editing requires a cached writable schema and suitable server change permissions.

The existing SQLite queue saves historical edits offline and survives restart. Schema 6 adds one original-record field; it supports comparisons and keeps one pending edit per activity. Pending edits replace their displayed activity card rather than appearing as a second record. Confirmed daily totals/trends still use server data. Deleting a pending edit removes only that edit and reveals the confirmed activity again.

Sync reads the current activity, then PATCHes only changed fields. Unrelated caregiver changes are preserved. A conflicting change to the same fields, a reassigned child, or a removed record stops for review. Users can delete the pending edit, sync, and edit the latest record. Unknown-outcome updates are not automatically retried; explicit retry recognizes an already-applied change without sending it twice. GET/PATCH are separate requests, so another client can still race between them: there is no atomic server lock.

Activity cards on Today, Timeline, and recent measurements have small status symbols: synced check, local circle, attention exclamation mark, or demo diamond. Each has an accessibility label and tooltip. Pending cards open their specific details/correction controls. The large pending footer was removed. No new runtime dependency, icon package, or service.

The upstream [activity viewsets](https://github.com/babybuddy/babybuddy/blob/master/api/views.py) and [partial-update serializers](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py) support this update approach. These are upstream sources, not a claim about the exact revision of a private deployment.

## Validation

- **64 JVM tests pass.** New coverage: field-only writes, preserved unrelated changes, conflict/removal refusal, lost replies and explicit retry, failed preflight leaving work queued, display overlays without duplicates, and clearing optional values.
- Debug/instrumentation/release APK and AAB builds pass. Debug/release lint: zero errors, six existing warnings. Bundletool 1.18.3 validates the AAB. APK signature and package identity remain unchanged; minimum API 26, target API 36.
- **Before first APK delivery:** Pixel 9 Pro, Android 17/API 37, x86_64 REL image, passed **226 assertions** on the delivered APK.
- **Later checks:** After APK delivery, Android 8/API 26 (Nexus One, x86) and Android 16/API 36 (320 dp small phone, dark, 200% text) also passed 226 assertions each on the identical APK.
- Device coverage includes the platform HTTP connection accepting PATCH, SQLite edit/base preservation across restart, duplicate edit refusal, correcting a queued edit, discarding without deleting the confirmed record, an actual synthetic PATCH/reconnect cycle, historical form editing/clearing tags, discreet status labels, and opening the exact rejected card's shared editor. The existing queue, timer, schema migration, rotation, diagnostics, and 0.1.14 correction checks also run.

All new device/server fixtures are synthetic and offline. No family-server requests, real activity edits, physical phone confirmation, or Play publication. The full earlier compatibility/QR matrix was not repeated. Real deployment permissions and older/customized server update behavior remain for physical/integration confirmation.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.15-debug.apk` (406,164 bytes).
- SHA-256: `910524eb53a696723fefaae9ef1c3fa0a8b4bba87197abb9f89560b003e7a5b6`.
- Source: `artifacts/Baby-Buddy-Pocket-0.1.15-source.zip`.
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.15-unsigned.aab`.
- Evidence/screenshots: `artifacts/v0.1.15/`.

Install as an update without uninstalling to preserve existing local data and pending entries.
