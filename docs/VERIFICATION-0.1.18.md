# Baby Buddy Pocket 0.1.18 verification

Version **0.1.18 / code 19**, 2026-09-22.

## Implemented behavior

Log-form choices and boolean fields share one styled Android Spinner. Closed fields match the existing Amount/Notes inputs with a quiet background, thin outline, 14 dp corners, a trailing chevron and 52 dp minimum height. Menus use a rounded opaque surface, wrapping rows with at least 48 dp height, and a soft green selected row with a checkmark. Colors follow the system light/dark theme. Field labels are associated with their controls for accessibility; checked menu rows expose their checked state.

Android still handles selection, scrolling, focus and popup dismissal. The original value mapping, required placeholders, Automatic/Yes/No semantics, remembered defaults, existing edit prefills and rotation draft restoration remain in use. No new runtime dependency, server operation, database migration or change to timer/offline behavior. Two original XML vectors provide the chevron/checkmark; duplicated plain-spinner setup was replaced by the shared helper.

## Validation

- 64 JVM tests pass. Debug/instrumentation/release APK and release bundle builds pass. Debug/release lint: zero errors, six existing warnings. Bundletool validates the bundle.
- **Before 0.1.18 delivery:** Pixel 9 Pro profile, Android 17/API 37, x86_64 REL image; **281 smoke assertions** pass on the final APK. Actual open/closed menu screenshots reviewed.
- Version 0.1.17 was delivered after its Pixel checks passed. The later 200% text screenshot review caught open-menu clipping despite passing smoke checks. Version 0.1.18 prevents the platform dropdown from forcing horizontal scrolling, retains the 48 dp row minimum after installing the checkmark, and checks actual visibility of the final character in a longer option. Original 0.1.17 artifacts remain unchanged.
- New device coverage checks that the open menu exposes the selected item as checked; a longer unsaved feeding choice survives activity recreation and remains checked after reopening; No saves the underlying false value and is remembered; Yes is selectable; Automatic clears a remembered boolean. Existing required-choice, log saving, edit-prefill, offline queue, timer, navigation, diagnostics and rotation flows also run.
- **Compatibility follow-up:** Android 8/API 26 (Nexus One, x86) and Android 16/API 36 (320 dp small phone, dark theme, 200% text) each passed 281 assertions on the identical APK. Open-menu and wrapped-label screenshots reviewed. These follow-up runs began after the initial 0.1.17 APK delivery and continued alongside the corrected Pixel build.
- APK signing certificate matches 0.1.17. Application ID remains `com.babybuddypocket.app`, minimum API 26, target API 36. GPL/project and ZXing asset bytes verified in both APK and AAB; matching source ZIP includes ZXing sources. Source/privacy/name checks pass.

Tests use synthetic offline data. No family-server records were read or changed. This is focused emulator/layout verification; phone confirmation and Google Play publication remain separate. No full device-matrix rerun or claim of a TalkBack audit.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.18-debug.apk` (423,425 bytes).
- SHA-256: `9f3e5a58de61317c823e8fd6de7362c738b50d84ce680dc59a9a5a112aa0c33f`.
- Matching source: `artifacts/Baby-Buddy-Pocket-0.1.18-source.zip` (includes ZXing core 3.5.4 source).
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.18-unsigned.aab`.
- Device evidence/screenshots: `artifacts/v0.1.18/`.

Install as an update without uninstalling to preserve local settings and pending entries. Keep the matching source ZIP alongside the APK when sharing it.
