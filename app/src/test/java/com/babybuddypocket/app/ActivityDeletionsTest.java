package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ActivityDeletionsTest {
  private JSONObject record() throws Exception {
    return new JSONObject()
        .put("id", 31)
        .put("child", 1)
        .put("note", "Synthetic")
        .put("time", "2026-01-01T01:00:00Z");
  }

  private static class Store extends SyncEngineTest.Store {
    int removals;
    boolean failRemoval, refuseClaim;

    @Override
    public boolean claim(long id) {
      if (refuseClaim) return false;
      state(id, "review", "In flight");
      return true;
    }

    @Override
    public void removedActivity(long id, String endpoint, long recordId) throws Exception {
      assertEquals("notes", endpoint);
      assertEquals(31, recordId);
      if (failRemoval) throw new IOException("Disk full");
      removals++;
      pending.removeIf(p -> p.optLong("local_id") == id);
    }
  }

  private Store queue() throws Exception {
    Store store = new Store();
    store.pending.add(
        new JSONObject()
            .put("local_id", 7)
            .put("endpoint", "notes")
            .put("method", "DELETE")
            .put("state", "queued")
            .put("original", record())
            .put("payload", record()));
    return store;
  }

  private class Server implements ApiClient.Transport {
    JSONObject current = record();
    int deletes, failure;
    boolean loseReply, offlineRead;

    Server() throws Exception {}

    public String request(String method, java.net.URI uri, String token, String body)
        throws Exception {
      if (uri.getPath().equals("/api/notes/31/")) {
        if (method.equals("GET") && offlineRead) throw new IOException("Offline");
        if (current == null) throw new ApiClient.HttpFailure(404, "Server returned 404.");
        if (method.equals("GET")) return current.toString();
        assertEquals("DELETE", method);
        assertNull(body);
        deletes++;
        if (failure != 0) throw new ApiClient.HttpFailure(failure, "Server returned " + failure);
        current = null;
        if (loseReply) throw new IOException("Lost reply");
        return "";
      }
      assertNotEquals("POST", method);
      assertNotEquals("PATCH", method);
      return uri.getPath().equals("/api/") ? "{}" : "[]";
    }

    ApiClient api() {
      return new ApiClient("https://deletion.invalid", "synthetic", this);
    }
  }

  @Test
  public void deletesOriginalIdAndHandlesAlreadyRemovedRecord() throws Exception {
    for (boolean missing : new boolean[] {false, true}) {
      Store store = queue();
      Server server = new Server();
      if (missing) server.current = null;
      new SyncEngine(store, server.api()).sync();
      assertEquals(missing ? 0 : 1, server.deletes);
      assertEquals(1, store.removals);
      assertTrue(store.pending.isEmpty());
    }
  }

  @Test
  public void caregiverChangesAndChildChangesRequireReview() throws Exception {
    for (String field : new String[] {"child", "note", "new_field"}) {
      Store store = queue();
      Server server = new Server();
      server.current.put(field, field.equals("child") ? 2 : "Changed");
      new SyncEngine(store, server.api()).sync();
      assertEquals(0, server.deletes);
      assertEquals("rejected", store.pending.get(0).getString("state"));
      assertEquals(ActivityDeletions.CONFLICT, SyncFeedback.pending(store.pending.get(0)));
      assertFalse(SyncFeedback.canEdit(store.pending.get(0)));
    }
  }

  @Test
  public void offlinePreflightKeepsIntentQueued() throws Exception {
    Store store = queue();
    Server server = new Server();
    server.offlineRead = true;
    assertThrows(IOException.class, () -> new SyncEngine(store, server.api()).sync());
    assertEquals("queued", store.pending.get(0).getString("state"));
    assertEquals(0, server.deletes);
  }

  @Test
  public void lostReplyDoesNotRetryWithoutExplicitReview() throws Exception {
    Store store = queue();
    Server server = new Server();
    server.loseReply = true;
    SyncEngine engine = new SyncEngine(store, server.api());
    assertThrows(IOException.class, engine::sync);
    assertEquals("review", store.pending.get(0).getString("state"));
    assertTrue(SyncFeedback.pending(store.pending.get(0)).contains("deleted"));
    engine.sync();
    assertEquals(1, server.deletes);
    assertEquals(1, store.pending.size());
    store.state(7, "queued", "Explicit retry");
    engine.sync();
    assertTrue(store.pending.isEmpty());
    assertEquals(1, server.deletes);
  }

  @Test
  public void permissionAndServerFailuresPreserveRequest() throws Exception {
    for (int code : new int[] {403, 405, 500, 408}) {
      Store store = queue();
      Server server = new Server();
      server.failure = code;
      SyncEngine engine = new SyncEngine(store, server.api());
      try {
        engine.sync();
      } catch (ApiClient.HttpFailure expected) {
        assertEquals(403, code);
      }
      assertEquals(
          code == 403 || code == 405 ? "rejected" : "review",
          store.pending.get(0).getString("state"));
      engine.sync();
      assertEquals(1, server.deletes);
      assertNotNull(server.current);
    }
  }

  @Test
  public void failedLocalCommitRetainsUncertainDeletion() throws Exception {
    Store store = queue();
    store.failRemoval = true;
    Server server = new Server();
    assertThrows(IOException.class, () -> new SyncEngine(store, server.api()).sync());
    assertEquals("review", store.pending.get(0).getString("state"));
    assertNull(server.current);
  }

  @Test
  public void cancelledClaimNeverSendsDelete() throws Exception {
    Store store = queue();
    store.refuseClaim = true;
    Server server = new Server();
    new SyncEngine(store, server.api()).sync();
    assertEquals(0, server.deletes);
  }

  @Test
  public void pendingDeletionStaysOnCardWithoutDuplicatingHistory() throws Exception {
    Store store = queue();
    JSONObject data = new JSONObject().put("notes", new JSONArray().put(record()));
    List<JSONObject> rows = ActivityEdits.rows(data, 1, store.pending);
    assertEquals(1, rows.size());
    assertTrue(ActivityDeletions.isDeletion(rows.get(0).getJSONObject("_pending")));
    assertEquals("Deletion pending", SyncFeedback.label(store.pending.get(0)));
    assertTrue(ActivityEdits.rows(data, 2, store.pending).isEmpty());
    assertEquals(1, data.getJSONArray("notes").length());
  }

  @Test
  public void comparesEquivalentTimesAndObjectKeyOrderWithoutUiMetadata() throws Exception {
    JSONObject a =
        record()
            .put("_type", "notes")
            .put("tags", new JSONArray().put(new JSONObject().put("name", "x").put("id", 2)));
    JSONObject b =
        record()
            .put("time", "2026-01-01T01:00:00+00:00")
            .put("tags", new JSONArray().put(new JSONObject().put("id", 2).put("name", "x")));
    assertFalse(ActivityDeletions.original(a).has("_type"));
    assertTrue(ActivityDeletions.matches(a, b));
    b.getJSONArray("tags").getJSONObject(0).put("name", "changed");
    assertFalse(ActivityDeletions.matches(a, b));
  }
}
