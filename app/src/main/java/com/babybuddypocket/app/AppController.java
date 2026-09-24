package com.babybuddypocket.app;

import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Process-scoped state keeps a rotation from starting a second sync or write. */
public final class AppController {
  private static AppController instance;

  public static synchronized AppController get(Context context) {
    if (instance == null) instance = new AppController(context.getApplicationContext());
    return instance;
  }

  public final LocalStore store;
  private final DiagnosticLog diagnostics;
  private final TimerNotifications notifications;
  public final Credentials credentials;
  public final SharedPreferences preferences;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  public JSONObject data;
  public boolean busy, demo;
  public String error = "";
  JSONObject syncProblem;
  public Runnable listener;
  private final List<JSONObject> demoTimers = new ArrayList<>();

  private AppController(Context context) {
    diagnostics =
        new DiagnosticLog(new java.io.File(context.getNoBackupFilesDir(), "diagnostics.json"));
    notifications = new TimerNotifications(context);
    store = new LocalStore(context);
    store.recoverRejectedTimers(true);
    credentials = new Credentials(context);
    preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE);
    // 0.1.2's public demo is retired. Its existing demo flag now selects local sample data.
    if (context.getDatabasePath("public-demo.db").exists())
      context.deleteDatabase("public-demo.db");
    if (preferences.contains("public_demo")) preferences.edit().remove("public_demo").apply();
    demo = preferences.getBoolean("demo", false) && credentials.server().isEmpty();
    data = demo ? DemoData.create() : store.snapshot();
  }

  public boolean connected() {
    return !credentials.server().isEmpty();
  }

  ActivityOptions activities() {
    return new ActivityOptions(preferences, demo);
  }

  public Units units() {
    return new Units(
        preferences.getBoolean("ounces", false),
        preferences.getBoolean("pounds", false),
        preferences.getBoolean("inches", false),
        preferences.getBoolean("fahrenheit", false));
  }

  void refreshNotifications() {
    try {
      notifications.update(data, timers(), pending(), demo);
    } catch (RuntimeException e) {
      log(DiagnosticLog.Event.LOCAL_FAILURE, e);
    }
  }

  void dismissTimerNotification(String key) {
    try {
      notifications.dismiss(key, timers(), pending(), demo);
    } catch (RuntimeException e) {
      log(DiagnosticLog.Event.LOCAL_FAILURE, e);
    }
  }

  private void changed() {
    refreshNotifications();
    if (listener != null) listener.run();
  }

  public void connect(String server, String token) {
    if (busy || connected()) return;
    busy = true;
    error = "";
    changed();
    worker.execute(
        () -> {
          Exception failure = null;
          JSONObject downloaded = null;
          try {
            ApiClient api = new ApiClient(server, token);
            MemoryStore staging = new MemoryStore();
            new SyncEngine(staging, api, diagnostics).sync();
            downloaded = staging.snapshot();
            // This flow is only accessible after a confirmed disconnect or from demo.
            store.clear();
            store.replace(downloaded);
            credentials.save(server, token);
          } catch (Exception e) {
            failure = e;
            diagnostics.record(DiagnosticLog.Event.CONNECT_FAILED, e);
          }
          JSONObject snapshot = downloaded;
          Exception problem = failure;
          main.post(
              () -> {
                busy = false;
                if (problem == null) {
                  demo = false;
                  preferences.edit().putBoolean("demo", false).remove("child").apply();
                  data = snapshot;
                } else error = SyncEngine.friendly(problem);
                changed();
              });
        });
  }

  public void sync() {
    if (busy || demo || !connected()) return;
    List<JSONObject> before = store.pending();
    busy = true;
    error = "";
    changed();
    worker.execute(
        () -> {
          String failure = "";
          try {
            new SyncEngine(
                    store, new ApiClient(credentials.server(), credentials.token()), diagnostics)
                .sync();
          } catch (Exception e) {
            failure = SyncEngine.friendly(e);
            diagnostics.record(DiagnosticLog.Event.SYNC_FAILED, e);
          }
          JSONObject downloaded = null;
          try {
            downloaded = store.snapshot();
          } catch (Exception e) {
            failure = SyncEngine.friendly(e);
            diagnostics.record(DiagnosticLog.Event.SYNC_FAILED, e);
          }
          JSONObject snapshot = downloaded;
          String message = failure;
          main.post(
              () -> {
                if (snapshot != null) data = snapshot;
                error = message;
                busy = false;
                JSONObject problem = SyncFeedback.newProblem(before, store.pending());
                if (problem != null) syncProblem = problem;
                changed();
                // A user may have saved a new entry after this sync took its outbox snapshot.
                if (message.isEmpty()
                    && store.pending().stream()
                        .anyMatch(p -> "queued".equals(p.optString("state")))) sync();
              });
        });
  }

  public void demo() {
    if (busy || connected()) return;
    demo = true;
    error = "";
    preferences.edit().putBoolean("demo", true).apply();
    data = DemoData.create();
    changed();
  }

  public void disconnect() {
    if (busy) return;
    store.clear();
    credentials.clear();
    worker.execute(diagnostics::clear);
    preferences.edit().clear().apply();
    demoTimers.clear();
    demo = false;
    data = new JSONObject();
    error = "";
    syncProblem = null;
    changed();
  }

  public List<JSONObject> pending() {
    return demo ? Collections.emptyList() : store.pending();
  }

  public long child() {
    JSONArray children = data.optJSONArray("children");
    long selected = preferences.getLong("child", -1);
    if (children != null)
      for (int i = 0; i < children.length(); i++)
        if (children.optJSONObject(i).optLong("id") == selected) return selected;
    return children == null || children.length() == 0
        ? -1
        : children.optJSONObject(0).optLong("id");
  }

  public JSONObject childRecord() {
    JSONArray children = data.optJSONArray("children");
    if (children != null)
      for (int i = 0; i < children.length(); i++)
        if (children.optJSONObject(i).optLong("id") == child()) return children.optJSONObject(i);
    return new JSONObject();
  }

  public void selectChild(long id) {
    preferences.edit().putLong("child", id).apply();
    changed();
  }

  public JSONObject schema(String endpoint) {
    JSONObject schemas = data.optJSONObject("_schemas");
    return schemas == null ? null : schemas.optJSONObject(endpoint);
  }

  List<JSONObject> timers() {
    return demo ? new ArrayList<>(demoTimers) : store.timers();
  }

  void startTimer(String endpoint, JSONObject payload) throws Exception {
    if (!Records.timed(endpoint))
      throw new IllegalArgumentException("This activity cannot be timed.");
    List<JSONObject> pending = pending();
    for (JSONObject timer : timers())
      if (TimerPolicy.runningLocal(timer, pending)
          && timer.getJSONObject("payload").getLong("child") == payload.getLong("child"))
        throw new TimerAlreadyRunning();
    JSONArray shared = data.optJSONArray("timers");
    if (shared != null)
      for (int i = 0; i < shared.length(); i++) {
        JSONObject timer = shared.getJSONObject(i);
        if (TimerPolicy.applies(timer, payload.getLong("child"))
            && !TimerPolicy.ending(pending, timer.getLong("id"))) throw new TimerAlreadyRunning();
      }
    JSONObject started = Records.copy(payload);
    started.remove("end");
    if (demo) {
      long id = System.nanoTime();
      data.getJSONArray("timers")
          .put(
              new JSONObject()
                  .put("id", id)
                  .put("child", payload.getLong("child"))
                  .put("name", Records.title(endpoint))
                  .put("start", payload.getString("start")));
      demoTimers.add(
          new JSONObject()
              .put("id", id)
              .put("server_id", id)
              .put("endpoint", endpoint)
              .put("payload", started));
    } else {
      if (!connected()) throw new IllegalStateException("Connect a server before logging.");
      if (schema("timers") == null)
        throw new IllegalStateException(
            "Sync first and check that your account can create timers.");
      store.startTimer(endpoint, started);
      log(DiagnosticLog.Event.TIMER_STARTED, null);
    }
    changed();
    sync();
  }

  static final class TimerAlreadyRunning extends IllegalStateException {
    TimerAlreadyRunning() {
      super(
          "A timer is already running. Finish or cancel it on Today before starting another."
              + " You can still add a manual log.");
    }
  }

  JSONObject timerOptions(long serverId) {
    for (JSONObject timer : timers()) if (timer.optLong("server_id", -1) == serverId) return timer;
    return null;
  }

  void finishTimer(long id, JSONObject payload) throws Exception {
    if (demo) {
      JSONObject timer = null;
      for (JSONObject row : demoTimers) if (row.getLong("id") == id) timer = row;
      if (timer == null) throw new IllegalArgumentException("This timer has already finished.");
      add(
          timer.getString("endpoint"),
          Records.copy(payload).put("timer", timer.getLong("server_id")));
      demoTimers.remove(timer);
    } else {
      store.finishTimer(id, payload);
      log(DiagnosticLog.Event.TIMER_STOPPED, null);
    }
    changed();
    sync();
  }

  void cancelTimer(long id) throws Exception {
    if (demo) {
      JSONObject local = timerOptions(id);
      JSONArray shared = data.getJSONArray("timers"), remaining = new JSONArray();
      for (int i = 0; i < shared.length(); i++)
        if (shared.getJSONObject(i).getLong("id") != id) remaining.put(shared.getJSONObject(i));
      data.put("timers", remaining);
      if (local != null) demoTimers.remove(local);
    } else store.cancelTimer(id);
    log(DiagnosticLog.Event.TIMER_CANCELLED, null);
    changed();
    sync();
  }

  void cancelSharedTimer(long id) throws Exception {
    JSONObject local = timerOptions(id);
    if (local != null) {
      cancelTimer(local.getLong("id"));
      return;
    }
    JSONArray shared = data.optJSONArray("timers");
    if (shared != null)
      for (int i = 0; i < shared.length(); i++) {
        JSONObject timer = shared.getJSONObject(i);
        if (timer.getLong("id") != id) continue;
        store.queueTimerRemoval(
            id, Records.copy(timer).put("child", timer.optLong("child", child())));
        log(DiagnosticLog.Event.TIMER_CANCELLED, null);
        changed();
        sync();
        return;
      }
    throw new IllegalArgumentException("This timer has already finished.");
  }

  void log(DiagnosticLog.Event event, Throwable failure) {
    worker.execute(() -> diagnostics.record(event, failure));
  }

  void diagnosticReport(java.util.function.Consumer<String> callback) {
    worker.execute(
        () -> {
          StringBuilder report = new StringBuilder(diagnostics.report());
          try {
            int queued = 0, rejected = 0, review = 0;
            for (JSONObject row : store.pending()) {
              switch (row.optString("state")) {
                case "queued":
                  queued++;
                  break;
                case "rejected":
                  rejected++;
                  break;
                case "review":
                  review++;
                  break;
              }
            }
            report
                .append("\nPending: queued=")
                .append(queued)
                .append(", rejected=")
                .append(rejected)
                .append(", review=")
                .append(review)
                .append('\n');
          } catch (Exception ignored) {
            report.append("\nQueue counts unavailable.\n");
          }
          main.post(() -> callback.accept(report.toString()));
        });
  }

  void clearDiagnostics() {
    worker.execute(diagnostics::clear);
  }

  public void add(String endpoint, JSONObject payload) throws Exception {
    if (!demo && !connected()) throw new IllegalStateException("Connect a server before logging.");
    if (demo) {
      JSONObject record = Records.copy(payload).put("id", System.currentTimeMillis());
      if (payload.has("timer")) {
        JSONArray timers = data.getJSONArray("timers"), remaining = new JSONArray();
        boolean found = false;
        for (int i = 0; i < timers.length(); i++) {
          JSONObject active = timers.getJSONObject(i);
          if (active.optLong("id") == payload.getLong("timer")) {
            record
                .put("start", active.getString("start"))
                .put("end", java.time.Instant.now().toString());
            found = true;
          } else remaining.put(active);
        }
        if (!found) throw new IllegalArgumentException("This timer has already finished.");
        data.put("timers", remaining);
        record.remove("timer");
      }
      JSONArray rows = data.optJSONArray(endpoint);
      if (rows == null) rows = new JSONArray();
      rows.put(record);
      data.put(endpoint, rows);
      changed();
    } else {
      if (payload.has("timer")) {
        long timerId = payload.getLong("timer");
        JSONObject active = null;
        JSONArray shared = data.optJSONArray("timers");
        if (shared != null)
          for (int i = 0; i < shared.length(); i++)
            if (shared.getJSONObject(i).optLong("id") == timerId) active = shared.getJSONObject(i);
        if (active == null)
          throw new IllegalArgumentException(
              "Sync to check whether this timer has already finished.");
        JSONObject finished =
            Records.copy(payload)
                .put("start", active.getString("start"))
                .put("end", java.time.Instant.now().toString());
        store.enqueueTimerFinish(endpoint, finished, timerId);
      } else store.enqueue(endpoint, payload);
      changed();
      sync();
    }
  }

  void editPending(long id, JSONObject payload) throws Exception {
    if (busy)
      throw new IllegalStateException("Wait until syncing finishes before saving these changes.");
    store.editPending(id, payload);
    changed();
    sync();
  }

  void editActivity(String endpoint, JSONObject original, JSONObject payload) throws Exception {
    if (demo) {
      JSONArray rows = data.getJSONArray(endpoint);
      for (int i = 0; i < rows.length(); i++)
        if (rows.getJSONObject(i).getLong("id") == original.getLong("id")) {
          JSONObject edited = Records.copy(rows.getJSONObject(i));
          payload.keys().forEachRemaining(key -> Records.put(edited, key, payload.opt(key)));
          rows.put(i, edited);
          changed();
          return;
        }
      throw new IllegalArgumentException("This sample activity no longer exists.");
    }
    if (!connected()) throw new IllegalStateException("Connect a server before editing.");
    store.enqueueEdit(endpoint, original, payload);
    changed();
    sync();
  }

  void deleteActivity(String endpoint, JSONObject original) throws Exception {
    if (demo) {
      JSONArray rows = data.getJSONArray(endpoint), remaining = new JSONArray();
      for (int i = 0; i < rows.length(); i++)
        if (rows.getJSONObject(i).getLong("id") != original.getLong("id"))
          remaining.put(rows.getJSONObject(i));
      data.put(endpoint, remaining);
    } else {
      if (!connected()) throw new IllegalStateException("Connect a server before deleting.");
      store.enqueueDeletion(endpoint, original);
    }
    changed();
    sync();
  }

  public static class MemoryStore implements SyncEngine.Store {
    private JSONObject snapshot = new JSONObject();

    public JSONObject snapshot() {
      return snapshot;
    }

    public void replace(JSONObject value) {
      snapshot = value;
    }

    public List<JSONObject> pending() {
      return Collections.emptyList();
    }

    public void state(long id, String state, String message) {
      throw new UnsupportedOperationException();
    }

    public void accepted(long id, String endpoint, JSONObject record) {
      throw new UnsupportedOperationException();
    }
  }
}
