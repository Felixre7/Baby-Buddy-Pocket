# Baby Buddy Pocket 0.1.9 verification

> Historical verification: versions through 0.1.9 used a different Android identity. Old identifiers and local artifact names are omitted here; these results do not verify the new 0.1.10 package. `PRE_PUBLICATION_PACKAGE` denotes that earlier identity.

Verified 2026-09-21. Version **0.1.9 / code 10**.

## Change

Added **Need a server? Try Railway** to the welcome screen. It opens the exact [Baby Buddy Railway template](https://railway.com/deploy/baby-buddy-self-hosted-baby-tracker--baby-buddy) recorded in the local Railway setup note, using Android's external browser intent. If no browser is available, the app displays the URL instead. Added one short matching setup sentence/link to the README and Google Play listing draft; no in-depth hosting instructions.

The public template page was opened and verified. No deployment, account creation, credential forwarding, new dependency, or sync/storage changes. Package/signing identity remains unchanged.

## Validation

- Debug, unsigned release, and instrumentation builds pass. Existing 39 JVM tests pass; zero lint errors and seven existing warnings. No new tests were added for this small link change.
- Pixel 9 Pro/API 36: all 77 existing smoke assertions pass in light mode with networking disabled. Inspected the welcome screen in light mode and dark mode with 1.3× text; the new button fits. No full dark-mode suite rerun for this release.
- Tapped the actual welcome button and verified Android's ACTION_VIEW intent opened the exact template URL in the emulator's external WebView Browser Tester app. The browser reported offline, as expected with networking disabled; the public page was independently verified online. The no-browser fallback was code-reviewed, not exercised.
- Installed successfully over 0.1.8 with `adb install -r`. No fresh migration/data-preservation fixture run; identity/signature remain unchanged and prior upgrade evidence is in [0.1.8 verification](VERIFICATION-0.1.8.md).
- No live family-server requests, physical-phone tests, or Railway deployment. Store text remains a draft.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.9-debug.apk` (386,316 bytes).
- APK SHA-256: `19f41ae2f1e9bc80af6e6ac291be6b71ec8079262f6312144c828ab6917ccc57`.
- Source: `artifacts/Baby-Buddy-Pocket-0.1.9-source.zip`.
- Build/test logs, browser-handoff evidence, and welcome screenshots: `artifacts/v0.1.9/`.
