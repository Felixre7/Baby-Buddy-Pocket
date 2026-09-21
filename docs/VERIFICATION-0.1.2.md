# the Android prototype 0.1.2 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified on 2026-09-21. Version name **0.1.2**, version code **3**. Builds retain the standalone ZXing scanner explicitly selected by the user and the adaptive launcher artwork from 0.1.1.

## Public demo behavior

- **Explore public demo** signs into `https://demo.baby-buddy.net/login/` using Baby Buddy's published `admin` / `admin` account and a server-issued CSRF cookie. It requests the JSON `/api/profile` endpoint for that account's API key. No key is hardcoded and no Add a device HTML is scraped. A separate device registration is not needed.
- Login cookies stay in an isolated in-memory jar. The API key is used for that refresh only, and never saved in the family connection. Every refresh obtains the current key, including after a demo reset. Login redirects are not followed automatically; only the expected same-origin profile destination is accepted.
- Records download completely into a separate SQLite cache. Public demo API traffic is restricted to GET/OPTIONS, and the controller independently rejects writes. Logging/timer completion actions are absent in this mode. The app makes no record-changing requests to the public demo.
- Refresh occurs on entry, once when restoring public demo after a process restart, and on explicit Sync. It does not poll the public service every minute. A failed refresh retains the prior complete snapshot and displays an error. Public demo units remain unconfirmed until selected in Settings.
- **Try offline demo** remains available for synthetic data and local practice logging, including when the public service is unavailable. Switching to practice or leaving demo clears the separate public cache.
- The live public demo currently returns HTTP pagination links despite HTTPS requests. For this fixed public host only, the adapter upgrades links with no explicit port, credentials, or fragment to HTTPS. The normal API path, collection, and pagination-loop checks still apply. No HTTP request is sent; family-server rules are unchanged. Unexpected hosts, ports, paths, or authentication changes fail closed.

## Results

| Check | Result |
| --- | --- |
| Debug APK, unsigned release APK, instrumentation APK | Build passed |
| JVM suite | 45 tests passed |
| Android lint | 0 errors; 8 pre-existing warnings |
| Pixel 9 Pro / Android 16 / API 36, public demo | 16 assertions passed against the live public server |
| Public demo restart and offline failure | 11 assertions passed with Wi-Fi/mobile data disabled |
| Light-mode smoke | 20 assertions passed |
| Dark mode at 1.3× text | 20 assertions passed |
| Final APK actual camera QR scan | 16 assertions passed using a synthetic camera image |
| Same-version replacement of final 0.1.2 | 5 preservation assertions passed |
| 0.1.1 → 0.1.2 upgrade | 5 preservation assertions passed before the public pagination/unit-label corrections |

The live demo test verifies complete downloads, persistent separate storage, no saved family credentials, no family-cache changes, no delivery/clearing of a synthetic family outbox entry, blocked controller writes, and absent logging controls. The offline test restarts the process, verifies the cached timestamp is unchanged after failure, checks the visible error, and switches to synthetic practice data. Tests do not assert fixed public record contents or counts because the shared demo resets and changes.

Added JVM coverage exercises CSRF requirements, failed login, unexpected redirects, malformed profile/key responses, write blocking before transport, the public HTTPS pagination exception, and attempts to escape the allowed host/collection. Existing snapshot, token-origin, outbox, forms, date/time, and QR tests continue to pass.

Update checks seed a synthetic encrypted token, URL, preferences, SQLite snapshot, and unsent outbox entry, reinstall with `adb install -r`, then verify all five. The final APK's signing certificate matches the earlier 0.1.0 and 0.1.1 builds. Actual Proton Drive download/install UI and physical-camera autofocus/low-light behavior still require phone testing. Camera permission refusal was verified in 0.1.1; scanner implementation is unchanged in 0.1.2.

The emulator uses the AOSP Pixel 9 Pro hardware profile at 1280×2856, without Play Services. Its Launcher3 mask is a rounded square; a Pixel launcher's circle is launcher-controlled. No family-server requests were made for these tests. Earlier Railway read/write integration evidence remains in [0.1.0 verification](VERIFICATION.md).

## Artifacts

- APK: the locally archived debug APK
- APK SHA-256: `445d13944b1e2e8a214894e0f8f80f8c326f4aa113448f363f9b7443d92830c3`
- Signing certificate SHA-256: `01b72b5b5ce3a29ced030ef4e4cac6872e5b6a39e136659de4831d1040b383e1`
- Source: the locally archived source ZIP
- Local test logs/screenshots: `artifacts/v0.1.2/` (excluded from source archive).

## Reproduction and limits

Build with Java 17 and SDK 36 using the README instructions. On an isolated clean emulator, install the debug and instrumentation APKs. Run:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w -e publicDemo live PRE_PUBLICATION_PACKAGE.test/PRE_PUBLICATION_PACKAGE.SmokeInstrumentation
adb -s EMULATOR_SERIAL shell svc wifi disable
adb -s EMULATOR_SERIAL shell svc data disable
adb -s EMULATOR_SERIAL shell am force-stop PRE_PUBLICATION_PACKAGE
adb -s EMULATOR_SERIAL shell am instrument -w -e publicDemo offline PRE_PUBLICATION_PACKAGE.test/PRE_PUBLICATION_PACKAGE.SmokeInstrumentation
adb -s EMULATOR_SERIAL shell svc wifi enable
adb -s EMULATOR_SERIAL shell svc data enable
```

Check explicit PASS results, not just ADB's exit code. The public service's availability, published credentials, login contract, and profile API are upstream dependencies. If they change, the app reports failure and offers offline practice; it does not bypass authentication or silently replace cached server data with synthetic data. This demo-specific browser-session flow is not a general username/password login feature for family servers.

Upstream references: [API documentation](https://docs.baby-buddy.net/api/), [login routes](https://github.com/babybuddy/babybuddy/blob/master/babybuddy/urls.py), [profile view](https://github.com/babybuddy/babybuddy/blob/master/api/views.py), [profile serializer](https://github.com/babybuddy/babybuddy/blob/master/api/serializers.py).
