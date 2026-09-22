# Baby Buddy Pocket 0.1.14 verification

Version **0.1.14 / code 15**, 2026-09-21.

## Implemented behavior

Common server rejections now have plain explanations for overlapping activities, future dates/times, time order, duration limits, missing required values, invalid choices, and account/access problems. A newly rejected or uncertain upload raises a notice with Review entry; Settings shows entries needing attention, and timeline/pending labels are human-readable. Unfamiliar failures point to Report a problem. Raw responses are not shown or exported; safe HTTP status, validation field names, rejection categories, and app code frames remain available. Existing private outbox messages are retained for recovery and translated when displayed.

Definitively rejected activity uploads offer **Edit activity**, reusing the original form and values. Cancel leaves the stored entry untouched; Save changes updates that same pending entry and queues it for sync. Edits cannot switch children, recreate a removed entry, or overwrite an uncertain upload. **Delete pending entry** explicitly removes only the device's pending copy, leaving server activities and any shared timer intact. Timer operations and unknown-outcome uploads keep explicit review-before-retry handling.

Schema 5 adds one optional original timer-start field to preserve the identity of a shared timer independently of corrected activity times. Both upload preflight and subsequent timer cleanup use that original identity; a restarted timer remains protected. Schema 4 entries capture their original start when first edited. No new runtime dependency, background service, or server change.

Routine saves say **Activity saved**. Sync details stay in Settings. Icons remain the original Android Canvas drawings and bundled XML vectors; README now describes their source.

## Validation

- **58 JVM tests pass**, covering common/unknown explanations, legacy messages, privacy, new-problem detection, edit eligibility, and existing sync/reliability behavior.
- Debug, instrumentation, release APK, and AAB builds pass. Debug/release lint: zero errors, six existing warnings. Google bundletool 1.18.3 validates the AAB.
- **Initial delivery:** Pixel 9 Pro, Android 17/API 37, x86_64, REL image with preview SDK 0, passes **205 assertions** on the delivered APK. Signature verified; package identity and debug certificate unchanged.
- **Later checks:** After APK delivery, Android 8/API 26 (Nexus One, x86) and Android 16/API 36 (320 dp small phone, dark, 200% text) also passed 205 assertions each on the identical APK.
- Device tests cover a rejected overlapping POST, database restart, same-entry correction, preserved timer identity, changed-timer cleanup protection, schema 4 migration, unsafe/stale edit refusal, plain new-error notice, prefilled form, cancel/save, unknown-outcome controls, and deleting only a local pending copy. Previous timer, offline, preferences, rotation, and diagnostic-sharing checks also run.
- An earlier run failed because a UI assertion read the screen before its dialog appeared. The captured hierarchy showed the correct review-only controls. The test now waits for the dialog; results above are from the final APK and corrected test.

These are synthetic/offline emulator checks. No family-server reads/writes were performed, no physical-phone confirmation is claimed, and the full earlier compatibility/QR matrix was not repeated for this focused change. Shared API operations still are not atomic across caregivers; uncertain uploads require explicit review. Unrecognized or translated server validation messages use the generic failure explanation. No Google Play upload/publication occurred.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.14-debug.apk` (497,531 bytes).
- SHA-256: `1f66868d8fe3aa5955f1e967010f80367248db7316dc49750d6b1ed7d90c871e`.
- Source: `artifacts/Baby-Buddy-Pocket-0.1.14-source.zip`.
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.14-unsigned.aab`.
- Test evidence and synthetic screenshots: `artifacts/v0.1.14-final/`.

Install as an update without uninstalling so existing pending entries are retained.
