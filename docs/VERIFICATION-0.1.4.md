# the Android prototype 0.1.4 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.4 / code 5**. Focused cleanup after public-demo removal.

## Removed

- The write-only `_local` timeline metadata field; pending-entry actions use the existing outbox list and never read it.
- The icon drawing helper's unused `close` option/branch; every caller used an open path.
- The four-argument `Units` constructor used only as a test shortcut. Tests now use the same explicit five-argument constructor as the app.
- The obsolete local `.tools/ReadQr.java` crop/decoder diagnostic. The synthetic QR fixture generator remains useful for scanner verification.

Reviewed production method references, icon callers, resources/manifest, demo remnants, and supporting tests. A timer icon initially looked unused, but queued timer entries reach it through the timeline; it was retained. Android lifecycle callbacks, the 0.1.2 database/preference upgrade cleanup, active smoke/live-sync tests, required licenses, and historical release archives remain intentional. No further unused app classes/resources were identified by this review and lint; this is not a formal proof of whole-program reachability.

Added a standing project instruction to remove obsolete code/resources/flags/tests/helpers when changing features, after checking indirect callers, offline/queued paths, and upgrade requirements. No new features or dependencies.

## Verification

- Ran `:app:clean` before rebuilding debug, unsigned release, and instrumentation APKs; builds passed.
- **37 JVM tests passed**; **0 lint errors**, 8 existing warnings. Existing tests were retained; no new tests were needed for these behavior-preserving removals.
- Pixel 9 Pro/API 36: **20 smoke assertions passed**, covering local demo, navigation, form logging/rotation, SQLite, and Keystore.
- Installed the final build over 0.1.3: **5 checks passed** for preservation of the synthetic encrypted token, URL, preferences, cache, and unsent outbox.
- Final APK inspection confirms the retired public-demo class, public-demo hostname, and `_local` field string are absent from DEX. Clean build avoids carrying stale class output forward.
- APK signature verified with the same certificate as previous delivered versions.
- No family-server requests, live server writes, or physical-phone testing were performed. Scanner implementation and upgrade cleanup are unchanged; earlier scanner, dark-mode, and retired-demo migration evidence remains in the prior reports.

## Artifacts

- APK: the locally archived debug APK.
- APK SHA-256: `a4f2536ce7bedda64b7239ac1e77f9a51237665978db745093fd7df5b068dd3d`.
- Source: the locally archived source ZIP.
- Signing certificate SHA-256: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`.
- Logs/screenshots: `artifacts/v0.1.4/` (excluded from source archive).
