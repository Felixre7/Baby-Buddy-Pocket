# Baby Buddy Pocket 0.1.8 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.8 / code 9**. This is a historical report for the pre-publication Android identity.

## Changes

- Public app name: **Baby Buddy Pocket**. Launcher/Android application label, welcome heading, and About heading share the `app_name` string resource. Gradle project name is `BabyBuddyPocket`.
- Welcome screen prominently states **Requires an existing Baby Buddy server**, with independent status, server address/API token, and no included hosting explained before connecting. New tagline: Your baby’s day, in your pocket.
- README, project handoff/workflow, and new [Google Play listing draft](PLAY-STORE-LISTING.md) use the chosen name. The store draft puts the server requirement first, explains the offline demo and sync behavior, and identifies the app as independent. No GitHub repository or Play Store release was published by this change.
- New artifacts use `Baby-Buddy-Pocket` filenames. Existing package ID `PRE_PUBLICATION_PACKAGE`, Java namespace, database name, preferences, Keystore alias, and signing key are retained for in-place updates. No sync/timer/storage behavior, dependency, or schema changes.
- Historical verification reports, copyright attribution, working folder, and existing Obsidian note paths retain their original names. The main note and changelog display the new name and have Obsidian aliases to keep them discoverable without breaking existing links.

## Validation

- Debug, unsigned release, and instrumentation builds pass. Existing 39 JVM tests pass; zero lint errors and seven existing warnings. No new tests were added for this text/resource rename.
- Pixel 9 Pro / AOSP Android 16 API 36: existing **77 assertions pass in light mode** and **77 in dark mode at 1.3× text**, with networking disabled. Welcome screenshots were inspected in both themes; the name and server requirement remain readable.
- Actual update from the Android prototype **0.1.6 → Baby Buddy Pocket 0.1.8**: seven existing preservation checks pass for encrypted credentials, URL, preferences, cache, queued record, and an older phone timer that remains finishable. This exercises an older released package with the former display name; a separate 0.1.7 upgrade run was not performed.
- APK manifest inspection: label `Baby Buddy Pocket`, package `PRE_PUBLICATION_PACKAGE`, version name `0.1.8`, code `9`, minimum API 26.
- Signature verifies with the existing certificate: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.
- No live family-server requests, physical-phone test, camera rerun, or publication. The APK remains debug-signed for sideloading; the Play Store file is draft copy, not a submitted listing.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.8-debug.apk` (385,964 bytes).
- APK SHA-256: `2a897bb2ede8c19bf84fb3b7a8da284551e80f27012eec051790a982aa0aa0db`.
- Source: `artifacts/Baby-Buddy-Pocket-0.1.8-source.zip`.
- Logs and screenshots: `artifacts/v0.1.8/`.

Install as an update without uninstalling. The launcher name changes; the saved connection and local data remain under the existing application identity.
