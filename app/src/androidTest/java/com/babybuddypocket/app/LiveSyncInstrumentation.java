package com.babybuddypocket.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import org.json.*;

/**
 * Opt-in live test, read-only unless allowWrite is set. Credentials arrive via private stdin
 * staging.
 */
public final class LiveSyncInstrumentation extends Instrumentation {
  private int assertions;
  private AppController app;
  private boolean ownsConnection;
  private ApiClient liveApi;
  private String liveToken, testNote;
  private long testChild;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    File input = new File(getTargetContext().getFilesDir(), "live-sync-credentials.json");
    try {
      check(new Credentials(getTargetContext()).server().isEmpty(), "Use a disconnected emulator");
      JSONObject connection;
      try (FileInputStream in = new FileInputStream(input);
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[1024];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        connection = new JSONObject(out.toString("UTF-8"));
      }
      check(input.delete(), "Remove staged credentials before connecting");
      runOnMainSync(() -> app = AppController.get(getTargetContext()));
      check(app.store.pending().isEmpty(), "No queued writes permitted during live read test");
      ownsConnection = true;
      String server = connection.getString("server");
      String token = connection.getString("token");
      liveToken = token;
      liveApi = new ApiClient(server, token);
      runOnMainSync(() -> app.connect(server, token));
      awaitSync();
      check(app.connected() && !app.demo, "Real server connection established");
      check(app.error.isEmpty(), "Live connection succeeds");
      check(app.data.has("children") && app.data.has("_synced"), "Complete live snapshot received");
      check(app.data.has("_schemas"), "Server form discovery completes");
      check(app.credentials.token().equals(token), "Encrypted saved token round trip");
      String encrypted =
          getTargetContext().getSharedPreferences("connection", 0).getString("token", "");
      check(!encrypted.contains(token), "Stored token is not plaintext");
      JSONObject first = app.data;
      LocalStore reopened = new LocalStore(getTargetContext());
      check(
          reopened.snapshot().toString().equals(first.toString()),
          "Full snapshot survives database reopen");
      reopened.close();

      // Exercise the real activity with the connected controller, without capturing family data.
      getUiAutomation();
      startActivitySync(
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      awaitSync();
      SystemClock.sleep(800);
      waitForIdleSync();
      AccessibilityNodeInfo root = null;
      for (int attempt = 0; attempt < 50; attempt++) {
        root = getUiAutomation().getRootInActiveWindow();
        if (root != null && contains(root, "Today")) break;
        SystemClock.sleep(200);
      }
      check(root != null && contains(root, "Today"), "Connected dashboard is displayed");
      check(!contains(root, "DEMO"), "Connected dashboard is not demo data");
      long before = app.data.getLong("_synced");
      runOnMainSync(() -> app.sync());
      awaitSync();
      check(
          app.error.isEmpty() && app.data.getLong("_synced") > before, "Manual refresh completes");
      check(app.store.pending().isEmpty(), "Read test never queues a server write");

      // A network failure must preserve this actual downloaded snapshot in SQLite.
      String cached = app.store.snapshot().toString();
      boolean failed = false;
      try {
        new SyncEngine(
                app.store,
                new ApiClient(
                    server,
                    token,
                    (method, uri, secret, body) -> {
                      throw new java.net.UnknownHostException("simulated offline");
                    }))
            .sync();
      } catch (java.net.UnknownHostException expected) {
        failed = true;
      }
      check(
          failed && app.store.snapshot().toString().equals(cached),
          "Offline failure retains downloaded cache");

      int records = 0, collections = 0;
      for (String endpoint : Records.ENDPOINTS) {
        JSONArray rows = app.data.optJSONArray(endpoint);
        if (rows != null) {
          collections++;
          records += rows.length();
        }
      }
      boolean write = connection.optBoolean("allowWrite", false);
      if (write) testUpload();
      result.putString(
          "stream",
          "\nPASS: "
              + assertions
              + " live sync assertions; HTTPS connection, schema discovery, dashboard, refresh,"
              + " encrypted token, cache reopen, simulated offline retention.\n"
              + "Downloaded: "
              + app.data.getJSONArray("children").length()
              + " children; "
              + records
              + " activity records across "
              + collections
              + " collections; "
              + app.data.getJSONObject("_schemas").length()
              + " writable schemas.\n"
              + (write
                  ? "One temporary note uploaded, independently read back, deleted, and absence"
                        + " verified.\n"
                  : "No server records created, edited, or deleted.\n"));
    } catch (Throwable e) {
      // No stack, raw server payload, key, or family records in test output.
      result.putString(
          "stream",
          "\nFAIL: live sync check after "
              + assertions
              + " assertions ("
              + e.getClass().getSimpleName()
              + ")."
              + (e instanceof AssertionError ? " " + e.getMessage() : "")
              + "\n");
    } finally {
      if (input.exists()) input.delete();
      if (testNote != null) {
        try {
          awaitSync();
          cleanupNote();
        } catch (Throwable e) {
          result.putString(
              "stream",
              result.getString("stream", "")
                  + "FAIL: temporary note cleanup requires attention. Marker: "
                  + testNote
                  + "\n");
        }
      }
      if (ownsConnection && app != null) {
        try {
          awaitSync();
          runOnMainSync(() -> app.disconnect());
          if (app.connected()
              || !app.store.pending().isEmpty()
              || app.store.snapshot().length() != 0) throw new IllegalStateException();
          result.putString(
              "stream",
              result.getString("stream", "")
                  + "Emulator credentials and downloaded cache cleared.\n");
        } catch (Throwable e) {
          result.putString(
              "stream",
              result.getString("stream", "") + "FAIL: emulator cleanup requires attention.\n");
        }
      }
    }
    finish(
        result.getString("stream", "").contains("FAIL:")
            ? Activity.RESULT_CANCELED
            : Activity.RESULT_OK,
        result);
  }

