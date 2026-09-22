package com.babybuddypocket.app;

import java.util.*;
import org.json.*;

/** A single foreground worker owns sync. GETs are repeatable; ambiguous POSTs are not. */
public final class SyncEngine {
  public interface Store {
    JSONObject snapshot();

    void replace(JSONObject snapshot) throws Exception;

    List<JSONObject> pending();

    void state(long id, String state, String message);

    void accepted(long id, String endpoint, JSONObject record) throws Exception;

    default boolean claim(long id) {
      state(id, "review", "Delivery is uncertain. Check your server before retrying.");
      return true;
    }

    default void removed(long id, long timerId) throws Exception {
      throw new UnsupportedOperationException();
    }
  }

  private final Store store;
  private final ApiClient api;
  private final DiagnosticLog diagnostics;

  public SyncEngine(Store store, ApiClient api) {
    this(store, api, null);
  }

  SyncEngine(Store store, ApiClient api, DiagnosticLog diagnostics) {
    this.diagnostics = diagnostics;
    this.store = store;
    this.api = api;
  }

  public void sync() throws Exception {
    // Authenticate / establish reachability before attempting any queued write.
    JSONArray children = api.list("children");
    JSONObject root = api.object("GET", "", null);
    for (JSONObject item : store.pending()) {
      if (!"queued".equals(item.optString("state"))) continue;
      long id = item.getLong("local_id");
      String endpoint = item.getString("endpoint");
      if (item.optString("method").equals("DELETE")) {
        // Deleting the same confirmed timer is safe to retry, including after a lost reply.
        try {
          JSONObject timer;
          try {
            timer = api.object("GET", "timers/" + item.getLong("cleanup_timer") + "/", null);
          } catch (ApiClient.HttpFailure e) {
            if (e.status != 404) throw e;
            store.removed(id, item.getLong("cleanup_timer"));
            continue;
          }
          JSONObject expected = item.getJSONObject("payload");
          if (!Records.time(timer).equals(Records.time(expected))
              || (!timer.isNull("child") && timer.getLong("child") != expected.getLong("child"))) {
            store.state(
                id,
                "rejected",
                "The timer was restarted or changed. Review it on the"
                    + " server; it has not been removed.");
            continue;
          }
          api.deleteTimer(item.getLong("cleanup_timer"));
          store.removed(id, item.getLong("cleanup_timer"));
        } catch (ApiClient.HttpFailure e) {
          if (e.status >= 400 && e.status < 500 && e.status != 408)
            store.state(id, "rejected", "Timer removal failed. " + e.getMessage());
          throw e;
        }
        continue;
      }
      if (item.has("cleanup_timer")) {
        try {
          JSONObject timer =
              api.object("GET", "timers/" + item.getLong("cleanup_timer") + "/", null);
          JSONObject payload = item.getJSONObject("payload");
          if ((!timer.isNull("child") && timer.getLong("child") != payload.getLong("child"))
              || !Records.time(timer)
                  .equals(
                      Records.instant(
                          item.optString("cleanup_start", payload.getString("start"))))) {
            store.state(
                id,
                "rejected",
                "The shared timer changed. Check the server before saving this activity.");
            continue;
          }
        } catch (ApiClient.HttpFailure e) {
          if (e.status == 404) {
            store.state(
                id,
                "rejected",
                "The shared timer was already finished or removed. Check the server before"
                    + " retrying.");
            continue;
          }
          throw e;
        }
      }
      JSONObject payload = item.getJSONObject("payload");
      if (endpoint.equals("timers")) {
        // Read immediately before creating, not just from the last full snapshot.
        // A failed read leaves the start queued and usable offline, without sending it.
        JSONArray running = api.list("timers");
        boolean waitForCleanup = false, conflict = false;
        for (int i = 0; i < running.length(); i++) {
          JSONObject timer = running.getJSONObject(i);
          if (!TimerPolicy.applies(timer, payload.getLong("child"))) continue;
          boolean ending = false;
          for (JSONObject row : store.pending())
            if (row.optLong("cleanup_timer", -1) == timer.getLong("id")
                && row.optString("state").equals("queued")) ending = true;
          if (ending) waitForCleanup = true;
          else conflict = true;
        }
        if (conflict) {
          // Claim first: the user may have stopped/cancelled during the GET.
          if (store.claim(id)) {
            store.state(id, "rejected", TimerPolicy.CONFLICT);
            log(DiagnosticLog.Event.TIMER_CONFLICT, null);
          }
          continue;
        }
        // A previous local Stop/Cancel owns its cleanup. Send that before the next start.
        if (waitForCleanup) continue;
      }
      String method = item.optString("method", "POST");
      String path = endpoint + "/";
      if (method.equals("PATCH")) {
        JSONObject original = item.getJSONObject("original");
        path += original.getLong("id") + "/";
        JSONObject changes = ActivityEdits.changes(original, payload);
        try {
          JSONObject current = api.object("GET", path, null);
          if (ActivityEdits.matches(current, payload, changes)) {
            if (store.claim(id)) store.accepted(id, endpoint, current);
            continue;
          }
          if (!ActivityEdits.matches(current, original, changes)) {
            if (store.claim(id)) store.state(id, "rejected", ActivityEdits.CONFLICT);
            continue;
          }
        } catch (ApiClient.HttpFailure e) {
          if (e.status >= 400 && e.status < 500 && e.status != 408) {
            if (store.claim(id))
              store.state(id, "rejected", e.status == 404 ? ActivityEdits.REMOVED : e.getMessage());
            log(DiagnosticLog.Event.WRITE_REJECTED, e);
            if (e.status == 401 || e.status == 403) throw e;
            continue;
          }
          throw e;
        }
        // Only changed fields: unrelated changes made by another caregiver are preserved.
        payload = changes;
      } else if (endpoint.equals("timers") || Records.timed(endpoint)) {
        JSONObject adjusted = api.timerTimes(payload, item.has("cleanup_timer"));
        if (!adjusted.optString("start").equals(payload.optString("start"))
            || !adjusted.optString("end").equals(payload.optString("end")))
          log(DiagnosticLog.Event.TIMER_TIME_ADJUSTED, null);
        payload = adjusted;
      }
      // Persist before sending: a crash at any point now requires review rather than a duplicate
      // write.
      if (!store.claim(id)) continue;
      try {
        JSONObject record = api.object(method, path, payload);
        if (!record.has("id"))
          throw new IllegalStateException("The server did not return a record ID.");
        if (method.equals("PATCH")
            && record.getLong("id") != item.getJSONObject("original").getLong("id"))
          throw new IllegalStateException("The server returned a different activity.");
        store.accepted(id, endpoint, record);
        log(DiagnosticLog.Event.WRITE_ACCEPTED, null);
      } catch (ApiClient.HttpFailure e) {
        boolean rejected = e.status >= 400 && e.status < 500 && e.status != 408;
        store.state(id, rejected ? "rejected" : "review", e.getMessage());
        log(
            rejected
                ? (endpoint.equals("timers")
                    ? DiagnosticLog.Event.TIMER_START_REJECTED
                    : DiagnosticLog.Event.WRITE_REJECTED)
                : DiagnosticLog.Event.WRITE_UNCERTAIN,
            e);
        if (e.status == 401 || e.status == 403) throw e;
      } catch (Exception e) {
        log(DiagnosticLog.Event.WRITE_UNCERTAIN, e);
        store.state(
            id,
            "review",
            "Delivery is uncertain. Check the server before retrying. " + friendly(e));
        throw e;
      }
    }
    JSONObject fresh = new JSONObject().put("children", children);
    JSONObject schemas = new JSONObject();
    for (String endpoint : Records.ENDPOINTS) {
      // Older servers do not offer every newer collection (e.g. medications).
      boolean offered = root.has(endpoint);
      Iterator<String> keys = root.keys();
      while (keys.hasNext() && !offered)
        offered = root.optString(keys.next()).endsWith("/" + endpoint + "/");
      if (!offered) continue;
      fresh.put(endpoint, api.list(endpoint));
      try {
        JSONObject options = api.object("OPTIONS", endpoint + "/", null).optJSONObject("actions");
        if (options != null && options.optJSONObject("POST") != null)
          schemas.put(endpoint, options.getJSONObject("POST"));
      } catch (ApiClient.HttpFailure e) {
        if (e.status != 403 && e.status != 405) throw e;
      }
    }
    fresh.put("_schemas", schemas);
    fresh.put("_synced", System.currentTimeMillis());
    // Replace only after every supported collection and every page succeeded.
    store.replace(fresh);
  }

  private void log(DiagnosticLog.Event event, Exception failure) {
    if (diagnostics != null) diagnostics.record(event, failure);
  }

  public static String friendly(Exception e) {
    if (e instanceof ApiClient.HttpFailure) {
      ApiClient.HttpFailure http = (ApiClient.HttpFailure) e;
      return SyncFeedback.explain(http.status, http.getMessage());
    }
    if (e instanceof java.net.UnknownHostException)
      return "Can't find the server. Check the address and your connection.";
    if (e instanceof javax.net.ssl.SSLException)
      return "The HTTPS certificate could not be verified. Check the server certificate.";
    if (e instanceof java.net.SocketTimeoutException) return "The server took too long to respond.";
    return e.getMessage() == null
        ? "Unable to sync. Check your connection and try again."
        : e.getMessage();
  }
}
