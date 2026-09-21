# Verification — 0.1.1

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21 on the Pixel 9 Pro AOSP emulator, Android 16 / API 36, 1280 × 2856 at 480 dpi.

## Changes

- Scan Baby Buddy's Add a device QR code to fill the server URL and masked API key. Manual entry remains available. Connection requires tapping Connect after reviewing the address. Browser session cookies are ignored.
- A native camera screen with optional light, permission refusal/manual-entry fallback, and local decoding through ZXing core 3.5.4. No Play Services/module download is required. Camera frames are not retained by the app; the scan activity blocks screenshots.
- Adaptive launcher icon with round-icon declaration and a monochrome layer for themed icons. The launcher controls its final mask; a circular launcher mask produces the Pixel-style round icon, while the AOSP emulator's default mask is a rounded square. Both foreground and background are now proper adaptive layers rather than one square bitmap/vector tile.
- Version name 0.1.1, version code 2; unchanged application ID and signing certificate.

## Final checks

- Debug APK, unsigned release APK, and Android test APK build successfully.
- 37 JVM tests pass: the prior 30 plus seven QR tests, including real generated camera-luminance decoding, a rotated QR, the upstream payload/session-cookie format, malformed keys, invalid/unsafe server addresses, and oversized input.
- Lint: zero errors; eight pre-existing warnings concerning target/tool/test-library freshness and icon drawing allocations.
- Pixel 9 Pro app smoke tests: 20 assertions in light mode / normal text, and 20 in dark mode / 1.3× text.
- QR permission refusal: 16 assertions, including decline and return to the existing manual URL.
- Actual emulator camera-to-form scan: 16 assertions. A synthetic Baby Buddy QR PNG was supplied through the emulator's imagefile back camera; real preview luminance frames passed through the production QR decoder and parser, and the resulting URL/key filled the form. No scan result was injected. Verified masked token and no connection/credential save before Connect.
- Same-version replacement (0.1.0 over 0.1.0) and upgrade to 0.1.1 both preserve an encrypted synthetic API key, connection URL, preferences, SQLite cache, and an unsent outbox entry: five checks for each replacement. The test did not launch a network connection or use real credentials.
- APK signing verification passes; signer SHA-256 `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`, matching the delivered 0.1.0 APK.
- Reviewed the QR-filled form, onboarding, and launcher screenshots. The imagefile camera fixture initially cropped the QR; identifying its visible source tile corrected the fixture without changes to the production decoder.

Results and screenshots are under `artifacts/v0.1.1/`. Temporary camera-frame diagnostic code was removed before the final build. Only synthetic QR credentials were used during this work. Earlier live Railway read/upload verification is recorded in [0.1.0 verification](VERIFICATION.md); live server writes were not repeated for these UI/setup changes. Physical-camera autofocus, low-light performance, and Proton Drive's phone installer UI still need user testing.

## Artifact

- APK: the locally archived debug APK, 373,588 bytes.
- SHA-256: `f8d45f7cb30f9556bd02d38905f3b12fd6962d1ac6def2b99bd8f47adcb3d62e`.
- Source: the locally archived source ZIP.
- Prior 0.1.0 artifacts are retained.

## References

- [Baby Buddy's login QR template](https://github.com/babybuddy/babybuddy/blob/master/babybuddy/templates/babybuddy/login_qr_code.txt)
- [Android APK update requirements](https://developer.android.com/google/play/app-updates)
- [ZXing releases](https://github.com/zxing/zxing/releases)
- [Google code scanner alternative](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner): delegates camera UI/decoding to Play Services and downloads a scanner module before first use; this differs from the standalone decoder selected for 0.1.1.
