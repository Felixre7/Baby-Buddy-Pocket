package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ActivityEditsTest {
  private JSONObject original() throws Exception {
    return new JSONObject()
        .put("id", 31)
        .put("child", 1)
        .put("note", "Original")
        .put("time", "2026-01-01T01:00:00+00:00")
        .put("tags", new JSONArray().put("night"));
  }

  private SyncEngineTest.Store queue(JSONObject original, JSONObject desired) throws Exception {
    SyncEngineTest.Store store = new SyncEngineTest.Store();
    store.pending.add(
        new JSONObject()
            .put("local_id", 7)
            .put("endpoint", "notes")
            .put("method", "PATCH")
            .put("state", "queued")
            .put("original", original)
            .put("payload", desired));
    return store;
  }

  private ApiClient api(JSONObject current, List<JSONObject> writes, boolean loseReply) {
    return new ApiClient(
        "https://edit.invalid",
        "synthetic",
        (m, u, t, b) -> {
          if (u.getPath().equals("/api/notes/31/")) {
            if (current == null) throw new ApiClient.HttpFailure(404, "Server returned 404.");
            if (m.equals("GET")) return current.toString();
            assertEquals("PATCH", m);
            JSONObject patch = new JSONObject(b);
            writes.add(patch);
            patch.keys().forEachRemaining(key -> Records.put(current, key, patch.opt(key)));
            if (loseReply) throw new IOException("Lost edit response");
            return current.toString();
          }
          if (u.getPath().equals("/api/")) return "{}";
          return "[]";
        });
  }

  @Test
  public void sendsOnlyChangedFieldsAndPreservesOtherCaregiverFields() throws Exception {
    JSONObject original = original(),
        desired = Records.copy(original).put("note", "Edited").put("time", "2026-01-01T01:00:00Z");
    JSONObject current = Records.copy(original).put("tags", new JSONArray().put("someone-else"));
    List<JSONObject> writes = new ArrayList<>();
    SyncEngineTest.Store store = queue(original, desired);
    new SyncEngine(store, api(current, writes, false)).sync();
    assertEquals(1, writes.size());
    assertEquals(1, writes.get(0).length());
    assertEquals("Edited", current.getString("note"));
    assertEquals("someone-else", current.getJSONArray("tags").getString(0));
    assertTrue(store.pending.isEmpty());
  }

  @Test
  public void conflictingOrRemovedRecordsAreNeverOverwrittenOrRecreated() throws Exception {
    for (boolean removed : new boolean[] {false, true}) {
      JSONObject original = original(), desired = Records.copy(original).put("note", "My edit");
      SyncEngineTest.Store store = queue(original, desired);
      List<JSONObject> writes = new ArrayList<>();
      new SyncEngine(
              store,
              api(removed ? null : Records.copy(original).put("note", "Other edit"), writes, false))
          .sync();
      assertTrue(writes.isEmpty());
      assertEquals("rejected", store.pending.get(0).getString("state"));
      assertEquals(
          removed ? ActivityEdits.REMOVED : ActivityEdits.CONFLICT,
          SyncFeedback.pending(store.pending.get(0)));
      assertFalse(SyncFeedback.canEdit(store.pending.get(0)));
    }
  }

  @Test
  public void lostReplyRequiresReviewAndExplicitRetryRecognizesAppliedEdit() throws Exception {
    JSONObject original = original(), current = Records.copy(original);
    SyncEngineTest.Store store = queue(original, Records.copy(original).put("note", "Edited"));
    List<JSONObject> writes = new ArrayList<>();
    SyncEngine engine = new SyncEngine(store, api(current, writes, true));
    assertThrows(IOException.class, engine::sync);
    assertEquals("review", store.pending.get(0).getString("state"));
    engine.sync();
    assertEquals(1, writes.size());
    store.state(7, "queued", "User requested review/retry");
    engine.sync();
    assertEquals(1, writes.size());
    assertTrue(store.pending.isEmpty());
  }

  @Test
  public void failedPreflightKeepsOfflineEditQueued() throws Exception {
    SyncEngineTest.Store store = queue(original(), Records.copy(original()).put("note", "Edited"));
    SyncEngine engine =
        new SyncEngine(
            store,
            new ApiClient(
                "https://edit.invalid",
                "synthetic",
                (m, u, t, b) -> {
                  if (u.getPath().equals("/api/notes/31/")) throw new IOException("Offline");
                  if (u.getPath().equals("/api/")) return "{}";
                  return "[]";
                }));
    assertThrows(IOException.class, engine::sync);
    assertEquals("queued", store.pending.get(0).getString("state"));
  }

  @Test
  public void displayOverlaysEditOnceAndExcludesTimerOperations() throws Exception {
    JSONObject base = original();
    JSONObject data = new JSONObject().put("notes", new JSONArray().put(base));
    SyncEngineTest.Store store = queue(base, Records.copy(base).put("note", "Local edit"));
    store.pending.add(
        new JSONObject()
            .put("endpoint", "timers")
            .put("payload", new JSONObject().put("child", 1)));
    List<JSONObject> rows = ActivityEdits.rows(data, 1, store.pending);
    assertEquals(1, rows.size());
    assertEquals("Local edit", rows.get(0).getString("note"));
    assertTrue(rows.get(0).has("_pending"));
    assertEquals("Original", data.getJSONArray("notes").getJSONObject(0).getString("note"));
    assertTrue(ActivityEdits.rows(data, 2, store.pending).isEmpty());
  }

  @Test
  public void editCanExplicitlyClearTextTagsAndNullableAmount() throws Exception {
    JSONObject schema =
        new JSONObject()
            .put("note", new JSONObject().put("type", "string").put("allow_blank", true))
            .put("tags", new JSONObject().put("type", "list"))
            .put("amount", new JSONObject().put("type", "float").put("allow_null", true));
    JSONObject original = original().put("amount", 20);
    Map<String, String> values = new HashMap<>();
    for (String key : new String[] {"note", "tags", "amount"}) values.put(key, "");
    JSONObject desired = FormValues.parseEdit(schema, values, 1, original);
    assertEquals("", desired.getString("note"));
    assertEquals(0, desired.getJSONArray("tags").length());
    assertTrue(desired.has("amount") && desired.isNull("amount"));
    assertEquals(3, ActivityEdits.changes(original, desired).length());
  }
}
