package com.babybuddypocket.app;

import java.util.*;
import org.json.*;

/** A deletion retains the exact reviewed record until the server confirms removal. */
final class ActivityDeletions {
  static final String CONFLICT =
      "This activity changed on another device. It has not been deleted. Discard this deletion"
          + " request, sync, then review the latest activity before deleting it.";

  static boolean isDeletion(JSONObject row) {
    return row.optString("method").equals("DELETE") && row.has("original");
  }

  static JSONObject original(JSONObject row) {
    JSONObject result = Records.copy(row);
    List<String> keys = new ArrayList<>();
    result.keys().forEachRemaining(keys::add);
    for (String key : keys) if (key.startsWith("_")) result.remove(key);
    return result;
  }

  static boolean matches(JSONObject current, JSONObject expected) {
    Set<String> keys = new HashSet<>();
    current.keys().forEachRemaining(keys::add);
    expected.keys().forEachRemaining(keys::add);
    for (String key : keys) {
      if (key.startsWith("_")) continue;
      Object a = current.opt(key), b = expected.opt(key);
      if (a instanceof JSONObject && b instanceof JSONObject) {
        if (!matches((JSONObject) a, (JSONObject) b)) return false;
      } else if (a instanceof JSONArray && b instanceof JSONArray) {
        JSONArray left = (JSONArray) a, right = (JSONArray) b;
        if (left.length() != right.length()) return false;
        for (int i = 0; i < left.length(); i++) {
          Object x = left.opt(i), y = right.opt(i);
          if (x instanceof JSONObject && y instanceof JSONObject) {
            if (!matches((JSONObject) x, (JSONObject) y)) return false;
          } else if (!ActivityEdits.same(key, x, y)) return false;
        }
      } else if (!ActivityEdits.same(key, a, b)) return false;
    }
    return true;
  }
}
