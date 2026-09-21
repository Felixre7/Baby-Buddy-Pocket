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

    default void removed(long id, long timerId) throws Exception {
      throw new UnsupportedOperationException();
    }
  }

  private final Store store;
  private final ApiClient api;

  public SyncEngine(Store store, ApiClient api) {
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
                "Activity is saved, but the timer was restarted or changed. Review it on the"
                    + " server; it has not been removed.");
            continue;
          }
          api.deleteTimer(item.getLong("cleanup_timer"));
          store.removed(id, item.getLong("cleanup_timer"));
        } catch (ApiClient.HttpFailure e) {
          if (e.status >= 400 && e.status < 500 && e.status != 408)
            store.state(
                id, "rejected", "Activity is saved; timer cleanup failed. " + e.getMessage());
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
              || !Records.time(timer).equals(Records.time(payload))) {
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
      // Persist before sending: a crash at any point now requires review rather than a duplicate
      // POST.
      store.state(
          id,
          "review",
          "Delivery is uncertain. Refresh the timeline and check your server before retrying.");
      try {
        JSONObject record = api.object("POST", endpoint + "/", item.getJSONObject("payload"));
        if (!record.has("id"))
          throw new IllegalStateException("The server did not return a record ID.");
        store.accepted(id, endpoint, record);
      } catch (ApiClient.HttpFailure e) {
        boolean rejected = e.status >= 400 && e.status < 500 && e.status != 408;
        store.state(id, rejected ? "rejected" : "review", e.getMessage());
        if (e.status == 401 || e.status == 403) throw e;
      } catch (Exception e) {
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

  public static String friendly(Exception e) {
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
