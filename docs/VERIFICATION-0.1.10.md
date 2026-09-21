# Baby Buddy Pocket 0.1.10 verification

Verified 2026-09-21. Version **0.1.10 / code 11**.

## Publication and identity

The user explicitly approved removing the former prototype name from every repository file, path, and internal identifier, even at the cost of backward compatibility. The Java namespace and Android application ID are now `com.babybuddypocket.app`; the database is `baby-buddy-pocket.db` and the Keystore alias is `baby-buddy-pocket-token`. Source and test directories, instrumentation commands, copyright project attribution, and documentation use the current name.

This is a **separate installation** from versions through 0.1.9. There is no local-data migration. Keep the earlier app until pending records and active timers are resolved, then connect the new app to the same server. The signing certificate is unchanged, but a matching certificate cannot bridge different application IDs.

The initial Git repository is prepared for [Felixre7/Baby-Buddy-Pocket](https://github.com/Felixre7/Baby-Buddy-Pocket). Source, tests, build scripts, licenses, and documentation are included. Generated builds, APKs, caches, local SDK configuration, signing keys, private notes, and temporary tooling are excluded. The Gradle wrapper has executable permission in Git. Historical verification reports omit the former identifiers and clearly distinguish prior evidence from this release.

## Validation

- Clean debug, unsigned release, and instrumentation builds pass. Final rebuild after whitespace normalization also passes.
- All **39 JVM tests** pass. Lint reports **zero errors and six warnings**.
- Pixel 9 Pro/API 36: **77 smoke assertions pass in each of light and dark/1.3× text modes**, with networking disabled. The dark run uses the final rebuilt APK; light ran before whitespace-only cleanup. Inspected the dark welcome and light Today screenshots.
- APK metadata confirms the new application ID, version code 11, version name 0.1.10, and Baby Buddy Pocket label. Signature verifies.
- No family-server request, physical-phone test, or migration test was performed. App behavior and runtime dependencies are otherwise unchanged. Google Play copy remains a draft.

## Artifacts

- APK: `artifacts/Baby-Buddy-Pocket-0.1.10-debug.apk` (386,396 bytes).
- APK SHA-256: `0b3bb98413ea0666945e8d39bb68d8bd4708b9ee495bd1831abcae3ce4b998c7`.
- Source archive: `artifacts/Baby-Buddy-Pocket-0.1.10-source.zip`.
- Local build, lint, signature, and emulator evidence: `artifacts/v0.1.10/`.
