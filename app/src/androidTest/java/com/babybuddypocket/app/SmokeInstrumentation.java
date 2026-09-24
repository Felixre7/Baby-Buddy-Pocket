package com.babybuddypocket.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.*;
import org.json.*;

/** Runs with the platform instrumentation API; no instrumentation libraries in the project. */
public final class SmokeInstrumentation extends Instrumentation {
  private int assertions;
  private boolean cleanTest, featuresOnly;
  private String screenPrefix = "";
  private String upgradePhase = "", scanMode = "", timerPhase = "";

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    screenPrefix = args == null ? "" : args.getString("prefix", "");
    upgradePhase = args == null ? "" : args.getString("upgrade", "");
    scanMode = args == null ? "" : args.getString("scan", "");
    timerPhase = args == null ? "" : args.getString("timerLifecycle", "");
    featuresOnly = args != null && args.getString("features", "").equals("true");
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      if (!timerPhase.isEmpty()) {
        timerLifecycle();
        result.putString(
            "stream",
            "\nPASS: timer lifecycle " + timerPhase + "; " + assertions + " assertions.\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      if (!upgradePhase.isEmpty()) {
        upgrade();
        result.putString(
            "stream", "\nPASS: upgrade " + upgradePhase + "; " + assertions + " assertions.\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      check(
          new Credentials(getTargetContext()).server().isEmpty(),
          "Use a clean emulator; refuses to change a connected app.");
      cleanTest = true;
      // The main suite tests allowed notifications; refusal has its own lifecycle phase.
      if (Build.VERSION.SDK_INT >= 33)
        getUiAutomation()
            .grantRuntimePermission(
                getTargetContext().getPackageName(),
                android.Manifest.permission.POST_NOTIFICATIONS);
      android.accessibilityservice.AccessibilityServiceInfo service =
          getUiAutomation().getServiceInfo();
      service.flags |=
          android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      getUiAutomation().setServiceInfo(service);
      database();
      serverTimers();
      rejectedTimersAndCancellation();
      offlineTimerConflict();
      rejectedActivities();
      historicEdits();
      syncedDeletions();
      activityPreferences();
      credentials();
      getTargetContext()
          .getSharedPreferences("preferences", Context.MODE_PRIVATE)
          .edit()
          .clear()
          .commit();
      Activity activity =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      capture("01-connect");
      if (featuresOnly) {
        agePresentation();
        deletionPresentation();
        notificationPresentation();
        promotionState();
        result.putString("stream", "\nPASS: focused features; " + assertions + " assertions.\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      if (!scanMode.isEmpty()) {
        scanner(activity);
        result.putString(
            "stream", "\nPASS: scanner " + scanMode + "; " + assertions + " assertions.\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      click("Try offline demo");
      pause();
      capture("02-today");
      check(awaitText("Maya"), "Dashboard shows selected child");
      check(awaitText("DEMO"), "Demo is labeled");
      click("Timeline");
      pause();
      capture("03-timeline");
      check(awaitText("Search notes"), "Timeline has search");
      filteredLogging();
      click("Trends");
      pause();
      check(awaitText("past seven days"), "Trends visible");
      capture("04-trends");
      click("Settings");
      pause();
      check(awaitText("exploring the demo"), "Demo stays isolated");
      capture("05-settings");
      click("Today");
      pause();
      click("Log activity");
      pause();
      check(awaitText("Choose your activities"), "Log activity opens the grid page");
      capture("06-activity-grid");
      click("Note");
      pause();
      setTextByHint("Required", "A sample note saved by the Android smoke test");
      capture("06-note-form");
      ActivityMonitor rotation = addMonitor("com.babybuddypocket.app.MainActivity", null, false);
      int originalRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      check(getUiAutomation().setRotation((originalRotation + 1) % 4), "Device can rotate");
      Activity rotated = waitForMonitorWithTimeout(rotation, 5000);
      check(rotated != null, "Rotation recreates the activity");
      pause();
      removeMonitor(rotation);
      capture("07-landscape");
      check(awaitText("A sample note saved"), "Form draft survives rotation");
      click("Add sample");
      pause();
      click("Timeline");
      pause();
      check(awaitText("A sample note saved"), "Sample log appears in timeline");
      check(getUiAutomation().setRotation(originalRotation), "Original device rotation restored");
      pause();
      timerFlow();
      timerConflictFlow();
      customizationFlow();
      booleanChoices();
      diagnosticsFlow();
      syncPresentation();
      rejectionPresentation();
      editingPresentation();
      agePresentation();
      deletionPresentation();
      notificationPresentation();
      promotionState();
      result.putString(
          "stream",
          "\nPASS: "
              + assertions
              + " assertions; SQLite restart/outbox, token encryption, navigation, logging form,"
              + " rotation, screenshots.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      try {
        if (cleanTest) {
          capture("failure");
          AccessibilityNodeInfo root = uiRoot();
          try (PrintWriter output =
              new PrintWriter(
                  new File(
                      getTargetContext().getExternalFilesDir(null),
                      "screenshots/failure-hierarchy.txt"))) {
            output.println("Root: " + root);
            if (root != null
                && getTargetContext().getPackageName().contentEquals(root.getPackageName()))
              for (AccessibilityNodeInfo node : nodes(root)) output.println(node);
          }
        }
      } catch (Exception ignored) {
      }
      result.putString("stream", "\nFAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void database() throws Exception {
    String name = "smoke-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject timer =
        new JSONObject().put("id", 9).put("child", 1).put("start", "2026-01-01T00:00:00Z");
    store.replace(new JSONObject().put("timers", new JSONArray().put(timer)));
    long id = store.enqueue("sleep", new JSONObject().put("child", 1).put("timer", 9));
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(store.pending().size() == 1, "Queued record survives database reopen");
    check(store.pending().get(0).getJSONObject("payload").getInt("timer") == 9, "Payload persists");
    store.state(id, "review", "Interrupted delivery");
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.pending().get(0).getString("state").equals("review"),
        "Uncertain state survives restart");
    store.accepted(id, "sleep", new JSONObject().put("id", 44).put("child", 1));
    check(store.pending().isEmpty(), "Accepted record removed from outbox");
    check(
        store.snapshot().getJSONArray("sleep").getJSONObject(0).getInt("id") == 44,
        "Accepted record cached");
    check(
        store.snapshot().getJSONArray("timers").length() == 0, "Consumed timer removed atomically");
    long another = store.enqueue("notes", new JSONObject().put("child", 1).put("note", "test"));
    store.discard(another);
    check(store.pending().isEmpty(), "Discard clears local pending only");
    check(store.snapshot().has("sleep"), "Discard preserves server cache");
    store.enqueue("notes", new JSONObject().put("child", 1));
    store.clear();
    check(
        store.snapshot().length() == 0 && store.pending().isEmpty(),
        "Disconnect clears cache and outbox");
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void serverTimers() throws Exception {
    String name = "timer-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject payload =
        new JSONObject()
            .put("child", 1)
            .put("nap", false)
            .put("start", "2026-01-01T01:00:00Z")
            .put("end", "2026-01-01T02:00:00Z");
    TimerServer server = new TimerServer();
    ApiClient api = new ApiClient("https://timers.invalid", "synthetic", server);
    store.startTimer("sleep", payload);
    long id = store.timers().get(0).getLong("id");
    JSONObject start = store.pending().get(0).getJSONObject("payload");
    check(
        start.length() == 3 && !start.has("nap"), "Only supported timer fields are sent to server");
    server.offline = true;
    try {
      new SyncEngine(store, api).sync();
    } catch (java.io.IOException expected) {
    }
    check(
        server.starts == 0 && store.pending().get(0).getString("state").equals("queued"),
        "Offline start is durable and unsent");
    store.finishTimer(id, payload);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.timers().isEmpty()
            && store.pending().size() == 1
            && store.pending().get(0).getString("endpoint").equals("sleep"),
        "An entirely offline session becomes one durable log without a redundant server timer");
    boolean duplicate = false;
    try {
      store.finishTimer(id, payload);
    } catch (IllegalArgumentException expected) {
      duplicate = true;
    }
    check(duplicate, "Repeated offline stop cannot enqueue another record");
    server.offline = false;
    new SyncEngine(store, api).sync();
    check(
        server.finishes == 1 && server.starts == 0 && store.pending().isEmpty(),
        "Offline session uploads once after restart");
    check(
        server.sleep.getJSONObject(0).getString("end").equals(payload.getString("end")),
        "Delayed upload retains actual stop time");

    payload.put("start", "2026-01-01T03:00:00Z").put("end", "2026-01-01T04:00:00Z");
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    new SyncEngine(store, api).sync();
    long sharedId = store.timers().get(0).getLong("server_id");
    AppController.MemoryStore other = new AppController.MemoryStore();
    new SyncEngine(other, api).sync();
    check(
        other.snapshot().getJSONArray("timers").getJSONObject(0).getLong("id") == sharedId,
        "Another client sees the same shared timer");
    store
        .getWritableDatabase()
        .execSQL(
            "CREATE TRIGGER fail_stop BEFORE INSERT ON outbox BEGIN SELECT RAISE(ABORT, 'synthetic"
                + " disk failure'); END");
    boolean failed = false;
    try {
      store.finishTimer(id, payload);
    } catch (android.database.sqlite.SQLiteException expected) {
      failed = true;
    }
    check(
        failed && store.timers().size() == 1 && store.pending().isEmpty(),
        "Storage failure cannot lose timer setup");
    store.getWritableDatabase().execSQL("DROP TRIGGER fail_stop");
    store.finishTimer(id, payload);
    duplicate = false;
    try {
      store.finishTimer(id, payload);
    } catch (IllegalArgumentException expected) {
      duplicate = true;
    }
    check(duplicate && store.pending().size() == 1, "Repeated shared stop cannot enqueue twice");
    JSONObject queued = store.pending().get(0);
    check(
        queued.getLong("cleanup_timer") == sharedId
            && !queued.getJSONObject("payload").has("timer"),
        "Cleanup metadata stays outside the timed record API payload");
    store.discard(queued.getLong("local_id"));
    check(store.timers().size() == 1, "Discarding an unsent stop preserves its shared timer setup");
    store.finishTimer(id, payload);
    new SyncEngine(store, api).sync();
    check(
        server.finishes == 2
            && server.timers.length() == 1
            && store.pending().get(0).getString("method").equals("DELETE"),
        "Confirmed upload atomically queues timer cleanup without resending the record");
    store.close();
    store = new LocalStore(getTargetContext(), name);
    server.loseDeleteReply = true;
    try {
      new SyncEngine(store, api).sync();
    } catch (java.io.IOException expected) {
    }
    check(
        server.timers.length() == 0 && store.pending().get(0).getString("state").equals("queued"),
        "Lost cleanup reply stays safely retryable");
    new SyncEngine(store, api).sync();
    check(
        server.finishes == 2 && store.pending().isEmpty() && store.timers().isEmpty(),
        "Cleanup retry handles already-deleted timer without duplicating activity");

    payload.put("start", "2026-01-01T05:00:00Z").put("end", "2026-01-01T06:00:00Z");
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    new SyncEngine(store, api).sync();
    sharedId = store.timers().get(0).getLong("server_id");
    store.finishTimer(id, payload);
    api.deleteTimer(sharedId);
    new SyncEngine(store, api).sync();
    check(
        store.pending().get(0).getString("state").equals("rejected")
            && server.finishes == 2
            && store.timers().isEmpty(),
        "Already-finished remote timer is detected before posting another activity");
    store.clear();
    store.startTimer("sleep", payload);
    store.discard(store.pending().get(0).getLong("local_id"));
    check(store.timers().isEmpty(), "Discarding a start also removes its local setup");
    store
        .getWritableDatabase()
        .execSQL(
            "CREATE TRIGGER fail_start BEFORE INSERT ON active_timers BEGIN SELECT RAISE(ABORT,"
                + " 'synthetic disk failure'); END");
    failed = false;
    try {
      store.startTimer("sleep", payload);
    } catch (android.database.sqlite.SQLiteException expected) {
      failed = true;
    }
    check(failed && store.pending().isEmpty(), "Failed start setup rolls back queued request");
    store.getWritableDatabase().execSQL("DROP TRIGGER fail_start");

    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    server.loseStartReply = true;
    int before = server.starts;
    try {
      new SyncEngine(store, api).sync();
    } catch (java.io.IOException expected) {
    }
    store.finishTimer(id, payload);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    new SyncEngine(store, api).sync();
    check(
        server.starts == before + 1
            && store.timers().get(0).has("finish_payload")
            && store.pending().get(0).getString("state").equals("review"),
        "Uncertain start and subsequent stop persist without blind retry");
    // Simulate a start response arriving after the user has already tapped stop.
    JSONObject uncertain = store.pending().get(0);
    store.accepted(uncertain.getLong("local_id"), "timers", server.timers.getJSONObject(0));
    check(
        store.pending().size() == 1 && store.pending().get(0).has("cleanup_timer"),
        "Stop during an in-flight start is linked transactionally on confirmation");
    new SyncEngine(store, api).sync();
    new SyncEngine(store, api).sync();
    check(
        store.pending().isEmpty() && server.finishes == 3,
        "Confirmed in-flight start then stop uploads and cleans up once");
    payload.put("start", "2026-01-01T07:00:00Z").put("end", "2026-01-01T08:00:00Z");
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    new SyncEngine(store, api).sync();
    store.finishTimer(id, payload);
    new SyncEngine(store, api).sync();
    server.timers.getJSONObject(0).put("start", "2026-01-01T09:00:00Z");
    new SyncEngine(store, api).sync();
    check(
        server.timers.length() == 1
            && server.finishes == 4
            && store.pending().get(0).getString("state").equals("rejected"),
        "Delayed cleanup never removes a timer another caregiver restarted");
    store.clear();
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void rejectedTimersAndCancellation() throws Exception {
    String name = "rejected-timer-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject payload =
        new JSONObject()
            .put("child", 1)
            .put("nap", false)
            .put("start", "2026-01-01T01:00:00Z")
            .put("end", "2026-01-01T02:00:00Z");
    String rejection = "Server returned 400. {\"start\":[\"Date/time can not be in the future.\"]}";
    store.startTimer("sleep", payload);
    long id = store.timers().get(0).getLong("id"),
        startId = store.pending().get(0).getLong("local_id");
    store.state(startId, "rejected", rejection);
    store.finishTimer(id, payload);
    check(
        store.timers().isEmpty()
            && store.pending().size() == 1
            && store.pending().get(0).getString("endpoint").equals("sleep"),
        "Rejected start does not block a finished log");
    store.clear();
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    startId = store.pending().get(0).getLong("local_id");
    store.state(startId, "review", "In flight");
    store.finishTimer(id, payload);
    store.state(startId, "rejected", rejection);
    check(
        store.timers().isEmpty() && store.pending().get(0).getString("endpoint").equals("sleep"),
        "Rejection arriving after stop releases the finished log transactionally");
    store.clear();
    store.startTimer("sleep", payload);
    startId = store.pending().get(0).getLong("local_id");
    store.state(startId, "rejected", rejection);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    store.recoverRejectedTimers(true);
    check(
        store.pending().get(0).getString("state").equals("queued"),
        "Prior definite clock rejection recovers after restart");
    id = store.timers().get(0).getLong("id");
    store.cancelTimer(id);
    check(
        store.timers().isEmpty() && store.pending().isEmpty(),
        "Cancelling unsent timer cannot publish it later");

    TimerServer server = new TimerServer();
    ApiClient api = new ApiClient("https://timers.invalid", "synthetic", server);
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    startId = store.pending().get(0).getLong("local_id");
    store.state(startId, "review", "In flight");
    store.cancelTimer(id);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.timers().get(0).optBoolean("cancel_requested"),
        "In-flight cancellation survives restart");
    new SyncEngine(store, api).sync();
    check(
        server.starts == 0 && store.pending().get(0).getString("state").equals("review"),
        "Cancelled ambiguous start is not blindly retried");
    JSONObject confirmed =
        new JSONObject().put("id", 100).put("child", 1).put("start", payload.getString("start"));
    server.timers.put(confirmed);
    store.accepted(startId, "timers", confirmed);
    check(
        store.pending().size() == 1 && store.pending().get(0).getString("method").equals("DELETE"),
        "Late start confirmation queues cancellation without an activity");
    new SyncEngine(store, api).sync();
    check(
        store.pending().isEmpty() && store.timers().isEmpty() && server.finishes == 0,
        "Confirmed cancellation removes only the shared timer");
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    new SyncEngine(store, api).sync();
    store.cancelTimer(id);
    new SyncEngine(store, api).sync();
    check(
        server.timers.length() == 0 && server.finishes == 0,
        "Normal shared cancellation never creates an activity");
    store.clear();
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    startId = store.pending().get(0).getLong("local_id");
    JSONObject adjustedStart =
        new JSONObject().put("id", 200).put("child", 1).put("start", "2026-01-01T00:59:30Z");
    store.accepted(startId, "timers", adjustedStart);
    store.finishTimer(id, payload);
    check(
        store
            .pending()
            .get(0)
            .getJSONObject("payload")
            .getString("start")
            .equals(adjustedStart.getString("start")),
        "Stop rendered before confirmation uses the latest confirmed server start");
    store.clear();
    store.startTimer("sleep", payload);
    id = store.timers().get(0).getLong("id");
    startId = store.pending().get(0).getLong("local_id");
    store.cancelTimer(id);
    check(!store.claim(startId), "Cancelled start cannot be claimed from an old sync snapshot");
    store.clear();
    store.close();
    getTargetContext().deleteDatabase(name);
    android.database.sqlite.SQLiteDatabase old =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            getTargetContext().getDatabasePath(name), null);
    old.execSQL("CREATE TABLE cache (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)");
    old.execSQL(
        "CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL, payload"
            + " TEXT NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL, method TEXT NOT NULL"
            + " DEFAULT 'POST', cleanup_timer INTEGER)");
    old.execSQL(
        "CREATE TABLE active_timers (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL,"
            + " payload TEXT NOT NULL, start_id INTEGER, server_id INTEGER, finish_payload TEXT)");
    old.execSQL(
        "INSERT INTO outbox(id,endpoint,payload,state,message) VALUES(1,'timers',?,'rejected',?)",
        new Object[] {payload.toString(), rejection});
    old.execSQL(
        "INSERT INTO active_timers(endpoint,payload,start_id,finish_payload) VALUES('sleep',?,1,?)",
        new Object[] {payload.toString(), payload.toString()});
    old.setVersion(3);
    old.close();
    store = new LocalStore(getTargetContext(), name);
    store.recoverRejectedTimers(true);
    check(
        store.timers().isEmpty()
            && store.pending().size() == 1
            && store.pending().get(0).getString("endpoint").equals("sleep"),
        "Version 3 stuck stop migrates into a completed log without losing its payload");
    check(
        store
            .pending()
            .get(0)
            .getJSONObject("payload")
            .getString("end")
            .equals(payload.getString("end")),
        "Upgrade recovery preserves the original stop time");
    store.clear();
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void offlineTimerConflict() throws Exception {
    String name = "timer-conflict-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject payload =
        new JSONObject()
            .put("child", 1)
            .put("nap", false)
            .put("start", "2026-01-01T01:00:00Z")
            .put("end", "2026-01-01T02:00:00Z");
    store.startTimer("sleep", payload);
    TimerServer server = new TimerServer();
    server.timers.put(
        new JSONObject()
            .put("id", 99)
            .put("child", 1)
            .put("name", "Feeding")
            .put("start", "2026-01-01T03:00:00Z"));
    ApiClient api = new ApiClient("https://timers.invalid", "synthetic", server);
    new SyncEngine(store, api).sync();
    check(
        server.starts == 0
            && store.pending().get(0).getString("state").equals("rejected")
            && store.timers().size() == 1,
        "Offline conflict preserves the local timer without publishing another");
    store.close();
    store = new LocalStore(getTargetContext(), name);
    store.finishTimer(store.timers().get(0).getLong("id"), payload);
    new SyncEngine(store, api).sync();
    check(
        server.finishes == 1 && store.pending().isEmpty(),
        "A separate local session remains saveable after restart and conflict");
    check(
        server.timers.getJSONObject(0).getLong("id") == 99,
        "Saving the separate local session never removes the other caregiver's timer");
    store.clear();
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void rejectedActivities() throws Exception {
    String name = "rejected-activity-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject original =
        new JSONObject()
            .put("child", 1)
            .put("nap", false)
            .put("start", "2026-01-01T01:00:00Z")
            .put("end", "2026-01-01T02:00:00Z");
    TimerServer server = new TimerServer();
    server.sleep.put(Records.copy(original).put("id", 1));
    server.timers.put(Records.copy(original).put("id", 99));
    store.enqueueTimerFinish("sleep", original, 99);
    long id = store.pending().get(0).getLong("local_id");
    ApiClient api = new ApiClient("https://timers.invalid", "synthetic", server);
    new SyncEngine(store, api).sync();
    check(
        SyncFeedback.label(store.pending().get(0)).equals("Times overlap"),
        "An actual rejected POST has a plain overlap explanation");
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.pending().get(0).getJSONObject("payload").toString().equals(original.toString()),
        "Rejected payload survives a restart unchanged");
    JSONObject corrected =
        Records.copy(original)
            .put("start", "2026-01-01T02:00:00Z")
            .put("end", "2026-01-01T03:00:00Z");
    boolean blocked = false;
    try {
      store.editPending(id, Records.copy(corrected).put("child", 2));
    } catch (IllegalArgumentException expected) {
      blocked = true;
    }
    check(blocked, "Editing cannot move a rejected entry to another child");
    store.editPending(id, corrected);
    check(
        store.pending().size() == 1
            && store.pending().get(0).getLong("local_id") == id
            && store
                .pending()
                .get(0)
                .getString("cleanup_start")
                .equals(original.getString("start")),
        "Correction replaces the same pending entry and preserves original shared timer identity");
    new SyncEngine(store, api).sync();
    check(
        server.finishes == 1 && store.pending().get(0).getString("method").equals("DELETE"),
        "Corrected shared activity passes preflight and uploads once");
    check(
        store
            .pending()
            .get(0)
            .getJSONObject("payload")
            .getString("start")
            .equals(original.getString("start")),
        "Timer cleanup uses original timer start rather than corrected activity start");
    server.timers.getJSONObject(0).put("start", "2026-01-01T04:00:00Z");
    new SyncEngine(store, api).sync();
    check(
        server.timers.length() == 1 && store.pending().get(0).getString("state").equals("rejected"),
        "A timer restarted after correction is never deleted");
    store.clear();
    id = store.enqueue("sleep", original);
    store.state(id, "review", "Unknown outcome");
    blocked = false;
    try {
      store.editPending(id, corrected);
    } catch (IllegalArgumentException expected) {
      blocked = true;
    }
    check(
        blocked
            && store
                .pending()
                .get(0)
                .getJSONObject("payload")
                .toString()
                .equals(original.toString()),
        "Uncertain uploads cannot be edited or overwritten");
    store.discard(id);
    blocked = false;
    try {
      store.editPending(id, corrected);
    } catch (IllegalArgumentException expected) {
      blocked = true;
    }
    check(
        blocked && store.pending().isEmpty(),
        "Stale editing cannot recreate a deleted pending entry");
    store.close();
    getTargetContext().deleteDatabase(name);
    android.database.sqlite.SQLiteDatabase old =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            getTargetContext().getDatabasePath(name), null);
    old.execSQL("CREATE TABLE cache (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)");
    old.execSQL(
        "CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL, payload"
            + " TEXT NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL, method TEXT NOT NULL"
            + " DEFAULT 'POST', cleanup_timer INTEGER)");
    old.execSQL(
        "CREATE TABLE active_timers (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL,"
            + " payload TEXT NOT NULL, start_id INTEGER, server_id INTEGER, finish_payload TEXT,"
            + " cancel_requested INTEGER NOT NULL DEFAULT 0)");
    old.execSQL(
        "INSERT INTO outbox(id,endpoint,payload,state,message,cleanup_timer)"
            + " VALUES(1,'sleep',?,'rejected','Overlap',99)",
        new Object[] {original.toString()});
    old.setVersion(4);
    old.close();
    store = new LocalStore(getTargetContext(), name);
    store.editPending(1, corrected);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.pending().get(0).getString("cleanup_start").equals(original.getString("start"))
            && store
                .pending()
                .get(0)
                .getJSONObject("payload")
                .getString("start")
                .equals(corrected.getString("start")),
        "Version 4 upgrade preserves the old shared timer identity through edit and restart");
    store.clear();
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void historicEdits() throws Exception {
    java.net.HttpURLConnection connection =
        (java.net.HttpURLConnection) new java.net.URL("https://edit.invalid").openConnection();
    connection.setRequestMethod("PATCH");
    check(
        connection.getRequestMethod().equals("PATCH"),
        "Platform connection accepts PATCH without another HTTP library");
    connection.disconnect();
    String name = "historic-edit-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject original =
        new JSONObject()
            .put("id", 31)
            .put("child", 1)
            .put("note", "Original")
            .put("time", "2026-01-01T01:00:00Z");
    JSONObject edited = Records.copy(original).put("note", "Edited offline");
    store.replace(new JSONObject().put("notes", new JSONArray().put(original)));
    store.enqueueEdit("notes", original, edited);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    JSONObject pending = store.pending().get(0);
    long id = pending.getLong("local_id");
    check(
        pending.getString("method").equals("PATCH")
            && pending.getJSONObject("original").getString("note").equals("Original")
            && pending.getJSONObject("payload").getString("note").equals("Edited offline"),
        "Offline edit and its comparison base survive restart");
    boolean blocked = false;
    try {
      store.enqueueEdit("notes", original, edited);
    } catch (IllegalArgumentException expected) {
      blocked = true;
    }
    check(
        blocked && store.pending().size() == 1,
        "An activity cannot accumulate competing local edit requests");
    store.editPending(id, Records.copy(edited).put("note", "Second correction"));
    check(
        store.pending().get(0).getJSONObject("original").getString("note").equals("Original"),
        "Editing a queued correction keeps its first server comparison base");
    store.discard(id);
    check(
        store
            .snapshot()
            .getJSONArray("notes")
            .getJSONObject(0)
            .getString("note")
            .equals("Original"),
        "Discarding an edit reveals the unchanged confirmed activity");
    store.enqueueEdit("notes", original, edited);
    final JSONObject current = Records.copy(original);
    int[] patches = {0};
    new SyncEngine(
            store,
            new ApiClient(
                "https://edit.invalid",
                "synthetic",
                (method, uri, token, body) -> {
                  String path = uri.getPath();
                  if (method.equals("PATCH")) {
                    patches[0]++;
                    JSONObject patch = new JSONObject(body);
                    current.put("note", patch.getString("note"));
                    return current.toString();
                  }
                  if (path.equals("/api/notes/31/")) return current.toString();
                  if (method.equals("OPTIONS")) return "{\"actions\":{\"POST\":{}}}";
                  if (path.equals("/api/")) return "{\"notes\":\"/api/notes/\"}";
                  if (path.equals("/api/children/")) return "[{\"id\":1}]";
                  return new JSONArray().put(current).toString();
                }))
        .sync();
    check(
        patches[0] == 1
            && store.pending().isEmpty()
            && store.snapshot().getJSONArray("notes").length() == 1
            && store
                .snapshot()
                .getJSONArray("notes")
                .getJSONObject(0)
                .getString("note")
                .equals("Edited offline"),
        "Reconnect updates the original server activity once and confirms its local copy");
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  /** API fixture enforces non-overlapping sessions and the 24-hour duration limit. No network. */
  private static final class TimerServer implements ApiClient.Transport {
    boolean offline, loseStartReply, loseDeleteReply;
    int starts, finishes;
    JSONArray timers = new JSONArray(), sleep = new JSONArray();

    public String request(String method, java.net.URI uri, String token, String body)
        throws Exception {
      if (offline) throw new java.io.IOException("Synthetic offline connection");
      String path = uri.getPath();
      if (path.startsWith("/api/timers/") && !path.equals("/api/timers/")) {
        long id = Long.parseLong(path.split("/")[3]);
        JSONObject found = null;
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < timers.length(); i++) {
          JSONObject timer = timers.getJSONObject(i);
          if (timer.getLong("id") == id) found = timer;
          else remaining.put(timer);
        }
        if (found == null) throw new ApiClient.HttpFailure(404, "Timer not found");
        if (method.equals("DELETE")) {
          timers = remaining;
          if (loseDeleteReply) {
            loseDeleteReply = false;
            throw new java.io.IOException("Lost cleanup reply");
          }
          return "";
        }
        return found.toString();
      }
      if (method.equals("OPTIONS")) return "{\"actions\":{\"POST\":{}}}";
      if (method.equals("GET")) {
        if (path.equals("/api/")) return "{\"timers\":\"/api/timers/\",\"sleep\":\"/api/sleep/\"}";
        if (path.equals("/api/children/")) return "[{\"id\":1}]";
        return (path.equals("/api/timers/") ? timers : sleep).toString();
      }
      JSONObject payload = new JSONObject(body);
      if (path.equals("/api/timers/")) {
        payload.put("id", ++starts);
        timers.put(payload);
        if (loseStartReply) {
          loseStartReply = false;
          throw new java.io.IOException("Lost start reply");
        }
      } else {
        FormValues.validateDuration(payload);
        for (int i = 0; i < sleep.length(); i++) {
          JSONObject old = sleep.getJSONObject(i);
          if (Records.instant(old.getString("start"))
                  .isBefore(Records.instant(payload.getString("end")))
              && Records.instant(old.getString("end"))
                  .isAfter(Records.instant(payload.getString("start"))))
            throw new ApiClient.HttpFailure(
                400, "Another entry intersects the specified time period.");
        }
        payload.put("id", ++finishes);
        sleep.put(payload);
      }
      return payload.toString();
    }
  }

  private void activityPreferences() throws Exception {
    SharedPreferences prefs = getTargetContext().getSharedPreferences("test-options", 0);
    prefs.edit().clear().commit();
    ActivityOptions real = new ActivityOptions(prefs, false);
    real.move("sleep", -1);
    real.visible("tummy-times", false);
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("feedings");
    real.remember("feedings", 1, schema, Collections.singletonMap("method", "bottle"));
    real = new ActivityOptions(prefs, false);
    check(
        real.ordered().get(0).equals("sleep") && !real.visible().contains("tummy-times"),
        "Order and visibility survive reloading preferences");
    check(
        real.defaults("feedings", 1).getString("method").equals("bottle"), "Last choice persists");
    check(real.defaults("feedings", 2).length() == 0, "Defaults are separate for each child");
    ActivityOptions demo = new ActivityOptions(prefs, true);
    check(
        demo.visible("tummy-times") && demo.defaults("feedings", 1).length() == 0,
        "Demo preferences cannot override family defaults");
    prefs.edit().putString("activity_order", "sleep,sleep,obsolete,notes").commit();
    check(
        real.ordered().size() == 12 && real.ordered().get(1).equals("notes"),
        "Unknown or repeated saved types do not lose supported activities");
    prefs.edit().clear().commit();
  }

  private void timerFlow() throws Exception {
    click("Today");
    pause();
    click("Log activity");
    pause();
    click("Sleep");
    pause();
    check(
        checked("Start a timer now") && !contains("End *"),
        "New timed logs default to starting a timer");
    capture("08-timer-default");
    click("Start a timer now");
    pause();
    check(awaitText("Start") && contains("End"), "Manual time entry remains available");
    click("Start a timer now");
    pause();
    click("Start timer");
    pause();
    check(awaitText("Stop & save"), "Running timer has a direct dashboard stop");
    capture("09-running-timer");
    AppController app = AppController.get(getTargetContext());
    check(app.timers().size() == 1, "Configured timer started");
    runOnMainSync(
        () -> {
          app.activities().visible("sleep", false);
          app.listener.run();
        });
    pause();
    pause();
    check(awaitText("Stop & save"), "Hiding an activity cannot strand its running timer");
    int before = app.data.getJSONArray("sleep").length();
    click("Stop & save");
    pause();
    check(!contains("Stop & save") && app.timers().isEmpty(), "One tap stops the timer");
    check(
        app.data.getJSONArray("sleep").length() == before + 1,
        "One tap also saves exactly one activity");
    runOnMainSync(() -> app.activities().visible("sleep", true));
    click("Log activity");
    click("Sleep");
    click("Start timer");
    click("Cancel timer");
    click("Cancel timer");
    check(
        !contains("Stop & save") && app.timers().isEmpty(),
        "Cancel button removes running demo timer");
    check(
        app.data.getJSONArray("sleep").length() == before + 1,
        "Cancel button does not save an activity");
  }

  private void timerConflictFlow() throws Exception {
    AppController app = AppController.get(getTargetContext());
    JSONObject start =
        new JSONObject()
            .put("child", app.child())
            .put("nap", false)
            .put("start", java.time.Instant.now().toString());
    runOnMainSync(
        () -> {
          try {
            app.startTimer("sleep", start);
            boolean blocked = false;
            try {
              app.startTimer("tummy-times", start);
            } catch (AppController.TimerAlreadyRunning expected) {
              blocked = true;
            }
            check(
                blocked && app.timers().size() == 1,
                "Different activity cannot start a second timer for this child");
            app.startTimer("sleep", Records.copy(start).put("child", 2));
            check(app.timers().size() == 2, "Another child can have a timer simultaneously");
            app.cancelTimer(app.timers().get(1).getLong("id"));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    click("Log activity");
    click("Sleep");
    click("Start timer");
    check(awaitText("A timer is already running"), "Conflicting start explains the existing timer");
    check(app.timers().size() == 1, "Blocked form does not create another timer");
    capture("18-existing-timer");
    click("View running timer");
    check(awaitText("Stop & save"), "Conflict action returns directly to the existing timer");
    click("Cancel timer");
    click("Cancel timer");
  }

  private void filteredLogging() throws Exception {
    click("Log activity");
    check(awaitText("Choose your activities"), "All activities still opens the activity picker");
    click("Back");
    click("Filter: Feeding");
    pause();
    boolean selected = false;
    for (AccessibilityNodeInfo node : nodes(uiRoot()))
      if ("Filter: Feeding".contentEquals(String.valueOf(node.getContentDescription())))
        selected = node.isSelected();
    check(selected, "The active Timeline filter is exposed to accessibility");
    capture("03-timeline-filtered");
    click("Log activity");
    check(awaitText("Time feeding"), "Filtered Timeline opens the matching timed form directly");
    check(checked("Start a timer now"), "Direct logging retains the timer default");
    click("Cancel");
    click("Today");
    click("Log activity");
    check(awaitText("Choose your activities"), "Timeline filter does not constrain Today logging");
    click("Back");
    click("Timeline");
    AppController app = AppController.get(getTargetContext());
    JSONObject schemas = app.data.optJSONObject("_schemas");
    JSONObject feeding = schemas.getJSONObject("feedings");
    runOnMainSync(() -> schemas.remove("feedings"));
    try {
      click("Log activity");
      check(
          awaitText("Choose your activities"), "A read-only filter falls back to writable choices");
      click("Back");
    } finally {
      runOnMainSync(
          () -> {
            try {
              schemas.put("feedings", feeding);
            } catch (JSONException e) {
              throw new AssertionError(e);
            }
          });
    }
    click("Filter: Note");
    click("Log activity");
    check(awaitText("Log note"), "A non-timed filter opens its matching form directly");
    setTextByHint("Required", "Filtered timeline sample");
    click("Add sample");
    click("Timeline");
    check(
        awaitText("Filtered timeline sample"), "Directly logged sample appears in the same filter");
    click("Filter: All activities");
    pause();
  }

  private void customizationFlow() throws Exception {
    AppController app = AppController.get(getTargetContext());
    click("Settings");
    pause();
    click("Move Sleep up");
    pause();
    check(app.activities().ordered().get(0).equals("sleep"), "Settings move control updates order");
    click("Tummy time");
    pause();
    check(!app.activities().visible("tummy-times"), "Settings checkbox hides an activity");
    for (AccessibilityNodeInfo node : uiRoot().findAccessibilityNodeInfosByText("Tummy time"))
      node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
    pause();
    android.graphics.Rect label = bounds("Tummy time"),
        up = bounds("Move Tummy time up"),
        down = bounds("Move Tummy time down");
    check(
        up.width() > 100 && down.width() > 100 && up.left >= label.right && down.top >= up.bottom,
        "Move controls have visible width and stack to the right of the activity label");
    capture("10-activity-settings");
    click("Today");
    pause();
    check(!contains("Tummy time"), "Hidden activity is absent from dashboard and latest records");
    click("Timeline");
    pause();
    check(!contains("Tummy time"), "Hidden activity is absent from timeline and filters");
    click("Trends");
    pause();
    check(!contains("Tummy time"), "Hidden activity is absent from trends");
    click("Today");
    pause();
    click("Log activity");
    pause();
    check(!contains("Tummy time"), "Hidden activity is absent from logging grid");
    click("Feeding");
    pause();
    click("Start a timer now");
    pause();
    click("Choose…");
    pause();
    click("formula");
    pause();
    click("Choose…");
    pause();
    click("bottle");
    pause();
    setTextByHint("Optional", "90");
    click("Add sample");
    pause();
    click("Log activity");
    pause();
    click("Feeding");
    pause();
    check(awaitText("formula") && contains("bottle"), "Next form prefills last feeding choices");
    check(
        checked("Start a timer now") && !contains("End *"),
        "A new form returns to timer mode after a manual log");
    boolean freshAmount = false;
    for (AccessibilityNodeInfo node : nodes(uiRoot()))
      if ("Optional".contentEquals(String.valueOf(node.getHintText()))) {
        freshAmount =
            node.isShowingHintText() || node.getText() == null || node.getText().length() == 0;
        break;
      }
    check(freshAmount, "Next feeding amount starts blank");
    capture("11-remembered-choices");
    click("formula");
    pause();
    check(checked("formula"), "Open choice menu marks the current value as checked");
    readableChoice("fortified breast milk");
    capture("25-choice-menu");
    click("fortified breast milk");
    pause();
    ActivityMonitor rotation = addMonitor("com.babybuddypocket.app.MainActivity", null, false);
    int originalRotation =
        ((android.view.WindowManager) getTargetContext().getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay()
            .getRotation();
    check(getUiAutomation().setRotation((originalRotation + 1) % 4), "Choice form can rotate");
    check(waitForMonitorWithTimeout(rotation, 5000) != null, "Choice form activity recreated");
    removeMonitor(rotation);
    pause();
    check(
        awaitText("fortified breast milk") && contains("bottle"),
        "Unsaved choice survives rotation independently of remembered defaults");
    check(getUiAutomation().setRotation(originalRotation), "Choice form rotation restored");
    pause();
    click("fortified breast milk");
    pause();
    check(checked("fortified breast milk"), "Restored choice is checked in the reopened menu");
    click("fortified breast milk");
    pause();
    capture("26-long-choice");
    click("Cancel");
    pause();
    getUiAutomation()
        .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    pause();
    check(
        !contains("Choose your activities"),
        "System Back returns from grid without closing the app");
  }

  private void booleanChoices() throws Exception {
    AppController app = AppController.get(getTargetContext());
    click("Log activity");
    click("Sleep");
    click("Start a timer now");
    click("Automatic");
    pause();
    check(checked("Automatic"), "An unset optional boolean is Automatic");
    click("No");
    pause();
    click("Add sample");
    pause();
    check(
        app.activities().defaults("sleep", app.child()).getString("nap").equals("false"),
        "No saves false rather than the display label");
    click("Log activity");
    click("Sleep");
    check(awaitText("No"), "Optional boolean remembers No");
    click("No");
    pause();
    check(checked("No"), "Optional boolean menu exposes the checked value");
    capture("27-boolean-menu");
    click("Yes");
    pause();
    click("Yes");
    pause();
    check(checked("Yes"), "Optional boolean can change to Yes");
    click("Automatic");
    pause();
    click("Start a timer now");
    click("Add sample");
    pause();
    JSONArray sleep = app.data.getJSONArray("sleep");
    check(
        app.activities().defaults("sleep", app.child()).getString("nap").isEmpty()
            && !sleep.getJSONObject(sleep.length() - 1).has("nap"),
        "Automatic remembers an unset choice and omits the boolean from the saved activity");
  }

  private void readableChoice(String text) {
    for (AccessibilityNodeInfo node : uiRoot().findAccessibilityNodeInfosByText(text)) {
      if (!text.contentEquals(node.getText())) continue;
      node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
      pause();
      Bundle request = new Bundle();
      request.putInt(
          AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
          text.length() - 1);
      request.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1);
      node.refreshWithExtraData(
          AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, request);
      Parcelable[] locations =
          node.getExtras()
              .getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
      android.graphics.Rect bounds = new android.graphics.Rect();
      node.getBoundsInScreen(bounds);
      android.graphics.RectF last =
          locations != null && locations.length == 1 ? (android.graphics.RectF) locations[0] : null;
      check(
          last != null && bounds.contains((int) last.centerX(), (int) last.centerY()),
          "The last character of the label is visible inside its row");
      return;
    }
    throw new AssertionError("Missing readable label: " + text);
  }

  private void diagnosticsFlow() throws Exception {
    AppController app = AppController.get(getTargetContext());
    app.log(
        DiagnosticLog.Event.TIMER_START_REJECTED,
        new ApiClient.HttpFailure(
            400, "Server returned 400. {\"start\":[\"synthetic-secret private-record\"]}"));
    click("Settings");
    click("Report a problem");
    check(awaitText("HTTP 400 fields=start"), "Support report keeps useful HTTP fields");
    check(
        !contains("synthetic-secret") && !contains("private-record"),
        "Report excludes response contents");
    capture("17-diagnostics");
    click("Copy report");
    check(awaitText("Recent technical events"), "Copy leaves report available");
    String[] copied = {""};
    runOnMainSync(
        () -> {
          android.content.ClipboardManager clipboard =
              (android.content.ClipboardManager)
                  getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);
          copied[0] =
              clipboard.getPrimaryClip().getItemAt(0).coerceToText(getTargetContext()).toString();
        });
    check(
        copied[0].contains("HTTP 400 fields=start") && !copied[0].contains("synthetic-secret"),
        "Copied report keeps sanitized details");
    // Android's temporary clipboard preview covers lower controls on small screens.
    // Let it dismiss before testing a real touch on Share (rather than its overlay).
    if (android.os.Build.VERSION.SDK_INT >= 33) SystemClock.sleep(8000);
    ActivityMonitor share =
        addMonitor(new IntentFilter(Intent.ACTION_CHOOSER), new ActivityResult(0, null), true);
    click("Share report");
    check(share.getHits() == 1, "Share opens system chooser without sending automatically");
    removeMonitor(share);
    click("Close report");
    click("Today");
  }

  private void syncPresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    app.credentials.save("https://sync-test.invalid", "synthetic-sync-token");
    try {
      runOnMainSync(
          () -> {
            app.demo = false;
            app.busy = true; // Keep the synthetic connection from making requests.
            app.error = "Synthetic connection interruption";
            app.store.enqueue(
                "notes",
                Records.put(
                    Records.put(new JSONObject(), "child", 1), "note", "Synthetic pending entry"));
            app.listener.run();
          });
      for (String tab : new String[] {"Today", "Timeline", "Trends"}) {
        click(tab);
        pause();
        check(
            !contains("Sync now")
                && !contains("Syncing your shared day")
                && !contains("Synthetic connection interruption")
                && !contains("Pending entries ("),
            tab + " keeps sync status and controls in Settings");
      }
      click("Settings");
      pause();
      check(
          contains("Syncing your shared day") && contains("Synthetic connection interruption"),
          "Settings shows sync status and errors");
      check(
          contains("Sync now") && contains("Pending entries (1)"),
          "Settings contains sync and pending review controls");
      capture("12-sync-settings");
      click("Pending entries (1)");
      pause();
      check(awaitText("Note · Waiting to sync"), "Pending records remain reviewable from Settings");
      click("Close");
      app.store.clear();
      app.data.getJSONObject("_schemas").put("timers", new JSONObject());
      JSONObject timed =
          new JSONObject()
              .put("child", app.child())
              .put("nap", false)
              .put("start", java.time.Instant.now().toString());
      runOnMainSync(
          () -> {
            try {
              app.startTimer("sleep", timed);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      click("Today");
      pause();
      check(
          contains("Cancel timer") && contains("Stop & save") && !contains("start pending"),
          "Offline start is visible and can be stopped immediately");
      capture("13-offline-start");
      click("Stop & save");
      pause();
      check(
          !contains("Stop & save")
              && app.store.pending().get(0).getString("endpoint").equals("sleep"),
          "Offline stop saves a durable completed activity");
      capture("14-offline-finish");
      timed.put("start", java.time.Instant.now().toString());
      runOnMainSync(
          () -> {
            try {
              app.startTimer("sleep", timed);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      check(
          app.timers().size() == 1,
          "Another offline session can start while an earlier finish waits");
      TimerServer server = new TimerServer();
      ApiClient api = new ApiClient("https://timers.invalid", "synthetic", server);
      new SyncEngine(app.store, api).sync();
      new SyncEngine(app.store, api).sync();
      runOnMainSync(
          () -> {
            app.data = app.store.snapshot();
            app.listener.run();
          });
      pause();
      check(
          contains("Cancel timer") && contains("Stop & save"),
          "Confirmed shared timer keeps one-tap configured stop");
      capture("15-shared-timer");
      click("Stop & save");
      pause();
      check(
          !contains("Finish pending") && !contains("Stop & save") && !app.store.pending().isEmpty(),
          "Offline shared stop leaves Today and stays durable in Settings");
      capture("16-shared-finish-pending");
      new SyncEngine(app.store, api).sync();
      new SyncEngine(app.store, api).sync();
      check(
          server.finishes == 2 && app.store.pending().isEmpty(),
          "Both offline sessions finish exactly once after reconnect");
      JSONObject remote =
          new JSONObject()
              .put("id", 901)
              .put("child", app.child())
              .put("name", "Feeding")
              .put("start", java.time.Instant.now().minusSeconds(60).toString());
      runOnMainSync(
          () -> {
            try {
              app.data = app.store.snapshot();
              app.data.put("timers", new JSONArray().put(remote));
              boolean blocked = false;
              try {
                app.startTimer("sleep", timed);
              } catch (AppController.TimerAlreadyRunning expected) {
                blocked = true;
              }
              check(
                  blocked && app.store.timers().isEmpty(),
                  "Cached timer from another caregiver blocks a different activity");
              app.data
                  .getJSONArray("timers")
                  .put(Records.copy(remote).put("id", 902).put("name", "Sleep"));
              app.listener.run();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      check(
          awaitText("Multiple timers found"),
          "Simultaneous server timers have an explicit conflict notice");
      check(
          app.pending().isEmpty() && app.data.getJSONArray("timers").length() == 2,
          "Conflicting server timers are never automatically removed or merged");
      capture("19-timer-conflict");
      click("Cancel timer");
      click("Cancel timer");
      check(
          app.pending().size() == 1 && app.pending().get(0).getString("method").equals("DELETE"),
          "Explicit conflict cancellation queues removal of one timer only");
      check(
          !contains("Multiple timers found") && contains("Finish & log"),
          "Remaining timer stays accessible after choosing an extra timer to cancel");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
  }

  private void rejectionPresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    app.credentials.save("https://sync-test.invalid", "synthetic-sync-token");
    try {
      JSONObject data = DemoData.create();
      JSONObject payload =
          new JSONObject()
              .put("child", 1)
              .put("nap", false)
              .put("start", "2026-01-01T01:00:00Z")
              .put("end", "2026-01-01T02:00:00Z")
              .put("notes", "Keep this original note");
      long id = app.store.enqueue("sleep", payload);
      List<JSONObject> before = app.store.pending();
      app.store.state(
          id,
          "rejected",
          "Server returned 400. Another entry intersects the specified time period. <a"
              + " href='private.invalid'>Private child</a>");
      runOnMainSync(
          () -> {
            app.demo = false;
            app.busy = false;
            app.data = data;
            app.store.replace(data);
            app.syncProblem = SyncFeedback.newProblem(before, app.store.pending());
            app.listener.run();
          });
      check(
          awaitText("Sleep needs attention")
              && contains("same type for this child")
              && !contains("private.invalid"),
          "New rejection appears without opening Settings and without raw server details");
      pause();
      capture("20-rejection-notice");
      click("Review entry");
      check(
          awaitText("Edit activity") && contains("Delete pending entry"),
          "Rejected activity offers correction and deletion");
      pause();
      capture("21-rejection-actions");
      click("Edit activity");
      check(
          awaitText("Edit sleep")
              && contains("Keep this original note")
              && !contains("Start timer now"),
          "Edit form prefills the rejected activity without timer mode");
      pause();
      capture("22-edit-rejected");
      click("Cancel");
      check(
          app.store
                  .pending()
                  .get(0)
                  .getJSONObject("payload")
                  .getString("notes")
                  .equals("Keep this original note")
              && app.store.pending().get(0).getString("state").equals("rejected"),
          "Cancel leaves rejected activity untouched");
      click("Pending entries (1)");
      click("Sleep · Times overlap");
      click("Edit activity");
      setTextByHint("Optional", "Corrected note");
      // Keep this UI test fully offline: saving still exercises the real controller/store path.
      app.credentials.clear();
      click("Save changes");
      check(
          app.store.pending().size() == 1
              && app.store.pending().get(0).getLong("local_id") == id
              && app.store.pending().get(0).getString("state").equals("queued")
              && app.store
                  .pending()
                  .get(0)
                  .getJSONObject("payload")
                  .getString("notes")
                  .equals("Corrected note"),
          "Save changes updates the same durable pending entry");
      app.credentials.save("https://sync-test.invalid", "synthetic-sync-token");
      app.store.state(id, "review", "Unknown outcome");
      runOnMainSync(() -> app.listener.run());
      click("Settings");
      click("Pending entries (1)");
      click("Sleep · Check before retrying");
      check(
          awaitText("couldn't confirm") && !contains("Edit activity"),
          "Unknown-outcome uploads retain review-only handling");
      click("Delete pending entry");
      check(
          awaitText("does not delete a server record"),
          "Deletion clearly identifies the local copy");
      click("Delete pending copy");
      pause();
      check(
          app.store.pending().isEmpty()
              && app.store.snapshot().getJSONArray("sleep").length()
                  == data.getJSONArray("sleep").length(),
          "Deleting a pending entry keeps downloaded server activities intact");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
  }

  private void editingPresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    try {
      runOnMainSync(
          () -> {
            app.demo();
            app.listener.run();
          });
      JSONObject note = app.data.getJSONArray("notes").getJSONObject(0);
      note.put("time", java.time.Instant.now().minusSeconds(60).toString());
      note.put("note", "Historical activity to edit");
      long recordId = note.getLong("id");
      runOnMainSync(() -> app.listener.run());
      click("Timeline");
      click("Historical activity to edit");
      click("Edit activity");
      check(
          awaitText("Edit note"),
          "Historical activities use the same editor as pending corrections");
      setTextByHint("Required", "Historical activity corrected");
      setTextByHint("e.g. night, milestone", "");
      click("Save changes");
      pause();
      check(
          app.data.getJSONArray("notes").length() == 1
              && app.data.getJSONArray("notes").getJSONObject(0).getLong("id") == recordId
              && app.data
                  .getJSONArray("notes")
                  .getJSONObject(0)
                  .getString("note")
                  .equals("Historical activity corrected")
              && app.data.getJSONArray("notes").getJSONObject(0).getJSONArray("tags").length() == 0,
          "Historical save changes the existing activity and can clear tags");
      JSONObject payload =
          new JSONObject()
              .put("child", 1)
              .put("note", "Rejected card to correct")
              .put("time", java.time.Instant.now().toString());
      long id = app.store.enqueue("notes", payload);
      app.store.state(id, "rejected", "Server returned 400. This field is required.");
      app.credentials.save("https://sync-test.invalid", "synthetic-sync-token");
      runOnMainSync(
          () -> {
            app.demo = false;
            app.listener.run();
          });
      click("Timeline");
      pause();
      check(
          contains("Needs attention") && contains("Synced"),
          "Activity cards have discreet accessible error and confirmed status symbols");
      capture("23-card-status");
      click("Rejected card to correct");
      check(
          awaitText("required value is missing") && contains("Edit activity"),
          "A rejected card opens its specific explanation and edit action");
      click("Edit activity");
      check(
          awaitText("Edit note") && contains("Rejected card to correct"),
          "Card correction opens the shared editor with its original values");
      click("Cancel");
      app.store.state(id, "queued", "Waiting to sync");
      runOnMainSync(
          () -> {
            app.busy = true;
            app.listener.run();
          });
      click("Today");
      pause();
      check(
          contains("Saved on this phone; waiting to sync") && contains("Rejected card to correct"),
          "Local entries and their status appear on Today as well as Timeline");
      capture("24-local-status");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
  }

  private void syncedDeletions() throws Exception {
    String name = "deletion-test.db";
    getTargetContext().deleteDatabase(name);
    LocalStore store = new LocalStore(getTargetContext(), name);
    JSONObject original =
        new JSONObject()
            .put("id", 31)
            .put("child", 1)
            .put("note", "Offline deletion")
            .put("time", "2026-01-01T00:00:00Z");
    JSONObject other = Records.copy(original).put("id", 32).put("child", 2);
    store.replace(new JSONObject().put("notes", new JSONArray().put(original).put(other)));
    store.enqueueDeletion("notes", original);
    long id = store.pending().get(0).getLong("local_id");
    boolean duplicate = false, editBlocked = false;
    try {
      store.enqueueDeletion("notes", original);
    } catch (IllegalArgumentException expected) {
      duplicate = true;
    }
    try {
      store.enqueueEdit("notes", original, Records.copy(original).put("note", "Edit"));
    } catch (IllegalArgumentException expected) {
      editBlocked = true;
    }
    check(
        duplicate && editBlocked && store.pending().size() == 1,
        "Repeated delete/edit actions preserve one pending change per original ID");
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        ActivityDeletions.isDeletion(store.pending().get(0))
            && store.pending().get(0).getJSONObject("original").getLong("id") == 31,
        "Offline deletion intent and original identity survive database reopen");
    check(
        store.snapshot().getJSONArray("notes").length() == 2,
        "Pending deletion preserves confirmed history until accepted");
    store.claim(id);
    store.close();
    store = new LocalStore(getTargetContext(), name);
    check(
        store.pending().get(0).getString("state").equals("review"),
        "Interrupted deletion remains uncertain after restart");
    store.removedActivity(id, "notes", 31);
    check(
        store.pending().isEmpty()
            && store.snapshot().getJSONArray("notes").length() == 1
            && store.snapshot().getJSONArray("notes").getJSONObject(0).getLong("id") == 32,
        "Confirmed deletion atomically removes only the target record and its queue entry");
    store.enqueueDeletion("notes", other);
    store.discard(store.pending().get(0).getLong("local_id"));
    check(
        store.snapshot().getJSONArray("notes").length() == 1,
        "Discarding a deletion request preserves the cached server record");
    store.enqueueEdit("notes", other, Records.copy(other).put("note", "Pending edit"));
    boolean deletionBlocked = false;
    try {
      store.enqueueDeletion("notes", other);
    } catch (IllegalArgumentException expected) {
      deletionBlocked = true;
    }
    check(deletionBlocked, "Deletion cannot erase an existing pending edit");
    store.close();
    getTargetContext().deleteDatabase(name);
  }

  private void agePresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    try {
      runOnMainSync(app::demo);
      JSONObject note = app.data.getJSONArray("notes").getJSONObject(0);
      note.put("time", java.time.Instant.now().minusSeconds(18 * 3600 + 40 * 60 + 5).toString());
      note.put("note", "Age-format test");
      runOnMainSync(() -> app.listener.run());
      click("Timeline");
      check(awaitText("18h 40m ago"), "History retains minutes after twelve hours");
      readableChoice("18h 40m ago");
      capture("29-history-hours-minutes");
    } finally {
      runOnMainSync(app::disconnect);
    }
  }

  private void promotionState() throws Exception {
    AppController app = AppController.get(getTargetContext());
    NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
    boolean supported = Build.VERSION.SDK_INT >= 36;
    boolean allowed = supported && manager.canPostPromotedNotifications();
    JSONObject start =
        new JSONObject()
            .put("child", 1)
            .put("start", java.time.Instant.now().minusSeconds(65).toString());
    try {
      app.store.replace(DemoData.create().put("timers", new JSONArray()));
      app.store.startTimer("sleep", start);
      long id = app.store.timers().get(0).getLong("id");
      runOnMainSync(
          () -> {
            app.busy = true;
            app.data = app.store.snapshot();
            app.refreshNotifications();
          });
      pause();
      Notification local = timerNotifications(manager)[0].getNotification();
      check(
          local.extras.getBoolean(TimerNotifications.REQUEST_PROMOTION) == allowed,
          "Local offline start requests promotion only when supported and allowed");
      if (allowed) {
        check(
            local.hasPromotableCharacteristics(),
            "Local timer meets native promotion requirements");
        check(
            (local.flags & Notification.FLAG_PROMOTED_ONGOING) != 0,
            "Android actually promoted the local timer");
      }
      check(
          !local.extras.containsKey("android.shortCriticalText"),
          "No static chip text overrides the native count-up chronometer");
      if (supported) {
        check(local.deleteIntent != null, "Local timer has an explicit dismissal receiver");
        local.deleteIntent.send();
        pause();
      }
      // A new manager instance must use persisted dismissal state, not an in-memory flag.
      new TimerNotifications(getTargetContext())
          .update(app.data, app.timers(), app.pending(), false);
      pause();
      check(
          !timerNotifications(manager)[0]
              .getNotification()
              .extras
              .getBoolean(TimerNotifications.REQUEST_PROMOTION),
          "Dismissed timer is not promoted again after manager recreation");
      List<JSONObject> linked = app.timers();
      linked.get(0).put("server_id", 901);
      JSONObject remote = Records.copy(start).put("id", 901).put("name", "Sleep");
      JSONObject data = Records.copy(app.data).put("timers", new JSONArray().put(remote));
      new TimerNotifications(getTargetContext()).update(data, linked, app.pending(), false);
      pause();
      check(
          timerNotifications(manager).length == 1
              && !timerNotifications(manager)[0]
                  .getNotification()
                  .extras
                  .getBoolean(TimerNotifications.REQUEST_PROMOTION),
          "Server publication preserves dismissal and deduplicates the local timer");
      new TimerNotifications(getTargetContext())
          .update(data, Collections.emptyList(), Collections.emptyList(), false);
      pause();
      Notification shared = timerNotifications(manager)[0].getNotification();
      check(
          !shared.extras.getBoolean(TimerNotifications.REQUEST_PROMOTION)
              && shared.deleteIntent == null,
          "A caregiver-only timer never requests promotion");
      // Ending a timer prunes dismissal state. A later timer can be promoted.
      app.store.discardTimer(id);
      new TimerNotifications(getTargetContext())
          .update(new JSONObject(), Collections.emptyList(), Collections.emptyList(), false);
      pause();
      check(timerNotifications(manager).length == 0, "Ended timer notification is removed");
      app.store.startTimer(
          "sleep", Records.copy(start).put("start", java.time.Instant.now().toString()));
      runOnMainSync(app::refreshNotifications);
      pause();
      check(
          timerNotifications(manager)[0]
                  .getNotification()
                  .extras
                  .getBoolean(TimerNotifications.REQUEST_PROMOTION)
              == allowed,
          "A new local timer remains eligible after an earlier timer was dismissed");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
    pause();
    check(timerNotifications(manager).length == 0, "Disconnect clears active notifications");
  }

  private void deletionPresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    try {
      runOnMainSync(() -> app.demo());
      JSONObject note = app.data.getJSONArray("notes").getJSONObject(0);
      note.put("time", java.time.Instant.now().minusSeconds(60).toString());
      note.put("note", "Activity to delete");
      runOnMainSync(() -> app.listener.run());
      click("Timeline");
      click("Activity to delete");
      pause();
      readableChoice("Edit activity");
      readableChoice("Delete activity");
      capture("25-activity-details");
      click("Delete activity");
      check(awaitText("Delete this activity?"), "Deletion requires deliberate confirmation");
      click("Keep activity");
      check(app.data.getJSONArray("notes").length() == 1, "Keep activity cancels deletion");
      click("Activity to delete");
      click("Delete activity");
      pause();
      capture("25-delete-confirmation");
      click("Delete activity");
      check(
          app.data.getJSONArray("notes").length() == 0, "Confirmed sample deletion removes record");

      // The connected fixture is held offline without running any network request.
      app.store.replace(new JSONObject().put("notes", new JSONArray().put(note)));
      app.credentials.save("https://sync-test.invalid", "synthetic-sync-token");
      runOnMainSync(
          () -> {
            app.demo = false;
            app.busy = true;
            Records.put(app.data, "notes", new JSONArray().put(note));
            app.listener.run();
          });
      click("Timeline");
      click("Activity to delete");
      click("Delete activity");
      check(awaitText("for all caregivers"), "Server deletion confirmation explains shared effect");
      pause();
      capture("26-server-delete-confirmation");
      click("Delete activity");
      check(
          app.pending().size() == 1 && ActivityDeletions.isDeletion(app.pending().get(0)),
          "UI queues synced deletion durably while offline");
      click("Activity to delete");
      check(
          awaitText("Waiting to delete") && contains("Discard deletion request"),
          "Pending deletion card opens its own review flow");
      check(!contains("Edit activity"), "Pending deletion cannot be edited into another operation");
      pause();
      capture("26-deletion-pending");
      click("Close");
      app.store.state(
          app.pending().get(0).getLong("local_id"), "rejected", ActivityDeletions.CONFLICT);
      runOnMainSync(
          () -> {
            app.busy = false;
            app.listener.run();
          });
      click("Activity to delete");
      check(awaitText("changed on another device"), "Caregiver deletion conflict is explained");
      click("Discard deletion request");
      check(
          awaitText("does not restore"), "Discard confirmation never promises server restoration");
      click("Discard request");
      check(
          app.pending().isEmpty() && app.store.snapshot().getJSONArray("notes").length() == 1,
          "Discard request preserves original record");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
  }

  private android.service.notification.StatusBarNotification[] timerNotifications(
      NotificationManager manager) {
    // Android may add its own automatic group-summary notification.
    return Arrays.stream(manager.getActiveNotifications())
        .filter(n -> n.getTag() != null && n.getTag().startsWith("timer:"))
        .toArray(android.service.notification.StatusBarNotification[]::new);
  }

  private void notificationPresentation() throws Exception {
    AppController app = AppController.get(getTargetContext());
    NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
    if (Build.VERSION.SDK_INT >= 33)
      getUiAutomation()
          .grantRuntimePermission(
              getTargetContext().getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
    try {
      runOnMainSync(() -> app.demo());
      JSONObject start =
          new JSONObject()
              .put("child", app.child())
              .put("start", java.time.Instant.now().minusSeconds(65).toString());
      runOnMainSync(
          () -> {
            try {
              app.startTimer("sleep", start);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      pause();
      android.service.notification.StatusBarNotification[] active = timerNotifications(manager);
      check(active.length == 1, "One notification represents a linked local/shared timer once");
      Notification notification = active[0].getNotification();
      check(
          (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0
              && notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
              && notification.when == Records.time(start).toEpochMilli(),
          "Ongoing notification uses the saved start time and Android's chronometer");
      check(
          notification.visibility == Notification.VISIBILITY_PRIVATE
              && notification.publicVersion != null
              && !notification
                  .publicVersion
                  .extras
                  .getCharSequence(Notification.EXTRA_TITLE)
                  .toString()
                  .contains("Maya"),
          "Lock-screen public view hides child and activity details");
      check(
          manager.getNotificationChannel(TimerNotifications.CHANNEL).getImportance()
              == NotificationManager.IMPORTANCE_LOW,
          "Timer channel is quiet and low importance");
      getUiAutomation()
          .performGlobalAction(
              android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
      pause();
      capture("27-timer-chip-home");
      SystemClock.sleep(2200);
      capture("27-timer-chip-home-later");
      getUiAutomation().executeShellCommand("cmd statusbar expand-notifications").close();
      pause();
      capture("27-timer-notification");
      getUiAutomation().executeShellCommand("cmd statusbar collapse").close();
      pause();
      notification.contentIntent.send();
      pause();
      click("Settings");
      notification.contentIntent.send();
      pause();
      check(awaitText("Stop & save"), "Notification tap opens Today with the running timer");
      runOnMainSync(
          () -> {
            try {
              app.startTimer("sleep", Records.copy(start).put("child", 2));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      pause();
      check(
          timerNotifications(manager).length == 2,
          "Different children retain separate notifications: "
              + Arrays.toString(
                  Arrays.stream(manager.getActiveNotifications()).map(n -> n.getTag()).toArray()));
      runOnMainSync(
          () -> {
            try {
              app.cancelTimer(app.timers().get(1).getLong("id"));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      pause();
      check(
          timerNotifications(manager).length == 1,
          "Cancelling one child's timer removes only its notification");
      click("Stop & save");
      pause();
      check(timerNotifications(manager).length == 0, "Stopping a timer removes its notification");

      JSONObject local =
          new JSONObject().put("id", 1).put("endpoint", "sleep").put("payload", start);
      JSONObject shared = Records.copy(start).put("id", 55);
      JSONObject data = new JSONObject().put("timers", new JSONArray().put(shared));
      local.put("server_id", 55);
      check(
          TimerNotifications.running(data, Arrays.asList(local), Collections.emptyList()).size()
              == 1,
          "Shared and local identities reconcile into one timer notification");
      JSONObject finish = new JSONObject().put("cleanup_timer", 55).put("payload", start);
      check(
          TimerNotifications.running(data, Arrays.asList(local), Arrays.asList(finish)).isEmpty(),
          "Pending shared finish hides the stale server timer notification");
      local.remove("server_id");
      local.put("finish_payload", start);
      check(
          TimerNotifications.running(
                  new JSONObject(), Arrays.asList(local), Collections.emptyList())
              .isEmpty(),
          "Uncertain start with a saved stop does not show as running");
    } finally {
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    }
  }

  private void scanner(Activity activity) throws Exception {
    String server = "https://qr-test.example/api/";
    String token = "0123456789abcdef0123456789abcdef01234567";
    setTextByHint("https://your-server.up.railway.app", "https://manual.example");
    ActivityMonitor scannerMonitor =
        addMonitor("com.babybuddypocket.app.QrScanActivity", null, false);
    click("Scan Baby Buddy QR code");
    waitForMonitorWithTimeout(scannerMonitor, 5000);
    removeMonitor(scannerMonitor);
    if (scanMode.equals("denied")) {
      boolean denied = false;
      for (int i = 0; i < 30 && !denied; i++) {
        AccessibilityNodeInfo root = uiRoot();
        if (root != null)
          for (AccessibilityNodeInfo node : nodes(root)) {
            String label =
                String.valueOf(node.getText()).replace('\u2019', '\'').toLowerCase(Locale.ROOT);
            if (label.equals("don't allow") || label.equals("deny"))
              denied = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
          }
        if (!denied) SystemClock.sleep(200);
      }
      check(denied, "Camera permission dialog can be declined");
      pause();
      check(awaitText("Camera access is off"), "Permission refusal has a usable fallback");
      click("Enter details manually");
      pause();
      check(awaitText("https://manual.example"), "Cancel preserves manual URL");
    } else {
      // The emulator camera displays a real encoded synthetic QR image; no injected scan result.
      for (int i = 0; i < 150 && !contains(server); i++) SystemClock.sleep(200);

      check(
          contains(server),
          "Camera QR scan returns the normalized server URL; preview ready="
              + contains("Keep the whole QR"));
      boolean[] filled = {false};
      runOnMainSync(
          () -> filled[0] = tokenFieldMatches(activity.getWindow().getDecorView(), token));
      check(filled[0], "Camera QR scan fills the correct masked API key");
      check(
          new Credentials(getTargetContext()).server().isEmpty(),
          "Scan waits for Connect before saving or sending credentials");
      capture("08-qr-filled");
    }
  }

  private boolean tokenFieldMatches(android.view.View view, String token) {
    if (view instanceof android.widget.EditText) {
      android.widget.EditText field = (android.widget.EditText) view;
      if ("Paste your API token".contentEquals(String.valueOf(field.getHint())))
        return token.contentEquals(field.getText())
            && field.getTransformationMethod()
                instanceof android.text.method.PasswordTransformationMethod;
    }
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (tokenFieldMatches(group.getChildAt(i), token)) return true;
    }
    return false;
  }

  private void timerLifecycle() throws Exception {
    Credentials credentials = new Credentials(getTargetContext());
    String fixture = "https://notification-test.invalid";
    if (timerPhase.equals("seed-denied")) {
      check(credentials.server().isEmpty(), "Lifecycle fixture requires a disconnected test app");
      AppController app = AppController.get(getTargetContext());
      JSONObject start =
          new JSONObject()
              .put("child", 1)
              .put("start", java.time.Instant.now().minusSeconds(65).toString());
      app.store.clear();
      app.store.replace(DemoData.create());
      app.store.startTimer("sleep", start);
      app.preferences.edit().clear().commit();
      credentials.save(fixture, "synthetic-notification-token");
      runOnMainSync(
          () -> {
            app.demo = false;
            app.busy = true;
            app.data = app.store.snapshot();
          });
      startActivitySync(
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      if (Build.VERSION.SDK_INT >= 33) {
        check(
            awaitText("Allow Baby Buddy Pocket"),
            "Timer permission is requested when a timer exists");
        capture("28-notification-permission");
        boolean denied = false;
        for (AccessibilityNodeInfo node : nodes(uiRoot())) {
          String label = String.valueOf(node.getText()).replace('\u2019', '\'');
          if (label.equalsIgnoreCase("Don't allow") || label.equalsIgnoreCase("Deny"))
            denied = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        check(denied, "Permission refusal action is available");
        pause();
        check(
            !getTargetContext()
                .getSystemService(NotificationManager.class)
                .areNotificationsEnabled(),
            "Notification permission can be declined");
      }
      check(
          app.store.timers().size() == 1
              && app.store
                  .timers()
                  .get(0)
                  .getJSONObject("payload")
                  .getString("start")
                  .equals(start.getString("start")),
          "Denied notification permission preserves the durable timer and start");
      check(awaitText("Stop & save"), "Timer remains usable with notifications denied");
      return;
    }
    check(
        credentials.server().equals(ApiClient.normalize(fixture).toString())
            && credentials.token().equals("synthetic-notification-token"),
        "Lifecycle continuation only touches its synthetic fixture");
    AppController app = AppController.get(getTargetContext());
    NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
    if (timerPhase.equals("dismiss")) {
      runOnMainSync(app::refreshNotifications);
      pause();
      Notification notification = timerNotifications(manager)[0].getNotification();
      check(notification.deleteIntent != null, "Restored local timer supports dismissal");
      notification.deleteIntent.send();
      pause();
      check(
          app.preferences.getStringSet("dismissed_timer_promotions", Collections.emptySet()).size()
              == 1,
          "Dismissal is committed before process exit");
    } else if (timerPhase.equals("restore") || timerPhase.equals("restore-dismissed")) {
      check(app.store.timers().size() == 1, "Timer survives process/device restart");
      runOnMainSync(app::refreshNotifications);
      pause();
      android.service.notification.StatusBarNotification[] active = timerNotifications(manager);
      check(active.length == 1, "Timer notification is present after restoration");
      check(
          active[0].getNotification().when
              == Records.time(app.store.timers().get(0).getJSONObject("payload")).toEpochMilli(),
          "Restored notification retains the original elapsed-time base");
      if (timerPhase.equals("restore-dismissed")) {
        check(
            !active[0].getNotification().extras.getBoolean(TimerNotifications.REQUEST_PROMOTION)
                && app.preferences
                        .getStringSet("dismissed_timer_promotions", Collections.emptySet())
                        .size()
                    == 1,
            "Dismissal survives a real process restart without losing the timer");
      }
    } else if (timerPhase.equals("finish")) {
      runOnMainSync(
          () -> {
            app.busy = true;
            try {
              JSONObject timer = app.store.timers().get(0);
              app.finishTimer(
                  timer.getLong("id"),
                  Records.copy(timer.getJSONObject("payload"))
                      .put("end", java.time.Instant.now().toString()));
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      pause();
      check(timerNotifications(manager).length == 0, "Offline Stop removes notification");
      check(
          app.store.timers().isEmpty()
              && app.store.pending().size() == 1
              && app.store.pending().get(0).getString("endpoint").equals("sleep"),
          "Offline Stop preserves a single completed log");
    } else if (timerPhase.equals("verify-finished")) {
      check(
          timerNotifications(manager).length == 0 && app.store.timers().isEmpty(),
          "Restart does not resurrect a stopped timer notification");
      check(
          app.store.pending().size() == 1
              && app.store.pending().get(0).getString("endpoint").equals("sleep"),
          "Completed offline log survives restart");
      runOnMainSync(
          () -> {
            app.busy = false;
            app.disconnect();
          });
    } else throw new IllegalArgumentException("Unknown timer lifecycle phase");
  }

  private void upgrade() throws Exception {
    Credentials credentials = new Credentials(getTargetContext());
    LocalStore store = new LocalStore(getTargetContext());
    if (upgradePhase.equals("seed-legacy-demo")) {
      check(
          credentials.server().isEmpty() && store.pending().isEmpty(), "Requires a clean emulator");
      try (LocalStore legacy = new LocalStore(getTargetContext(), "public-demo.db")) {
        legacy.replace(new JSONObject().put("legacy_demo_marker", true));
      }
      getTargetContext()
          .getSharedPreferences("preferences", 0)
          .edit()
          .putBoolean("demo", true)
          .putBoolean("public_demo", true)
          .commit();
      store.replace(new JSONObject().put("family_marker", "preserved"));
      store.enqueue(
          "notes", new JSONObject().put("child", 999).put("note", "synthetic migration test"));
      store.close();
      return;
    }
    if (upgradePhase.equals("verify-legacy-demo")) {
      AppController[] controller = new AppController[1];
      runOnMainSync(() -> controller[0] = AppController.get(getTargetContext()));
      AppController app = controller[0];
      check(app.demo && !app.connected(), "Old demo selection becomes local demo");
      check(
          app.childRecord().optString("first_name").equals("Maya"),
          "Local synthetic data replaces public cache");
      check(
          !getTargetContext().getDatabasePath("public-demo.db").exists(), "Retired cache removed");
      check(!app.preferences.contains("public_demo"), "Retired preference removed");
      check(
          store.snapshot().optString("family_marker").equals("preserved"),
          "Family cache unchanged");
      check(store.pending().size() == 1, "Family outbox unchanged");
      runOnMainSync(app::disconnect);
      store.close();
      return;
    }
    if (upgradePhase.equals("seed")) {
      check(
          credentials.server().isEmpty() && store.pending().isEmpty(),
          "Upgrade test requires a clean emulator");
      credentials.save("https://upgrade-test.invalid", "synthetic-upgrade-token");
      store.replace(new JSONObject().put("upgrade_marker", "retained"));
      store.enqueue(
          "notes", new JSONObject().put("child", 999).put("note", "synthetic upgrade outbox"));
      store.startTimer(
          "sleep",
          new JSONObject()
              .put("child", 999)
              .put("nap", false)
              .put("start", "2026-01-01T01:00:00Z"));
      getTargetContext()
          .getSharedPreferences("preferences", 0)
          .edit()
          .putBoolean("pounds", true)
          .commit();
    } else {
      runOnMainSync(() -> AppController.get(getTargetContext()));
      check(
          credentials.server().equals("https://upgrade-test.invalid/api/"),
          "Connection survives update");
      check(
          credentials.token().equals("synthetic-upgrade-token"),
          "Keystore key and token survive update");
      check(
          store.snapshot().optString("upgrade_marker").equals("retained"), "Cache survives update");
      check(
          store.pending().size() == 1
              && store
                  .pending()
                  .get(0)
                  .getJSONObject("payload")
                  .getString("note")
                  .equals("synthetic upgrade outbox"),
          "Unsent outbox survives update");
      check(
          getTargetContext().getSharedPreferences("preferences", 0).getBoolean("pounds", false),
          "Settings survive update");
      JSONObject legacy = store.timers().get(0);
      check(
          !legacy.has("start_id") && !legacy.has("server_id"),
          "Upgrade preserves existing phone-only timer without publishing it");
      store.finishTimer(
          legacy.getLong("id"),
          Records.copy(legacy.getJSONObject("payload")).put("end", "2026-01-01T02:00:00Z"));
      check(
          store.timers().isEmpty()
              && store.pending().size() == 2
              && !store.pending().get(1).getJSONObject("payload").has("timer"),
          "Older phone timer still finishes as an ordinary duration entry");
      if (upgradePhase.equals("verify-clear")) {
        credentials.clear();
        store.clear();
        getTargetContext().getSharedPreferences("preferences", 0).edit().clear().commit();
      }
    }
    store.close();
  }

  private void credentials() throws Exception {
    Credentials credentials = new Credentials(getTargetContext());
    credentials.save("https://example.invalid", "synthetic-test-token");
    check(credentials.token().equals("synthetic-test-token"), "Keystore token round trip");
    String stored =
        getTargetContext()
            .getSharedPreferences("connection", Context.MODE_PRIVATE)
            .getString("token", "");
    check(!stored.contains("synthetic-test-token"), "Token is not plaintext");
    credentials.clear();
    check(credentials.server().isEmpty(), "Credentials cleared");
  }

  private void check(boolean condition, String name) {
    if (!condition) throw new AssertionError(name);
    assertions++;
  }

  private void pause() {
    SystemClock.sleep(800);
    waitForIdleSync();
  }

  private AccessibilityNodeInfo uiRoot() {
    AccessibilityNodeInfo active = getUiAutomation().getRootInActiveWindow();
    if (active != null && active.refresh()) return active;
    for (android.view.accessibility.AccessibilityWindowInfo window :
        getUiAutomation().getWindows()) {
      if (window.isFocused()) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root != null && root.refresh()) return root;
      }
    }
    return getUiAutomation().getRootInActiveWindow();
  }

  private boolean awaitText(String text) {
    for (int attempt = 0; attempt < 25; attempt++) {
      if (contains(text)) return true;
      SystemClock.sleep(200);
    }
    return false;
  }

  private boolean contains(String text) {
    AccessibilityNodeInfo root = uiRoot();
    for (int i = 0; root == null && i < 25; i++) {
      SystemClock.sleep(200);
      root = uiRoot();
    }
    if (root == null) return false;
    for (AccessibilityNodeInfo node : nodes(root))
      if (String.valueOf(node.getText()).contains(text)
          || String.valueOf(node.getHintText()).contains(text)
          || String.valueOf(node.getContentDescription()).contains(text)) return true;
    return false;
  }

  private boolean checked(String text) {
    for (AccessibilityNodeInfo node : uiRoot().findAccessibilityNodeInfosByText(text))
      if (node.isCheckable() && node.isChecked()) return true;
    return false;
  }

  private android.graphics.Rect bounds(String label) {
    for (AccessibilityNodeInfo node : nodes(uiRoot()))
      if (label.contentEquals(String.valueOf(node.getText()))
          || label.contentEquals(String.valueOf(node.getContentDescription()))) {
        android.graphics.Rect rect = new android.graphics.Rect();
        node.getBoundsInScreen(rect);
        return rect;
      }
    throw new AssertionError("Missing control: " + label);
  }

  private List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root) {
    List<AccessibilityNodeInfo> list = new ArrayList<>();
    list.add(root);
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo child = root.getChild(i);
      if (child != null) list.addAll(nodes(child));
    }
    return list;
  }

  private void setTextByHint(String hint, String value) {
    for (int attempt = 0; attempt < 25; attempt++) {
      AccessibilityNodeInfo root = uiRoot();
      if (root != null) {
        for (AccessibilityNodeInfo node : nodes(root)) {
          if (node.isEditable()
              && (hint.contentEquals(String.valueOf(node.getHintText()))
                  || hint.contentEquals(String.valueOf(node.getText())))) {
            Bundle args = new Bundle();
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return;
          }
        }
      }
      SystemClock.sleep(200);
    }
    throw new AssertionError("No editable field: " + hint);
  }

  private void click(String text) {
    for (int attempt = 0; attempt < 25; attempt++) {
      AccessibilityNodeInfo root = uiRoot();
      if (root == null) {
        SystemClock.sleep(200);
        continue;
      }
      List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
      matches.sort(
          (a, b) ->
              Boolean.compare(
                  !(text.contentEquals(String.valueOf(a.getText()))
                      || text.contentEquals(String.valueOf(a.getContentDescription()))),
                  !(text.contentEquals(String.valueOf(b.getText()))
                      || text.contentEquals(String.valueOf(b.getContentDescription())))));
      for (AccessibilityNodeInfo match : matches) {
        AccessibilityNodeInfo node = match;
        while (node != null) {
          if (node.isClickable()) {
            node.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
            pause();
            node.refresh();
            android.graphics.Rect hit = new android.graphics.Rect();
            android.graphics.Rect window = new android.graphics.Rect();
            node.getBoundsInScreen(hit);
            uiRoot().getBoundsInScreen(window);
            if (node.isVisibleToUser() && hit.intersect(window) && !hit.isEmpty()) {
              long now = SystemClock.uptimeMillis();
              android.view.MotionEvent down =
                  android.view.MotionEvent.obtain(
                      now,
                      now,
                      android.view.MotionEvent.ACTION_DOWN,
                      hit.centerX(),
                      hit.centerY(),
                      0);
              android.view.MotionEvent up =
                  android.view.MotionEvent.obtain(
                      now,
                      now + 50,
                      android.view.MotionEvent.ACTION_UP,
                      hit.centerX(),
                      hit.centerY(),
                      0);
              down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
              up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
              try {
                check(
                    getUiAutomation().injectInputEvent(down, true)
                        && getUiAutomation().injectInputEvent(up, true),
                    "Visible control accepts touch: " + text);
              } finally {
                down.recycle();
                up.recycle();
              }
              waitForIdleSync();
              return;
            }
          }
          node = node.getParent();
        }
      }
      SystemClock.sleep(200);
    }
    throw new AssertionError("No clickable control: " + text);
  }

  private void capture(String name) throws Exception {
    File directory = new File(getTargetContext().getExternalFilesDir(null), "screenshots");
    if (!directory.exists() && !directory.mkdirs())
      throw new IOException("Cannot create screenshot directory");
    Bitmap image = getUiAutomation().takeScreenshot();
    if (image == null) throw new IOException("Screenshot unavailable");
    try (FileOutputStream stream =
        new FileOutputStream(new File(directory, screenPrefix + name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    image.recycle();
  }
}
