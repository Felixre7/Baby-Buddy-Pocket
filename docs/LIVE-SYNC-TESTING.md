# Opt-in live sync verification

Use a dedicated, disconnected emulator with the debug app installed. This test refuses an existing connection or queued writes. It clears local credentials/cache after finishing and captures no screenshots or family record contents.

Build the alternate test runner (the app APK is unchanged):

```sh
sh ./gradlew :app:assembleDebugAndroidTest -PliveSyncTest=true
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Supply a UTF-8 JSON object at runtime with `server` and `token` fields, optionally `allowWrite: true`. Send it through standard input to `adb -s EMULATOR_SERIAL shell "run-as com.babybuddypocket.app sh -c 'cat > files/live-sync-credentials.json'"`. First create the private directory using `adb -s EMULATOR_SERIAL shell run-as com.babybuddypocket.app mkdir -p files`. Obtain the key through a private prompt or secret manager; do not place it in shell arguments/history, project files, or instrumentation arguments. The staging file is briefly plaintext inside app-private storage and is deleted before connecting.

```sh
adb -s EMULATOR_SERIAL shell am instrument -w com.babybuddypocket.app.test/com.babybuddypocket.app.LiveSyncInstrumentation
```

Require `PASS` and no `FAIL` in the output, including cleanup. ADB's exit code is insufficient. If the test cannot start, remove the staged file using `adb -s EMULATOR_SERIAL shell run-as com.babybuddypocket.app rm -f files/live-sync-credentials.json`.

Without `allowWrite`, the test uses server reads only. It checks authentication, schema discovery, the connected dashboard, refresh, token encryption, SQLite reopen, and cache retention after a simulated offline failure.

With `allowWrite: true`, the test also uses the first existing child to upload one uniquely labeled note through the app's outbox. It independently reads the note back, checks the cache, deletes only the exact UUID-marked note with matching child/content, and verifies removal from server/cache. Enable this only when creating and removing that temporary server record is authorized. Uploads are not retried after uncertain delivery. On failure, cleanup searches only for the unique marker; a cleanup failure reports the marker for manual review. This is test tooling, not an app feature for deleting records.

Building again without `-PliveSyncTest=true` restores the default demo smoke runner. Neither test runner nor runtime input is included in the production APK.
