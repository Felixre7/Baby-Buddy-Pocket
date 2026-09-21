# Android compatibility — 0.1.11

Tested on 2026-09-21; version code 12, application ID `com.babybuddypocket.app`. Minimum Android 8.0 (API 26), target API 36. This is emulator evidence, not certification for every phone.

## Changes found necessary by testing

- Restore an open form after the activity window attaches. On Android 8–9, restoring the dialog too early during rotation could leave the form visibly stuck after saving. Dismiss the old dialog when its activity is destroyed and preserve the pending draft across another recreation.
- Put Save/Start and Cancel inside the scrollable form. The standard dialog button bar could clip controls on narrow screens with very large text. Keep navigation labels to one line; full accessibility labels remain available.
- Match older Android system-bar backgrounds to the app theme so navigation buttons remain visible in light mode.

No production dependency was added and the supported minimum was not changed. Supporting Android below 8 would require Java API compatibility work, including Google's core-library desugaring for `java.time`, and another test matrix. Keeping Android 8 as the current floor follows the project's simplicity priority; it is not a claim that older support is impossible.

## Final release APK matrix

Each row exercises 123 assertions using the same final release APK signed with the local debug key for testing. Light/dark indicates app configuration; old OS images can use a forced night-mode setting. Pixel/Nexus names describe emulator hardware profiles, not actual manufacturer devices.

| Android / API | Emulator profile | Display pixels / density | Theme / font | Result |
| --- | --- | --- | --- | --- |
| 8.0 / 26 | Nexus One, software navigation | 480×800 / 240 dpi | Light / 100% | PASS |
| 8.1 / 27 | Nexus S | 480×800 / 240 dpi | Dark / 130% | PASS |
| 9 / 28 | Pixel 3 XL | 1440×2960 / 560 dpi | Light / 100% | PASS |
| 10 / 29 | Pixel 3a | 1080×2220 / 440 dpi | Dark / 130% | PASS |
| 11 / 30 | Pixel 4a | 1080×2340 / 440 dpi | Light / 100% | PASS |
| 12 / 31 | Pixel 5 | 1080×2340 / 440 dpi | Dark / 130% | PASS |
| 12L / 32 | Pixel C tablet | 2560×1800 / 320 dpi | Light / 100% | PASS |
| 13 / 33 | Pixel 7 Pro | 1440×3120 / 560 dpi | Dark / 130% | PASS |
| 14 / 34 | Pixel 8 | 1080×2400 / 420 dpi | Light / 100% | PASS |
| 15 / 35 | Pixel 9 Pro XL | 1344×2992 / 480 dpi | Dark / 130% | PASS |
| 16 / 36 | Pixel 9 Pro | 1280×2856 / 480 dpi | Light / 100% | PASS |
| 16 / 36 | Small phone, 320 dp width | 480×800 / 240 dpi override | Dark / 200% | PASS |
| 16 / 36 | Pixel Fold, inner display | 2208×1840 / 420 dpi | Dark / 130% | PASS |
| 17 / 37 | Pixel 10 Pro | 1280×2856 / 480 dpi | Light / 100% | PASS |

The foldable used Google's `google_apis_ps16k` image; `getconf PAGE_SIZE` returned **16384**. Android 17's installed image reported API 37, release 17, codename REL and preview SDK 0. API 26, 27 and 29 used x86 images; other rows used x86_64. The tablet starts in landscape; rotation tests adapt to the original orientation. The foldable run covers its unfolded display and rotation, not live fold/unfold transitions. Both button and gesture-navigation configurations were exercised, without asserting every navigation variant on every API.

An additional Pixel 9 Pro/API 36 run, dark/130%, passed all 123 assertions with a universal APK generated from the final AAB using Google's bundletool 1.18.3. That makes **15 successful configuration/package runs across 12 API levels**.

## What was exercised