  private void awaitSync() throws Exception {
    long until = SystemClock.elapsedRealtime() + 240000;
    boolean[] busy = {true};
    while (busy[0] && SystemClock.elapsedRealtime() < until) {
      runOnMainSync(() -> busy[0] = app.busy);
      if (busy[0]) SystemClock.sleep(200);
    }
    if (busy[0]) throw new IOException("Sync timeout");
    waitForIdleSync();
  }

  private void testUpload() throws Exception {
    check(
        app.child() >= 0 && app.schema("notes") != null,
        "An existing child and writable note schema are required");
    testChild = app.child();
    testNote = "Baby Buddy Pocket temporary sync verification - " + java.util.UUID.randomUUID();
    JSONObject payload =
        new JSONObject()
            .put("child", testChild)
            .put("time", java.time.Instant.now().toString())
            .put("note", testNote);
    Exception[] addError = {null};
    runOnMainSync(
        () -> {
          try {
            app.add("notes", payload);
          } catch (Exception e) {
            addError[0] = e;
          }
        });
    if (addError[0] != null) throw new IOException("Unable to enqueue test note");
    awaitSync();
    check(app.error.isEmpty() && app.store.pending().isEmpty(), "App outbox upload is confirmed");
    JSONArray matches = testNotes();
    check(matches.length() == 1, "Exactly one test note appears in a separate server read");
    long id = matches.getJSONObject(0).getLong("id");
    JSONObject detail = liveApi.object("GET", "notes/" + id + "/", null);
    check(
        testNote.equals(detail.optString("note")) && detail.optLong("child") == testChild,
        "Uploaded note has the expected contents and child");
    boolean cached = false;
    JSONArray notes = app.store.snapshot().getJSONArray("notes");
    for (int i = 0; i < notes.length(); i++) {
      JSONObject note = notes.getJSONObject(i);
      if (note.optLong("id") == id && testNote.equals(note.optString("note"))) cached = true;
    }
    check(cached, "Uploaded note is cached after refresh");
    cleanupNote();
    check(testNotes().length() == 0, "Temporary note removed from server");
    runOnMainSync(() -> app.sync());
    awaitSync();
    check(app.error.isEmpty(), "Refresh after deletion succeeds");
    notes = app.store.snapshot().getJSONArray("notes");
    for (int i = 0; i < notes.length(); i++)
      check(
          notes.getJSONObject(i).optLong("id") != id,
          "Deleted test note is absent from refreshed cache");
    testNote = null;
  }

  private JSONArray testNotes() throws Exception {
    JSONArray matches = new JSONArray(), notes = liveApi.list("notes");
    for (int i = 0; i < notes.length(); i++) {
      JSONObject note = notes.getJSONObject(i);
      if (testNote.equals(note.optString("note")) && note.optLong("child") == testChild)
        matches.put(note);
    }
    return matches;
  }

  private void cleanupNote() throws Exception {
    JSONArray matches = testNotes();
    for (int i = 0; i < matches.length(); i++) {
      long id = matches.getJSONObject(i).getLong("id");
      String path = "notes/" + id + "/";
      JSONObject detail = liveApi.object("GET", path, null);
      if (!testNote.equals(detail.optString("note")) || detail.optLong("child") != testChild)
        throw new IOException("Refusing to delete an unrelated note");
      java.net.HttpURLConnection request =
          (java.net.HttpURLConnection) liveApi.safeUri(path).toURL().openConnection();
      try {
        request.setInstanceFollowRedirects(false);
        request.setConnectTimeout(15000);
        request.setReadTimeout(25000);
        request.setRequestMethod("DELETE");
        request.setRequestProperty("Authorization", "Token " + liveToken);
        if (request.getResponseCode() != 204)
          throw new IOException("Test note cleanup not confirmed");
      } finally {
        request.disconnect();
      }
    }
    if (testNotes().length() != 0) throw new IOException("Test note remains on server");
  }

  private void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    assertions++;
  }

  private boolean contains(AccessibilityNodeInfo node, String text) {
    if (String.valueOf(node.getText()).contains(text)
        || String.valueOf(node.getContentDescription()).contains(text)) return true;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child != null && contains(child, text)) return true;
    }
    return false;
  }
}
