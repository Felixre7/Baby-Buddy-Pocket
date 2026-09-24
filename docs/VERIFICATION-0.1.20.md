# Baby Buddy Pocket 0.1.20 verification

Version **0.1.20 / code 21**, 2026-09-22.

## Implemented behavior

Includes ongoing Android timer notifications and synced activity deletion from [0.1.19](VERIFICATION-0.1.19.md). Undo remains deferred. The existing Baby Buddy server is still required.

Screenshot review after the 0.1.19 test APK was delivered found that three platform dialog actions clipped at 320 dp / 200% text, despite passing automation. Edit activity and Delete activity now use the existing wrapping buttons inside the scrollable details content; Done remains the standard close action. New checks verify the final character of both action labels is visible. The original 0.1.19 APK and source archive are preserved.

## Validation completed before the corrected APK link

- **73 JVM tests pass**, including nine deletion regressions.
- Debug, instrumentation and release APKs plus release bundle build successfully with JDK 17. Debug lint: **zero errors, six existing warnings**.
- **Pixel 9 Pro, Android 16/API 36:** focused storage, deletion and notification UI suite passes **119 assertions** on this build.
- **Small phone, 320 dp, dark theme, 200% text, Android 16/API 36:** the same focused suite passes **119 assertions** on the identical APK. Corrected activity-detail screenshot reviewed: both complete action labels and Done are readable.
- The focused checks cover persistence, conflicting/uncertain deletion review, confirmation/discard, timer notification properties and taps, multiple children, and stop/cancel removal.
- APK signature verified. The application ID remains com.babybuddypocket.app.

## Follow-up checks and limits

At initial packaging the full regression suite and Android 8/API 26 preparation were in progress. The tool session was subsequently interrupted before the full suite produced a captured result; the Android 8 runtime check did not run. Neither is claimed as passed. The focused 119-assertion runs above completed on this exact build.

Notification permission refusal, process restoration, actual boot-receiver delivery, ordinary process kill, offline finish and restart were checked on 0.1.19. This build changes only the activity-details layout and version; those lifecycle checks were not repeated on 0.1.20.

Android 17/API 37 runtime testing remains blocked on this computer: both stable-channel 37.0 and 37.2 REL images crash repeatedly in SurfaceFlinger before stable startup. Pixel 9 Pro is the device profile; the actual tested OS is Android 16. Earlier 0.1.18/API 37 results do not validate this build.

No family-server records were accessed or changed. Physical-phone/server confirmation remains open. Deletion uses a server read followed by DELETE, not an atomic cross-device lock; uncertain writes require explicit review. Notifications depend on Android permission/settings and may be dismissed or suppressed; the timer remains saved independently. Remote changes are reconciled by existing foreground sync, with no added background polling or service.

## Artifacts

- Test APK: artifacts/Baby-Buddy-Pocket-0.1.20-new-key-test.apk.
- APK SHA-256: 9390a7f69d464e7735523c3e25f00dabdbd8e637248e29327bab3f258eedda0d.
- Matching source: artifacts/Baby-Buddy-Pocket-0.1.20-source.zip, including ZXing core 3.5.4 source.
- Unsigned APK/bundle: artifacts/Baby-Buddy-Pocket-0.1.20-unsigned.apk and artifacts/Baby-Buddy-Pocket-0.1.20-unsigned.aab.
- Evidence: artifacts/v0.1.20/.

**The APK uses this computer's new debug key. It cannot update the existing phone installation without the original signing key.** Do not uninstall the existing app to bypass the mismatch: local pending work would be removed. Use this APK only on a separate clean test installation or to update the previous new-key 0.1.19 test installation. Certificate SHA-256: 9c83e6e34257b011486e5f52a92a3f3081d8dd79faf8bf6295917d2260c5d333.

Source changes are on local branch codex/timer-notifications-deletion, uncommitted and not pushed. No Google Play publication occurred.

## Reproduction

On an isolated clean emulator (the runner refuses personal credentials):

    adb -s EMULATOR_SERIAL shell am instrument -w -e features true com.babybuddypocket.app.test/com.babybuddypocket.app.SmokeInstrumentation

Omit -e features true for the full suite.
