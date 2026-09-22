package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class SyncEngineTest {
  static class Store implements SyncEngine.Store {
    JSONObject snapshot = new JSONObject();
    List<JSONObject> pending = new ArrayList<>();
    boolean replaced;
    boolean failAccepted;

    public JSONObject snapshot() {
      return snapshot;
    }

    public void replace(JSONObject value) {
      snapshot = value;
      replaced = true;
    }

    public List<JSONObject> pending() {
      return new ArrayList<>(pending);
    }

    public void state(long id, String state, String message) {
      for (JSONObject row : pending)
        if (row.optLong("local_id") == id) {
          Records.put(row, "state", state);
          Records.put(row, "message", message);
        }
    }

    public void accepted(long id, String endpoint, JSONObject record) throws Exception {
      if (failAccepted) throw new IOException("Disk full");
      pending.removeIf(p -> p.optLong("local_id") == id);
    }

    Store queue() throws Exception {
      pending.add(
          new JSONObject()
              .put("local_id", 1)
              .put("endpoint", "notes")
              .put("state", "queued")
              .put("payload", new JSONObject().put("child", 1).put("note", "Hello")));
      return this;
    }
  }

  private static String read(String method, String path) {
    if (path.equals("/api/"))
      return "{\"children\":\"https://baby.example/api/children/\",\"notes\":\"https://baby.example/api/notes/\"}";
    if (method.equals("OPTIONS"))
      return "{\"actions\":{\"POST\":{\"note\":{\"type\":\"string\",\"required\":true}}}}";
    return "{\"next\":null,\"results\":[{\"id\":1,\"child\":1}]}";
  }

  @Test
  public void successfulRefreshReplacesSnapshotAndCachesSchema() throws Exception {
    Store store = new Store();
    new SyncEngine(
            store,
            new ApiClient("https://baby.example", "test", (m, u, t, b) -> read(m, u.getPath())))
        .sync();
    assertTrue(store.replaced);
    assertTrue(store.snapshot.getJSONObject("_schemas").has("notes"));
    assertFalse(store.snapshot.has("medications"));
  }

  @Test
  public void failedCollectionDoesNotReplaceOldCache() throws Exception {
    Store store = new Store();
    store.snapshot.put("sentinel", true);
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (u.getPath().equals("/api/notes/")) throw new IOException("Offline");
                  return read(m, u.getPath());
                }));
    assertThrows(IOException.class, engine::sync);
    assertFalse(store.replaced);
    assertTrue(store.snapshot.getBoolean("sentinel"));
  }

  @Test
  public void reachabilityFailureLeavesQueuedWriteUntouched() throws Exception {
    Store store = new Store().queue();
    int[] posts = {0};
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) posts[0]++;
                  throw new IOException("Offline");
                }));
    assertThrows(IOException.class, engine::sync);
    assertEquals(0, posts[0]);
    assertEquals("queued", store.pending.get(0).getString("state"));
  }

  @Test
  public void marksUncertaintyBeforePostAndAcceptsOnce() throws Exception {
    Store store = new Store().queue();
    int[] posts = {0};
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) {
                    posts[0]++;
                    assertEquals("review", store.pending.get(0).getString("state"));
                    return "{\"id\":99,\"child\":1,\"note\":\"Hello\"}";
                  }
                  return read(m, u.getPath());
                }));
    engine.sync();
    engine.sync();
    assertEquals(1, posts[0]);
    assertTrue(store.pending.isEmpty());
  }

  @Test
  public void ambiguousPostNeverAutomaticallyRetries() throws Exception {
    Store store = new Store().queue();
    int[] posts = {0};
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) {
                    posts[0]++;
                    throw new java.net.SocketTimeoutException();
                  }
                  return read(m, u.getPath());
                }));
    assertThrows(java.net.SocketTimeoutException.class, engine::sync);
    engine.sync();
    assertEquals(1, posts[0]);
    assertEquals("review", store.pending.get(0).getString("state"));
    assertTrue(store.replaced);
  }

  @Test
  public void rejectedPostStaysVisibleAndDoesNotRetry() throws Exception {
    Store store = new Store().queue();
    int[] posts = {0};
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) {
                    posts[0]++;
                    throw new ApiClient.HttpFailure(400, "note is required");
                  }
                  return read(m, u.getPath());
                }));
    engine.sync();
    engine.sync();
    assertEquals(1, posts[0]);
    assertEquals("rejected", store.pending.get(0).getString("state"));
    assertEquals("note is required", store.pending.get(0).getString("message"));
  }

  @Test
  public void serverErrorRemainsUncertain() throws Exception {
    Store store = new Store().queue();
    new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) throw new ApiClient.HttpFailure(500, "Server error");
                  return read(m, u.getPath());
                }))
        .sync();
    assertEquals("review", store.pending.get(0).getString("state"));
  }

  @Test
  public void storageFailureAfterSuccessDoesNotRetry() throws Exception {
    Store store = new Store().queue();
    store.failAccepted = true;
    int[] posts = {0};
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("POST")) {
                    posts[0]++;
                    return "{\"id\":99}";
                  }
                  return read(m, u.getPath());
                }));
    assertThrows(IOException.class, engine::sync);
    engine.sync();
    assertEquals(1, posts[0]);
    assertEquals("review", store.pending.get(0).getString("state"));
  }

  @Test
  public void readOnlyOptionsAreSupported() throws Exception {
    Store store = new Store();
    new SyncEngine(
            store,
            new ApiClient(
                "https://baby.example",
                "test",
                (m, u, t, b) -> {
                  if (m.equals("OPTIONS")) return "{\"actions\":{}}";
                  return read(m, u.getPath());
                }))
        .sync();
    assertTrue(store.snapshot.has("notes"));
    assertEquals(0, store.snapshot.getJSONObject("_schemas").length());
  }

  @Test
  public void futureTimerStartUsesObservedServerClockBeforePost() throws Exception {
    Store store = new Store();
    java.time.Instant now = java.time.Instant.parse("2026-01-01T12:00:00Z");
    store.pending.add(
        new JSONObject()
            .put("local_id", 1)
            .put("endpoint", "timers")
            .put("state", "queued")
            .put(
                "payload",
                new JSONObject()
                    .put("child", 1)
                    .put("name", "Sleep")
                    .put("start", now.plusSeconds(30).toString())));
    ApiClient[] client = new ApiClient[1];
    int[] posts = {0};
    client[0] =
        new ApiClient(
            "https://example.invalid",
            "synthetic",
            (method, uri, token, body) -> {
              client[0].observeServerDate(now.toEpochMilli());
              if (method.equals("POST")) {
                JSONObject payload = new JSONObject(body);
                if (java.time.Instant.parse(payload.getString("start")).isAfter(now))
                  throw new ApiClient.HttpFailure(
                      400,
                      "Server returned 400. {\"start\":[\"Date/time can not be in the future.\"]}");
                posts[0]++;
                return payload.put("id", 9).toString();
              }
              if (uri.getPath().equals("/api/")) return "{}";
              return "[]";
            });
    new SyncEngine(store, client[0]).sync();
    assertEquals(1, posts[0]);
    assertTrue(store.pending.isEmpty());
  }
}
