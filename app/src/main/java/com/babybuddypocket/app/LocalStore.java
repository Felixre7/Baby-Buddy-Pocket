package com.babybuddypocket.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

public final class LocalStore extends SQLiteOpenHelper implements SyncEngine.Store {
  public LocalStore(Context context) {
    this(context, "baby-buddy-pocket.db");
  }

  LocalStore(Context context, String name) {
    super(context, name, null, 3);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE cache (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL, payload"
            + " TEXT NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL, method TEXT NOT NULL"
            + " DEFAULT 'POST', cleanup_timer INTEGER)");
    createTimers(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 3) {
      db.execSQL("ALTER TABLE outbox ADD COLUMN method TEXT NOT NULL DEFAULT 'POST'");
      db.execSQL("ALTER TABLE outbox ADD COLUMN cleanup_timer INTEGER");
    }
    if (oldVersion < 2) createTimers(db);
    else if (oldVersion < 3) {
      db.execSQL("ALTER TABLE active_timers ADD COLUMN start_id INTEGER");
      db.execSQL("ALTER TABLE active_timers ADD COLUMN server_id INTEGER");
      db.execSQL("ALTER TABLE active_timers ADD COLUMN finish_payload TEXT");
    }
  }

  private void createTimers(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE active_timers (id INTEGER PRIMARY KEY AUTOINCREMENT, endpoint TEXT NOT NULL,"
            + " payload TEXT NOT NULL, start_id INTEGER, server_id INTEGER, finish_payload TEXT)");
  }

