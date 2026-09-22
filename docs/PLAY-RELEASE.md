# Google Play release preparation

Status: preparation only; no Play Console upload or release has been made. Requirements checked on 2026-09-21.

## Build and compatibility

- Application ID: `com.babybuddypocket.app`. Keep this identity for future updates.
- Minimum: Android 8.0 / API 26. The app uses platform `java.time`; older Android versions require a compatibility library and additional testing. Keep the current minimum unless broader support is explicitly selected. [Android API desugaring](https://developer.android.com/studio/write/java8-support).
- Target: API 36, meeting the current requirement for new phone apps and updates. A target API does not raise the minimum supported OS. [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- No native `.so` libraries are bundled; there are no native ABI filters. The app and QR decoder use Java. Java-only apps are already compatible with 16 KB memory pages, though UI/runtime testing remains useful. [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes).
- Camera and autofocus are optional device features. Manual server setup remains available when a camera is absent or permission is denied.
- See [compatibility evidence](COMPATIBILITY.md) for actual emulator results and limits. Emulator profiles do not reproduce manufacturer firmware or establish universal device compatibility.

Build using JDK 17 and Android SDK 36:

```sh
sh ./gradlew :app:bundleRelease :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

The bundle is `app/build/outputs/bundle/release/app-release.aab`. It is **unsigned** with the current project configuration. The release APK is also unsigned. Local emulator testing uses a copy of the release APK signed with the Android debug key; that is test evidence, not a Play-ready signature.

## License and corresponding source

Baby Buddy Pocket uses [GPL-3.0-only](../LICENSE). For each distributed APK or Play release, provide the exact corresponding source, including build scripts and required dependency source, with clear access instructions next to the binary download or store listing. A link to a moving development branch is not a substitute for the source that matches the released build. Keep release source available for as long as required by the license.

Include the GPLv3 text, project copyright/no-warranty notice, and source location with the distribution; retain ZXing's Apache-2.0 license/notices and the Gradle wrapper notices. Verify these items in the actual release package and listing before publishing. See GPLv3 sections 4–6 in [LICENSE](../LICENSE) for the distribution requirements. Existing 0.1.15 artifacts predate this license change and have not been rebuilt or relicensed retroactively.

## Before uploading

1. Choose and securely back up an upload key, then use Android Studio's **Generate Signed App Bundle / APK** flow and enroll in Play App Signing. Keep keys/passwords outside Git. Do not use the debug key for the Play release. [App signing](https://developer.android.com/studio/publish/app-signing).
2. Confirm the developer account and required verification are ready. Prepare support contact details, store icon, feature graphic, and real-device/emulator screenshots. Existing copy is in [the listing draft](PLAY-STORE-LISTING.md); keep the existing-server requirement prominent.
3. Publish a privacy policy and make it accessible in the app. Complete Data safety using actual data flows: records and authentication are sent to the server selected by the user; QR frames stay on-device; no advertising or analytics SDK is included. Do not equate “no developer backend” with “no data leaves the device.” Review collection/sharing exceptions against Google's definitions. [Data safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
4. Complete the health-app declaration, target-audience, content-rating, ads, and app-access sections. This app is for caregivers and stores baby health/activity records. Give reviewers the offline-demo instructions and, if connected features need review, a dedicated synthetic test server/account rather than a family account. [Health-app declarations](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).
5. Upload to internal testing first; inspect Play's device catalog, generated APKs, and pre-launch results. Test on physical ARM devices, including a Samsung phone and a lower-memory phone, with a real camera and a synthetic server. Exercise process termination, offline-to-online recovery, permissions, keyboard entry, accessibility, and shared timers.
6. If the personal developer account was created after November 13, 2023, the production-access process requires a closed test with at least 12 testers opted in continuously for 14 days. Emulators do not replace those testers. Confirm the requirement in the actual account. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

This document is a release worklist, not a claim that Play has approved the app. No new hosting service, background worker, or production dependency is needed for these preparation steps.
