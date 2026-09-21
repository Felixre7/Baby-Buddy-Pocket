package com.babybuddypocket.app;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.*;
import org.json.*;

/** Runs with the platform instrumentation API; no instrumentation libraries in the project. */
public final class SmokeInstrumentation extends Instrumentation {
  private int assertions;
  private String screenPrefix = "";
  private String upgradePhase = "", scanMode = "";

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    screenPrefix = args == null ? "" : args.getString("prefix", "");
    upgradePhase = args == null ? "" : args.getString("upgrade", "");
    scanMode = args == null ? "" : args.getString("scan", "");
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
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
      database();
      serverTimers();
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
      check(contains("Maya"), "Dashboard shows selected child");
      check(contains("DEMO"), "Demo is labeled");
      click("Timeline");
      pause();
      capture("03-timeline");
      check(contains("Search notes"), "Timeline has search");
      click("Trends");
      pause();
      check(contains("past seven days"), "Trends visible");
      capture("04-trends");
      click("Settings");
      pause();
      check(contains("exploring the demo"), "Demo stays isolated");
      capture("05-settings");
      click("Today");
      pause();
      click("Log activity");
      pause();
      check(contains("Choose your activities"), "Log activity opens the grid page");
      capture("06-activity-grid");
      click("Note");
      pause();
      setTextByHint("Required", "A sample note saved by the Android smoke test");
      capture("06-note-form");
      ActivityMonitor rotation = addMonitor("com.babybuddypocket.app.MainActivity", null, false);
      runOnMainSync(
          () -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      pause();
      Activity rotated = rotation.getLastActivity();
      removeMonitor(rotation);
      capture("07-landscape");
      check(contains("A sample note saved"), "Form draft survives rotation");
      click("Add sample");
      pause();
      click("Timeline");
      pause();
      check(contains("A sample note saved"), "Sample log appears in timeline");
      runOnMainSync(
          () -> rotated.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      pause();
      timerFlow();
      customizationFlow();
      syncPresentation();
      result.putString(
          "stream",
          "\nPASS: "
              + assertions
              + " assertions; SQLite restart/outbox, token encryption, navigation, logging form,"
              + " rotation, screenshots.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      try {
        capture("failure");
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
    check(contains("Start") && contains("End"), "Manual time entry remains available");
    click("Start a timer now");
    pause();
    click("Start timer");
    pause();
    check(contains("Stop & save"), "Running timer has a direct dashboard stop");
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
    check(contains("Stop & save"), "Hiding an activity cannot strand its running timer");
    int before = app.data.getJSONArray("sleep").length();
    click("Stop & save");
    pause();
    check(!contains("Stop & save") && app.timers().isEmpty(), "One tap stops the timer");
    check(
        app.data.getJSONArray("sleep").length() == before + 1,
        "One tap also saves exactly one activity");
    runOnMainSync(() -> app.activities().visible("sleep", true));
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
    for (AccessibilityNodeInfo node :
        getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Tummy time"))
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
    check(contains("formula") && contains("bottle"), "Next form prefills last feeding choices");
    check(
        checked("Start a timer now") && !contains("End *"),
        "A new form returns to timer mode after a manual log");
    boolean freshAmount = false;
    for (AccessibilityNodeInfo node : nodes(getUiAutomation().getRootInActiveWindow()))
      if ("Optional".contentEquals(String.valueOf(node.getHintText()))) {
        freshAmount =
            node.isShowingHintText() || node.getText() == null || node.getText().length() == 0;
        break;
      }
    check(freshAmount, "Next feeding amount starts blank");
    capture("11-remembered-choices");
    click("Cancel");
    pause();
    getUiAutomation()
        .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    pause();
    check(
        !contains("Choose your activities"),
        "System Back returns from grid without closing the app");
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
      check(contains("Note · queued"), "Pending records remain reviewable from Settings");
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
          contains("start pending") && contains("Stop & save"),
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
          contains("Shared timer") && contains("Stop & save"),
          "Confirmed shared timer keeps one-tap configured stop");
      capture("15-shared-timer");
      click("Stop & save");
      pause();
      check(
          contains("Finish pending") && !contains("Stop & save"),
          "Offline shared stop shows pending state and blocks repeat taps");
      capture("16-shared-finish-pending");
      new SyncEngine(app.store, api).sync();
      new SyncEngine(app.store, api).sync();
      check(
          server.finishes == 2 && app.store.pending().isEmpty(),
          "Both offline sessions finish exactly once after reconnect");
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
    ActivityMonitor scannerMonitor = addMonitor("com.babybuddypocket.app.QrScanActivity", null, false);
    click("Scan Baby Buddy QR code");
    waitForMonitorWithTimeout(scannerMonitor, 5000);
    removeMonitor(scannerMonitor);
    if (scanMode.equals("denied")) {
      boolean denied = false;
      for (int i = 0; i < 30 && !denied; i++) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root != null)
          for (AccessibilityNodeInfo node : nodes(root)) {
            String label =
                String.valueOf(node.getText()).replace('\u2019', '\'').toLowerCase(Locale.ROOT);
            if (label.equals("don't allow"))
              denied = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
          }
        if (!denied) SystemClock.sleep(200);
      }
      check(denied, "Camera permission dialog can be declined");
      pause();
      check(contains("Camera access is off"), "Permission refusal has a usable fallback");
      click("Enter details manually");
      pause();
      check(contains("https://manual.example"), "Cancel preserves manual URL");
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

  private boolean contains(String text) {
    AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
    for (int i = 0; root == null && i < 25; i++) {
      SystemClock.sleep(200);
      root = getUiAutomation().getRootInActiveWindow();
    }
    if (root == null) return false;
    for (AccessibilityNodeInfo node : nodes(root))
      if (String.valueOf(node.getText()).contains(text)
          || String.valueOf(node.getHintText()).contains(text)
          || String.valueOf(node.getContentDescription()).contains(text)) return true;
    return false;
  }

  private boolean checked(String text) {
    for (AccessibilityNodeInfo node :
        getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text))
      if (node.isCheckable() && node.isChecked()) return true;
    return false;
  }

  private android.graphics.Rect bounds(String label) {
    for (AccessibilityNodeInfo node : nodes(getUiAutomation().getRootInActiveWindow()))
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
    for (AccessibilityNodeInfo node : nodes(getUiAutomation().getRootInActiveWindow()))
      if (hint.contentEquals(String.valueOf(node.getHintText()))) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return;
      }
    throw new AssertionError("No editable field: " + hint);
  }

  private void click(String text) {
    AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
    for (int i = 0; root == null && i < 25; i++) {
      SystemClock.sleep(200);
      root = getUiAutomation().getRootInActiveWindow();
    }
    if (root == null) throw new AssertionError("No active window");
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
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        node = node.getParent();
      }
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
