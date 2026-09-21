# the Android prototype 0.1.6 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.6 / code 7**.

## Changes

- New feeding/sleep/tummy-time/pumping forms default to **Start a timer now**. Switch it off for manual start/end entry. Restored drafts retain their selected mode; editing a phone timer or finishing a shared server timer does not start a new timer.
- Settings activity rows put the visibility checkbox/name on the left, with **Move up** above **Move down** on the right. Unavailable moves are disabled and faded.
- Once connected, sync status, errors, **Sync now**, and **Pending entries** are grouped at the top of Settings. Removed the global sync/pending banners, duplicate pending-review control, custom refresh icon, and its special drawing scale. Individual pending timeline records remain labeled, and the demo stays visibly identified. Automatic sync behavior is unchanged.
- No new dependencies or data migrations. Net production Java change is **-2 lines** including comments/formatting, relative to the saved 0.1.5 source.

## Shared timer investigation

Yes, a timer started by another client can appear and be finished here if it was saved to the same Baby Buddy server and the user's permissions allow it. [The Baby Buddy API](https://docs.baby-buddy.net/api/#timer-field) exposes timers and accepts a timer ID when creating a duration activity. The [current serializer](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py) checks consumption permission, uses the timer's start time, sets the end on receipt, saves the activity, and stops the timer.

The existing app already downloads the `timers` collection and displays matching timers on Today with **Finish & log…**. Finishing sends that ID with the chosen activity. This explains the reported cross-device behavior as a supported possibility; no specific family timer or iPhone interaction was inspected or changed. Newly started the Android prototype phone timers remain local until the completed record syncs. No new shared-timer feature was added.

## Final verification

- Debug, unsigned release, and instrumentation builds pass; **39 JVM tests** pass; **0 lint errors**, **7 existing warnings**. Removing the custom icon also removes its allocation warning.
- Pixel 9 Pro / AOSP Android 16 API 36 with networking disabled: **57 assertions pass in light mode** and **57 in dark mode at 1.3× text**.
- Tests cover the default timer switch, manual-mode fallback, returning to the timer default for a new form, one-tap stop/save, existing preferences/storage/form/rotation behavior, and visible right-hand stacked controls.
- Synthetic connected-state UI checks confirm Today/Timeline/Trends omit global sync status and controls, while Settings exposes sync state/errors and pending review. A queued entry can still be reviewed. The synthetic connection is prevented from making requests and cleared afterward.
- Visual screenshots exposed an early zero-width control measurement problem that click-only testing did not catch. Corrected the layout before final verification and added screen-bound checks. Final light/dark screenshots show both controls in place. An initial timer assertion was also changed to inspect the actual switch state instead of the platform's capitalized button text.
- Installed the final APK over **0.1.5**: **5 checks pass** for preservation of the synthetic encrypted token, server URL, preference, cache, and queued entry.
- Final APK signature verified with the same certificate as prior releases: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.
- No family-server requests, real record changes, new live sync tests, physical-phone tests, or camera reruns. Existing network/storage/scanner code is unchanged. Shared-timer findings come from upstream documentation/source and local code inspection, not a new two-phone integration test.

## Artifacts

- APK: the locally archived debug APK.
- APK SHA-256: `2e5856ae2cefaf00f87d4a62c2a14427128d22ee10ab068a291a2993d0927e5b`.
- Source: the locally archived source ZIP.
- Build/test logs and inspected screenshots: `artifacts/v0.1.6/` (excluded from the source archive).

Install as an update without uninstalling. The APK uses the existing signing key; the physical Proton Drive installation flow remains a phone check.