  synchronized void startTimer(String endpoint, JSONObject payload) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject request =
          new JSONObject()
              .put("child", payload.getLong("child"))
              .put("name", Records.title(endpoint))
              .put("start", payload.getString("start"));
      long startId = enqueue("timers", request);
      ContentValues values = new ContentValues();
      values.put("endpoint", endpoint);
      values.put("payload", payload.toString());
      values.put("start_id", startId);
      db.insertOrThrow("active_timers", null, values);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  synchronized List<JSONObject> timers() {
    List<JSONObject> rows = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,endpoint,payload,start_id,server_id,finish_payload FROM active_timers"
                    + " ORDER BY id",
                null)) {
      while (cursor.moveToNext())
        rows.add(
            new JSONObject()
                .put("id", cursor.getLong(0))
                .put("endpoint", cursor.getString(1))
                .put("payload", new JSONObject(cursor.getString(2)))
                .put("start_id", cursor.isNull(3) ? null : cursor.getLong(3))
                .put("server_id", cursor.isNull(4) ? null : cursor.getLong(4))
                .put(
                    "finish_payload",
                    cursor.isNull(5) ? null : new JSONObject(cursor.getString(5))));
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return rows;
  }

  synchronized void discardTimer(long id) {
    getWritableDatabase().delete("active_timers", "id=?", new String[] {Long.toString(id)});
  }

  synchronized void finishTimer(long id, JSONObject payload) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject timer = null;
      for (JSONObject row : timers()) if (row.getLong("id") == id) timer = row;
      if (timer == null) throw new IllegalArgumentException("This timer has already finished.");
      if (payload.getLong("child") != timer.getJSONObject("payload").getLong("child"))
        throw new IllegalArgumentException("The timer belongs to another child.");
      JSONObject finished = Records.copy(payload);
      if (!finished.has("end")) finished.put("end", java.time.Instant.now().toString());
      if (!finished.has("start"))
        finished.put("start", timer.getJSONObject("payload").getString("start"));
      FormValues.validateDuration(finished);
      if (timer.has("server_id")) {
        enqueueTimerFinish(timer.getString("endpoint"), finished, timer.getLong("server_id"));
        ContentValues consumedSetup = new ContentValues();
        consumedSetup.putNull("finish_payload");
        db.update("active_timers", consumedSetup, "id=?", new String[] {Long.toString(id)});
      } else if (timer.has("start_id")) {
        if (timer.has("finish_payload"))
          throw new IllegalArgumentException("This timer already has a pending finish.");
        boolean unsent = false;
        for (JSONObject row : pending())
          if (row.optLong("local_id") == timer.getLong("start_id"))
            unsent = row.optString("state").equals("queued");
        if (unsent) {
          // A whole session completed before its start was sent: upload just the finished log.
          enqueue(timer.getString("endpoint"), finished);
          discard(timer.getLong("start_id"));
        } else {
          ContentValues stop = new ContentValues();
          stop.put("finish_payload", finished.toString());
          db.update("active_timers", stop, "id=?", new String[] {Long.toString(id)});
        }
      } else {
        enqueue(timer.getString("endpoint"), finished);
        discardTimer(id);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public synchronized JSONObject snapshot() {
    try (Cursor cursor =
        getReadableDatabase().rawQuery("SELECT data FROM cache WHERE id=1", null)) {
      return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : new JSONObject();
    } catch (JSONException e) {
      throw new IllegalStateException("Local data could not be read", e);
    }
  }

  @Override
  public synchronized void replace(JSONObject data) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("id", 1);
      values.put("data", data.toString());
      if (db.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1)
        throw new IllegalStateException("Unable to save downloaded data");
      JSONArray shared = data.optJSONArray("timers");
      if (shared != null)
        for (JSONObject local : timers()) {
          if (!local.has("server_id")) continue;
          boolean found = false;
          for (int i = 0; i < shared.length(); i++) {
            JSONObject remote = shared.optJSONObject(i);
            if (remote != null
                && remote.optLong("id") == local.optLong("server_id")
                && remote.optLong("child", -1) == local.optJSONObject("payload").optLong("child"))
              found = true;
          }
          if (!found) discardTimer(local.optLong("id"));
        }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized long enqueue(String endpoint, JSONObject data) {
    if (data.has("timer"))
      for (JSONObject row : pending())
        if (row.optJSONObject("payload").optLong("timer", -1) == data.optLong("timer")
            || row.optLong("cleanup_timer", -1) == data.optLong("timer"))
          throw new IllegalArgumentException(
              "This timer already has a pending finish. Review it in Settings.");
    ContentValues values = new ContentValues();
    values.put("endpoint", endpoint);
    values.put("payload", data.toString());
    values.put("state", "queued");
    values.put("message", "Waiting to sync");
    return getWritableDatabase().insertOrThrow("outbox", null, values);
  }

  synchronized void enqueueTimerFinish(String endpoint, JSONObject payload, long timerId)
      throws Exception {
    for (JSONObject row : pending())
      if (row.optLong("cleanup_timer", -1) == timerId
          || row.optJSONObject("payload").optLong("timer", -1) == timerId)
        throw new IllegalArgumentException(
            "This timer already has a pending finish. Review it in Settings.");
    JSONObject finished = Records.copy(payload);
    finished.remove("timer");
    FormValues.validateDuration(finished);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      long id = enqueue(endpoint, finished);
      ContentValues link = new ContentValues();
      link.put("cleanup_timer", timerId);
      db.update("outbox", link, "id=?", new String[] {Long.toString(id)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public synchronized List<JSONObject> pending() {
    List<JSONObject> rows = new ArrayList<>();
    try (Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,endpoint,payload,state,message,method,cleanup_timer FROM outbox ORDER BY"
                    + " id",
                null)) {
      while (cursor.moveToNext())
        rows.add(
            new JSONObject()
                .put("local_id", cursor.getLong(0))
                .put("endpoint", cursor.getString(1))
                .put("payload", new JSONObject(cursor.getString(2)))
                .put("state", cursor.getString(3))
                .put("message", cursor.getString(4))
                .put("method", cursor.getString(5))
                .put("cleanup_timer", cursor.isNull(6) ? null : cursor.getLong(6)));
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return rows;
  }

  @Override
  public synchronized void state(long id, String state, String message) {
    ContentValues values = new ContentValues();
    values.put("state", state);
    values.put("message", message);
    if (getWritableDatabase().update("outbox", values, "id=?", new String[] {Long.toString(id)})
        != 1) throw new IllegalStateException("Pending entry disappeared");
  }

  public synchronized void discard(long id) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("outbox", "id=?", new String[] {Long.toString(id)});
      db.delete(
          "active_timers", "start_id=? AND server_id IS NULL", new String[] {Long.toString(id)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public synchronized void accepted(long id, String endpoint, JSONObject record) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject data = snapshot();
      JSONArray rows = data.optJSONArray(endpoint);
      if (rows == null) rows = new JSONArray();
      JSONArray updated = new JSONArray();
      for (int i = 0; i < rows.length(); i++)
        if (rows.getJSONObject(i).optLong("id") != record.getLong("id"))
          updated.put(rows.getJSONObject(i));
      updated.put(record);
      data.put(endpoint, updated);
      for (JSONObject pending : pending()) {
        if (pending.optLong("local_id") == id && pending.optJSONObject("payload").has("timer")) {
          long legacyId = pending.getJSONObject("payload").getLong("timer");
          JSONArray shared = data.optJSONArray("timers"), remaining = new JSONArray();
          if (shared != null)
            for (int i = 0; i < shared.length(); i++)
              if (shared.getJSONObject(i).optLong("id") != legacyId)
                remaining.put(shared.getJSONObject(i));
          data.put("timers", remaining);
        }
        if (pending.optLong("local_id") == id && pending.has("cleanup_timer")) {
          long cleanup =
              enqueue(
                  "timers",
                  new JSONObject()
                      .put("id", pending.getLong("cleanup_timer"))
                      .put("start", pending.getJSONObject("payload").getString("start"))
                      .put("child", pending.getJSONObject("payload").getLong("child")));
          ContentValues deletion = new ContentValues();
          deletion.put("method", "DELETE");
          deletion.put("cleanup_timer", pending.getLong("cleanup_timer"));
          deletion.put("message", "Activity saved. Waiting to remove its shared timer.");
          db.update("outbox", deletion, "id=?", new String[] {Long.toString(cleanup)});
        }
      }
      if (endpoint.equals("timers")) {
        ContentValues link = new ContentValues();
        link.put("server_id", record.getLong("id"));
        db.update("active_timers", link, "start_id=?", new String[] {Long.toString(id)});
        // A stop recorded offline waits durably for the start's server ID.
        for (JSONObject timer : timers())
          if (timer.optLong("start_id", -1) == id) {
            JSONObject options =
                timer.getJSONObject("payload").put("start", record.getString("start"));
            ContentValues normalized = new ContentValues();
            normalized.put("payload", options.toString());
            db.update(
                "active_timers",
                normalized,
                "id=?",
                new String[] {Long.toString(timer.getLong("id"))});
            if (timer.has("finish_payload"))
              finishTimer(
                  timer.getLong("id"),
                  timer.getJSONObject("finish_payload").put("start", record.getString("start")));
          }
      }
      replace(data);
      discard(id);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public synchronized void removed(long id, long timerId) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject data = snapshot();
      JSONArray timers = data.optJSONArray("timers"), remaining = new JSONArray();
      if (timers != null)
        for (int i = 0; i < timers.length(); i++)
          if (timers.getJSONObject(i).optLong("id") != timerId)
            remaining.put(timers.getJSONObject(i));
      data.put("timers", remaining);
      replace(data);
      discard(id);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void clear() {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("cache", null, null);
      db.delete("outbox", null, null);
      db.delete("active_timers", null, null);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
