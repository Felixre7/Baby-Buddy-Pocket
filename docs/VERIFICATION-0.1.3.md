# the Android prototype 0.1.3 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.3 / code 4**.

## Changes

- Removed the public-demo login/session HTTP client, API-key lookup, pagination exception, separate runtime cache/mode, refresh controls, read-only restrictions, and public-demo-specific tests.
- Kept **Try offline demo** with synthetic records and local practice logging. It works without network access and resets practice entries when the process restarts.
- Added a small startup cleanup for 0.1.2: delete only the retired `public-demo.db` and remove its `public_demo` preference. An existing demo selection naturally opens local sample data. Family credentials, preferences, cache, and outbox keep their existing behavior.
- Retained standalone ZXing scanning, adaptive launcher artwork, and the About screen's installed-version display.
- Recorded the user's priorities in `AGENTS.md`, README, project handoff, and Obsidian: useful functionality and simplicity over feature count; favor maintainability, responsiveness, easy builds, and few dependencies. Push back on disproportionate complexity and propose simpler options while preserving correctness and data integrity.
- Net change from delivered 0.1.2: **275 fewer production Java lines**, **175 fewer JVM-test lines**, and **75 fewer instrumentation lines** (including retained/new migration checks). Physical line counts include comments/blank lines; no new runtime dependency.

## Verification

| Check | Result |
| --- | --- |
| Debug APK, unsigned release APK, instrumentation APK | Build passed |
| JVM tests | 37 passed; removed the 8 public-demo tests |
| Lint | 0 errors, 8 existing warnings |
| Actual 0.1.2 → 0.1.3 family-connection upgrade | 5 preservation assertions passed |
| Actual 0.1.2 → 0.1.3 retired-demo migration | 6 assertions passed |
| Pixel 9 Pro / API 36 light-mode smoke with Wi-Fi and mobile data disabled | 20 assertions passed |
| Same emulator, dark mode and 1.3× text, networking disabled | 20 assertions passed |
| Final APK inspection | PublicDemo class and public-demo hostname absent from DEX |
| APK signature | Verified; same certificate as previous delivered builds |

The connection upgrade test seeds synthetic credentials, an encrypted token, preferences, cache, and an unsent outbox entry under 0.1.2, installs 0.1.3, initializes the controller, and verifies preservation. The separate retired-demo test seeds the old preference/database under 0.1.2 and verifies local sample selection, removal of the retired database/preference, and preservation of a separate synthetic family cache/outbox. No server requests or family credentials were needed.

Smoke tests cover SQLite/Keystore, navigation, local note creation, form rotation, and screenshots. Reviewed the final onboarding screenshot: only the local demo option is present. QR decoding tests pass; camera hardware scanning and permission refusal were not rerun because scanner code is unchanged (see earlier reports). Physical-phone camera behavior and Proton Drive's installation UI still need phone testing. Historical verification reports remain unchanged as release evidence.

## Artifacts

- APK: the locally archived debug APK.
- APK SHA-256: `7840f18c4bce996135bfa373bc313499a69b55061a1cb4e19e2eba40837bbed2`.
- Source: the locally archived source ZIP.
- Signer SHA-256: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.
- Local logs/screenshots: `artifacts/v0.1.3/` (excluded from source archive).

The new APK can update previous builds with the matching signing key. Do not uninstall first if preserving local data. Archived 0.1.2 APK/source files still contain the retired feature and are retained only as history; use 0.1.3 for current installs.
