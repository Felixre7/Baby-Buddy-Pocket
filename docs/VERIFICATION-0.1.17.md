# Baby Buddy Pocket 0.1.17 verification

Version **0.1.17 / code 18**, 2026-09-22.

## Implemented behavior

Log-form choices and boolean fields share one styled Android Spinner. Closed fields match the existing Amount/Notes inputs with a quiet background, thin outline, 14 dp corners, a trailing chevron and 52 dp minimum height. Menus use a rounded opaque surface and a soft green selected row with a checkmark. Colors follow the system light/dark theme. Field labels are associated with their controls for accessibility; checked menu rows expose their checked state. Later review found menu wrapping and minimum-row-height issues corrected in 0.1.18, as described below.

Android still handles selection, scrolling, focus and popup dismissal. The original value mapping, required placeholders, Automatic/Yes/No semantics, remembered defaults, existing edit prefills and rotation draft restoration remain in use. No new runtime dependency, server operation, database migration or change to timer/offline behavior. Two original XML vectors provide the chevron/checkmark; duplicated plain-spinner setup was replaced by the shared helper.

## Validation

- 64 JVM tests pass. Debug/instrumentation/release APK and release bundle builds pass. Debug/release lint: zero errors, six existing warnings. Bundletool validates the bundle.
- **Before first APK delivery:** Pixel 9 Pro profile, Android 17/API 37, x86_64 REL image; **280 smoke assertions** pass on the final APK. Actual open/closed menu screenshots reviewed.
- New device coverage checks that the open menu exposes the selected item as checked; a longer unsaved feeding choice survives activity recreation and remains checked after reopening; No saves the underlying false value and is remembered; Yes is selectable; Automatic clears a remembered boolean. Existing required-choice, log saving, edit-prefill, offline queue, timer, navigation, diagnostics and rotation flows also run.
- **After first APK delivery:** Android 8/API 26 and Android 16/API 36 (320 dp small phone, dark theme, 200% text) each passed 280 smoke assertions on the identical APK. Screenshot review nevertheless found that a long option was clipped in the open menu at 200% text; the closed field wrapped correctly. The automated suite did not yet check glyph visibility. This visual defect prompted the 0.1.18 correction; use that newer APK. The original 0.1.17 APK/source ZIP are preserved.
- APK signing certificate matches 0.1.16. Application ID remains `com.babybuddypocket.app`, minimum API 26, target API 36. GPL/project and ZXing asset bytes verified in both APK and AAB; matching source ZIP includes ZXing sources. Source/privacy/name checks pass.

Tests use synthetic offline data. No family-server records were read or changed. This is focused emulator/layout verification; phone confirmation and Google Play publication remain separate. No full device-matrix rerun or claim of a TalkBack audit.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.17-debug.apk` (423,289 bytes).
- SHA-256: `381d88a9e69fa60d2e850f0c27ee1b7446114d0d52e1275b86495dbd0ffa6262`.
- Matching source: `artifacts/Baby-Buddy-Pocket-0.1.17-source.zip` (includes ZXing core 3.5.4 source).
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.17-unsigned.aab`.
- Device evidence/screenshots: `artifacts/v0.1.17/`.

Install as an update without uninstalling to preserve local settings and pending entries. Keep the matching source ZIP alongside the APK when sharing it.
