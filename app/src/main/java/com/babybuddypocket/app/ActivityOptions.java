package com.babybuddypocket.app;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Device preferences; hiding an activity never changes downloaded or queued records. */
final class ActivityOptions {
  private final SharedPreferences preferences;
  private final String prefix;

  ActivityOptions(SharedPreferences preferences, boolean demo) {
    this.preferences = preferences;
    prefix = demo ? "demo_" : "";
  }

  List<String> ordered() {
    List<String> types = new ArrayList<>();
    List<String> supported = new ArrayList<>(Arrays.asList(Records.ENDPOINTS));
    supported.removeAll(Arrays.asList("timers", "tags"));
    for (String type : preferences.getString(prefix + "activity_order", "").split(","))
      if (supported.contains(type) && !types.contains(type)) types.add(type);
    for (String type : supported) if (!types.contains(type)) types.add(type);
    return types;
  }

  boolean visible(String type) {
    return !preferences.getBoolean(prefix + "hide_" + type, false);
  }

  void visible(String type, boolean visible) {
    preferences.edit().putBoolean(prefix + "hide_" + type, !visible).apply();
  }

  List<String> visible() {
    List<String> types = ordered();
    types.removeIf(type -> !visible(type));
    return types;
  }

  void move(String type, int direction) {
    List<String> types = ordered();
    int from = types.indexOf(type), to = from + direction;
    if (from < 0 || to < 0 || to >= types.size()) return;
    Collections.swap(types, from, to);
    preferences.edit().putString(prefix + "activity_order", String.join(",", types)).apply();
  }

  JSONObject defaults(String endpoint, long child) {
    try {
      return new JSONObject(
          preferences.getString(prefix + "defaults_" + child + "_" + endpoint, "{}"));
    } catch (JSONException e) {
      return new JSONObject();
    }
  }

  void remember(String endpoint, long child, JSONObject schema, Map<String, String> values) {
    preferences
        .edit()
        .putString(
            prefix + "defaults_" + child + "_" + endpoint,
            FormValues.choices(schema, values).toString())
        .apply();
  }
}
