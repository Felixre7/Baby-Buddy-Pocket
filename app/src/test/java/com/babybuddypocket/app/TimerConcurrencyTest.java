package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import org.json.*;
import org.junit.Test;

public class TimerConcurrencyTest {
  static class Server implements ApiClient.Transport {
    JSONArray timers = new JSONArray();
    int starts;
    boolean failTimerRead;

    public String request(String method, URI uri, String token, String body) throws Exception {
      String path = uri.getPath();
      if (method.equals("OPTIONS")) return "{}";
      if (method.equals("POST")) {
        JSONObject created = new JSONObject(body).put("id", ++starts);
        timers.put(created);
        return created.toString();
      }
      if (method.equals("DELETE")) {
        timers = new JSONArray();
        return "";
      }
      if (path.equals("/api/")) return "{\"timers\":\"/api/timers/\"}";
      if (path.startsWith("/api/timers/")) {
        if (failTimerRead) throw new IOException("Offline");
        if (!path.equals("/api/timers/")) return timers.getJSONObject(0).toString();
        return timers.toString();
      }
      return "[]";
    }
  }

  private SyncEngineTest.Store queued(long child, String name) throws Exception {
    SyncEngineTest.Store store = new SyncEngineTest.Store();
    store.pending.add(
        new JSONObject()
            .put("local_id", 1)
            .put("endpoint", "timers")
            .put("state", "queued")
            .put(
                "payload",
                new JSONObject()
                    .put("child", child)
                    .put("name", name)
                    .put("start", "2026-01-01T00:00:00Z")));
    return store;
  }

  private void sync(SyncEngineTest.Store store, Server server) throws Exception {
    new SyncEngine(store, new ApiClient("https://example.invalid", "synthetic", server)).sync();
  }

  @Test
  public void secondCaregiverCannotPublishAnotherActivityForSameChild() throws Exception {
    Server server = new Server();
    SyncEngineTest.Store first = queued(1, "Feeding"), second = queued(1, "Tummy time");
    sync(first, server);
    sync(second, server);
    assertEquals(1, server.starts);
    assertTrue(first.pending.isEmpty());
    assertEquals("rejected", second.pending.get(0).getString("state"));
    assertEquals(TimerPolicy.CONFLICT, second.pending.get(0).getString("message"));
    assertEquals(1, second.snapshot.getJSONArray("timers").length());
  }

  @Test
  public void differentChildrenMayEachHaveATimer() throws Exception {
    Server server = new Server();
    sync(queued(1, "Sleep"), server);
    SyncEngineTest.Store second = queued(2, "Sleep");
    sync(second, server);
    assertEquals(2, server.starts);
    assertTrue(second.pending.isEmpty());
  }

  @Test
  public void unassignedServerTimerIsNotSilentlyIgnored() throws Exception {
    Server server = new Server();
    server.timers.put(new JSONObject().put("id", 99).put("child", JSONObject.NULL));
    SyncEngineTest.Store store = queued(1, "Sleep");
    sync(store, server);
    assertEquals(0, server.starts);
    assertEquals("rejected", store.pending.get(0).getString("state"));
  }

  @Test
  public void failedFreshCheckLeavesLocalStartQueuedWithoutPosting() throws Exception {
    Server server = new Server();
    server.failTimerRead = true;
    SyncEngineTest.Store store = queued(1, "Sleep");
    assertThrows(IOException.class, () -> sync(store, server));
    assertEquals(0, server.starts);
    assertEquals("queued", store.pending.get(0).getString("state"));
  }

  @Test
  public void previousCleanupCompletesBeforeNextStart() throws Exception {
    Server server = new Server();
    JSONObject old =
        new JSONObject().put("id", 99).put("child", 1).put("start", "2026-01-01T00:00:00Z");
    server.timers.put(old);
    SyncEngineTest.Store store =
        new SyncEngineTest.Store() {
          public void removed(long id, long timerId) {
            pending.removeIf(row -> row.optLong("local_id") == id);
          }
        };
    store.pending.addAll(queued(1, "Sleep").pending);
    store.pending.add(
        new JSONObject()
            .put("local_id", 2)
            .put("endpoint", "timers")
            .put("method", "DELETE")
            .put("cleanup_timer", 99)
            .put("state", "queued")
            .put("payload", old));
    sync(store, server);
    assertEquals(0, server.starts);
    assertEquals("queued", store.pending.get(0).getString("state"));
    sync(store, server);
    assertEquals(1, server.starts);
    assertTrue(store.pending.isEmpty());
  }
}
