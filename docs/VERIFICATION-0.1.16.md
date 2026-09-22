# Baby Buddy Pocket 0.1.16 verification

Version **0.1.16 / code 17**, 2026-09-22.

## Implemented behavior

On Timeline, Log activity opens the selected activity's form directly. All activities, or a selected activity without a writable schema, still opens the activity picker. Today keeps its general activity picker regardless of the Timeline filter. Existing forms, timer defaults, remembered choices, offline storage, and sync behavior are reused unchanged.

Selected filters use the existing soft green background and green text in light/dark themes, leaving the stronger filled style for Log activity. Filters expose their selected state to accessibility services. The leading plus sign was removed from both Log activity buttons. No new runtime dependency or icon library; existing original icons remain.

The first GPLv3 APK includes the root LICENSE and NOTICE through a small build copy task, plus the existing ZXing notices. The corresponding source ZIP includes build files, project notices, and ZXing core 3.5.4 source (Maven Central source archive SHA-1 `c2a8bf4ac17e71097d187f2d23755e1e6f1861e2`). Previous MIT artifacts are unchanged.

## Validation

- 64 JVM tests pass; debug and release lint report zero errors and six existing warnings. Debug/instrumentation/release APK and release bundle builds pass. Bundletool validates the bundle.
- **Before first APK delivery:** Pixel 9 Pro profile, Android 17/API 37, x86_64 REL image, passes **250 assertions** on the final delivered APK. The earlier pre-packaging run is separate evidence and is not substituted for this final run.
- The new device flow verifies All activities opens the picker, the selected filter is exposed to accessibility, timed and non-timed filters open the correct forms directly, timer defaults remain, Today ignores the Timeline filter, read-only types fall back to writable choices, and a saved sample appears under the same filter. Existing timer/offline/editing/rotation/diagnostics checks remain in the smoke suite.
- **After first delivery:** After first APK delivery, Android 8/API 26 (Nexus One, x86) and Android 16/API 36 (320 dp small phone, dark theme, 200% text) each passed 250 assertions on the identical APK.
- Exact GPL/project/ZXing notice bytes verified inside the final APK and AAB. APK signature matches 0.1.15, with unchanged application ID/minimum API 26/target API 36. Source archive integrity and privacy/name checks pass.

Tests use synthetic offline data. No family-server records were read or changed. Physical-phone confirmation of the older timer rejection and the separate server/display unit-conversion choice remain open. No Play publication or full-device-matrix rerun.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.16-debug.apk` (420,471 bytes).
- SHA-256: `7555c656e0b6bbd34cbe2f66507ded91cf7cf535bc1ec9795cb533c762deba15`.
- Matching source: `artifacts/Baby-Buddy-Pocket-0.1.16-source.zip` (including ZXing source).
- Unsigned bundle: `artifacts/Baby-Buddy-Pocket-0.1.16-unsigned.aab`.
- Device evidence/screenshots: `artifacts/v0.1.16/`.

Install as an update without uninstalling to preserve existing local data and pending entries. Keep the matching source ZIP alongside the APK when sharing it.