- Navigation, dashboard, timeline, trends, settings, activity ordering/visibility and remembered choices.
- Actual injected screen taps on visible controls, scrolling and rotation with an entered form draft; saving afterward and seeing the entry in the timeline.
- Local SQLite reopen/outbox durability, encrypted-token round trip, offline timer start/finish, synthetic shared-timer sync and uncertain-delivery/cleanup behavior. The synthetic transport exercises the real queue/sync code; it does not test live TLS or a real server's authentication.
- Camera permission refusal (**44 assertions**) and camera-frame QR decoding into the connection form (**43 assertions**) on both Android 8.0 and Pixel 9 Pro/Android 16. QR credentials were synthetic; camera input was an emulator image, not a physical camera.
- Screenshots inspected for small phones, large text, tablet, foldable, old Android system bars and Android 17. Form actions at extreme text sizes require scrolling; navigation labels may ellipsize while retaining full accessibility descriptions.

No family server requests or family data were used. All recorded final main-run crash buffers were empty. Earlier failures were diagnosed and rerun: these exposed the production issues above, stale accessibility roots in the test helper, and an Android 8 system-image SystemUI crash with a legacy hardware-navigation profile. The Android 8 final run uses software navigation. The helper now refreshes accessibility roots and handles older hint/window metadata; production code was not changed to work around test-only failures.

## Build and artifact checks

- Debug APK, release APK, instrumentation APK and release AAB builds pass.
- **39 JVM tests pass**. Final debug/release lint: **0 errors, 6 warnings** (target API newer-version notice, a test-only JSON dependency update notice, and four existing draw-allocation warnings).
- The AAB passes `bundletool validate`. No native `.so` libraries or ABI filters are present. The app bundle remains unsigned; emulator copies are test-signed, not ready for Play upload.
- APK signing identity is unchanged from 0.1.10. No separate 0.1.10-to-0.1.11 data-preservation fixture was run in this batch.

SHA-256 values identify the exact tested/delivered outputs:

| Output | SHA-256 |
| --- | --- |
| Final test-signed release APK | `f7f6b7d28d455b94221cd527a5475b2ff4f26d18c39cd33fd2b99eaadd497a24` |
| Delivered debug APK | `5e6007715d33aeae531888dd81ba2caf09cccd78c067e679a318382526bacbdf` |
| Unsigned release AAB | `9f71472b6485f6bfe3cf419657bdb4e8454f82b067e0b541e2dfb57eb35dda86` |
| Bundle-generated universal test APK | `e140151e232f7243aeb4d78e4a2a67565a51873bcc028a6c5bd1bfcd214930b6` |

The broad matrix uses the release APK, not the debug delivery APK. Local evidence is under ignored `artifacts/compatibility-release/<configuration>/`: `result.json` records image, actual OS properties, APK hash and assertions; `smoke.txt`, scanner logs, crash buffers and screenshots retain details. Older diagnostic screenshots can remain in these local folders; use the final result and current prefix, not a leftover failure capture. Build logs and host-specific emulator orchestration stay outside the source repository.

## Reproduce core checks

Use JDK 17 and Android SDK 36. Install official system images using Android Studio's Device Manager; choose the profiles/overrides above. Use a dedicated clean emulator with no real credentials, because the suite changes local settings and synthetic data.

```sh
sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.babybuddypocket.app.test/com.babybuddypocket.app.SmokeInstrumentation
```

For release-APK checks, sign a copy of `app-release-unsigned.apk` with the same test key used by the instrumentation APK and install that copy instead. For bundle validation, run `java -jar bundletool.jar validate --bundle=app-release.aab`; generate a test-signed universal APK with `build-apks --mode=universal` for an installation check. Keep all signing keys outside Git. Scanner modes use `-e scan denied` or `-e scan camera`; the latter expects the synthetic QR payload specified in `SmokeInstrumentation`, supplied as the emulator's camera image.

## Remaining release boundaries

Emulators cannot establish compatibility with every manufacturer's firmware, physical ARM device, camera, keyboard, battery restriction or memory-pressure behavior. This batch does not cover Android below 8, TalkBack operation, all locales, live fold transitions, physical process-kill/network recovery, or end-to-end live server sync on each OS. Earlier live integration evidence remains separate in [the original verification report](VERIFICATION.md).

Before public release, use Play internal/pre-launch testing and physical devices, including a Samsung phone and a lower-memory phone, with a synthetic server. Complete signing, privacy/store declarations and any required closed testing in [the Play release worklist](PLAY-RELEASE.md). Nothing has been uploaded to Play Console.
