package com.babybuddypocket.app;

import java.math.BigDecimal;
import java.util.*;
import org.json.*;

/** Pending edits reuse the outbox; only changed fields are sent to the existing record. */
final class ActivityEdits {
  static final String CONFLICT =
      "This activity changed on another device. Your edit is still saved here. Delete this pending"
          + " edit, sync, then edit the latest activity to avoid replacing someone else's changes.";
  static final String REMOVED =
      "This activity was removed from the server. Delete this pending edit;"
          + " it will not recreate the removed activity.";

  static boolean same(String key, Object a, Object b) {
    if (a == null || a == JSONObject.NULL) return b == null || b == JSONObject.NULL;
    if (b == null || b == JSONObject.NULL) return false;
    if (Arrays.asList("start", "end", "time").contains(key))
      return Records.instant(a.toString()).equals(Records.instant(b.toString()));
    if (a instanceof Number || b instanceof Number)
      try {
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
      } catch (NumberFormatException ignored) {
        return false;
      }
    return a.toString().equals(b.toString());
  }

  static JSONObject changes(JSONObject original, JSONObject desired) throws JSONException {
    JSONObject changed = new JSONObject();
    Iterator<String> keys = desired.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!key.startsWith("_")
          && !key.equals("id")
          && !key.equals("child")
          && !same(key, original.opt(key), desired.opt(key))) changed.put(key, desired.get(key));
    }
    return changed;
  }

  static boolean matches(JSONObject current, JSONObject expected, JSONObject fields) {
    if (!same("child", current.opt("child"), expected.opt("child"))) return false;
    Iterator<String> keys = fields.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!same(key, current.opt(key), expected.opt(key))) return false;
    }
    return true;
  }

  static List<JSONObject> rows(JSONObject data, long child, List<JSONObject> pending) {
    List<JSONObject> rows = Records.timeline(data, child);
    for (JSONObject item : pending) {
      String type = item.optString("endpoint");
      if (type.equals("timers")) continue;
      JSONObject payload = item.optJSONObject("payload");
      if (payload == null || payload.optLong("child", -1) != child) continue;
      JSONObject original = item.optJSONObject("original");
      JSONObject shown = original == null ? new JSONObject() : Records.copy(original);
      if (original != null)
        rows.removeIf(
            row ->
                row.optString("_type").equals(type)
                    && row.optLong("id", -1) == original.optLong("id"));
      payload.keys().forEachRemaining(key -> Records.put(shown, key, payload.opt(key)));
      Records.put(shown, "_type", type);
      Records.put(shown, "_pending", item);
      rows.add(shown);
    }
    rows.sort((a, b) -> Records.time(b).compareTo(Records.time(a)));
    return rows;
  }
}
