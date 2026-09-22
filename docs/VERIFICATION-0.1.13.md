# Baby Buddy Pocket 0.1.13 verification

Version **0.1.13 / code 14**, 2026-09-21. Includes the clock-rejection recovery and private diagnostic reports from [0.1.12](VERIFICATION-0.1.12.md).

## Implemented policy

The user explicitly chose **one running timer per child across all activities**. A known local or shared timer blocks another start for that child and offers a View running timer action. Different children can still have timers. Manual logs remain available. An unassigned server timer is treated as a possible conflict, because the app cannot safely infer its child or activity from its optional name.

Sync reads current server timers immediately before each timer POST. Failure to read leaves the start queued, without sending. A conflicting start is marked rejected locally, preserving its options and timestamps; it can be cancelled or stopped as a separate session. Unknown-delivery starts still require review and are never automatically retried. A prior local Stop/Cancel frees the local slot, but publication of the next start waits for that prior timer's queued cleanup.

When multiple timers are present, Today displays a conflict notice and distinguishes unshared local timers from shared timers. Neither timer is automatically merged or deleted. The user can cancel an extra timer or finish an actual separate session. No schema change, runtime dependency, server modification, or new background service was introduced.

## Concurrent caregiver limitations

The [upstream timer serializer](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py) exposes child, name, start, duration, and user; it has no typed activity field or atomic per-child create-if-absent operation. The [timer model](https://github.com/babybuddy/babybuddy/blob/master/core/models.py) does not enforce one timer per child. These were inspected as upstream source, not asserted as the exact deployed server revision.

The preflight read substantially narrows duplicate starts but is not a distributed lock. Two clients may both see no timer before either POST completes, or an offline device may learn about an existing timer only when it reconnects. Later sync displays conflicting timers without destructive reconciliation.

Finishing an existing timer already checks its identity/start before posting the activity and cleans up only after confirmed save. These are separate requests, so simultaneous finishes can still race. Existing server overlap validation can reject some conflicting duration logs, while different activity types/manual records are not generally duplicate-safe. Rejected or uncertain work stays reviewable; this client does not promise global exactly-once logging across other apps. No live family timer was used for this batch.

## Build checks

- **53 JVM tests pass**, including two caregiver queues, different activity names for one child, different children, unassigned timers, failed fresh reads, and waiting for prior cleanup.
- Debug/instrumentation/release APK and AAB builds pass. Debug/release lint: zero errors, six existing warnings. Google bundletool 1.18.3 validates the AAB. APK signature verifies with the unchanged debug certificate; application ID is unchanged, code 14, minimum API 26, target API 36.


## Final APK emulator checks

Every configuration passed **175 assertions** on the same final APK, offline with synthetic data. Crash buffers were empty.

| API | Profile | Configuration |
| --- | --- | --- |
| 37 | Pixel 9 Pro | Android 17, x86_64, new primary profile |
| 36 | Pixel 9 Pro | Android 16, x86_64, light |
| 26 | Nexus One | Android 8, x86, small screen, light |
| 28 | Pixel 3 XL | Android 9, x86_64, light |
| 36 | Small phone | 480 × 800 at 240 dpi, dark, 200% text |
| 37 | Pixel 10 Pro | Android 17, x86_64, gesture navigation |

Tests cover blocking another activity for the same child, allowing another child's timer, directing the form to the existing timer, preserving two server timers until an explicit cancellation, keeping the remaining timer accessible, offline-conflict restart/save without deleting the other caregiver's timer, and all previous clock/recovery/diagnostics checks. Small-screen and older-Android conflict screenshots were inspected.

The APK was offered for physical-phone testing once Pixel 9 Pro/API 36 and the unit tests had passed, while remaining checks continued. At the user's request, Pixel 9 Pro/API 37 was then established as the primary profile. Future ordinary batches concentrate initial checks on Pixel 9 Pro with the latest stable Android, deliver the first test APK promptly, and continue warranted compatibility tests in parallel. The final results above include those later checks; no APK change was needed.

These are emulator results, not a physical-phone/OEM guarantee. No QR camera rerun or live family-server operation was performed for 0.1.13. Physical two-caregiver testing remains outstanding. No Play upload or publication occurred.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.13-debug.apk` (396,948 bytes).
- APK SHA-256: `17e41dafbfed48462b127210c31df1796850bdc99b99ba64af36b6a09ff0882e`.
- Source: `artifacts/Baby-Buddy-Pocket-0.1.13-source.zip`.
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.13-unsigned.aab`.
- Raw test evidence and synthetic screenshots: `artifacts/v0.1.13/`.

Update the matching 0.1.10 or later test installation without uninstalling. This retains pending entries for recovery and adds the local Report a problem flow introduced in 0.1.12.
