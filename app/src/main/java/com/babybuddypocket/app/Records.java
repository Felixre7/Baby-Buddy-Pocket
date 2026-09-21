package com.babybuddypocket.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public final class Records {
  private Records() {}

  static boolean timed(String type) {
    return Arrays.asList("feedings", "sleep", "tummy-times", "pumping").contains(type);
  }

  public static final String[] ENDPOINTS = {
    "feedings",
    "sleep",
    "changes",
    "tummy-times",
    "pumping",
    "notes",
    "weight",
    "height",
    "head-circumference",
    "temperature",
    "bmi",
    "medications",
    "timers",
    "tags"
  };

  public static String title(String type) {
    switch (type) {
      case "feedings":
        return "Feeding";
      case "sleep":
        return "Sleep";
      case "changes":
        return "Diaper";
      case "tummy-times":
        return "Tummy time";
      case "head-circumference":
        return "Head circumference";
      case "bmi":
        return "BMI";
      case "medications":
        return "Medication";
      case "notes":
        return "Note";
      case "timers":
        return "Timer";
      default:
        return type.isEmpty() ? "" : Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }
  }

  public static JSONObject copy(JSONObject value) {
    try {
      return new JSONObject(value.toString());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  public static JSONObject put(JSONObject object, String key, Object value) {
    try {
      object.put(key, value);
      return object;
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String text(JSONObject row, String field) {
    return row.isNull(field) ? "" : row.optString(field, "");
  }

  public static Instant instant(String value) {
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (Exception ignored) {
      try {
        return LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant();
      } catch (Exception e) {
        return Instant.EPOCH;
      }
    }
  }

  public static Instant time(JSONObject row) {
    for (String key : new String[] {"start", "time", "date"})
      if (!text(row, key).isEmpty()) return instant(text(row, key));
    return Instant.EPOCH;
  }

  public static long overlap(JSONObject row, LocalDate day, ZoneId zone) {
    Instant start = instant(text(row, "start")), end = instant(text(row, "end"));
    Instant low = day.atStartOfDay(zone).toInstant(),
        high = day.plusDays(1).atStartOfDay(zone).toInstant();
    return Math.max(
        0,
        Duration.between(start.isAfter(low) ? start : low, end.isBefore(high) ? end : high)
            .getSeconds());
  }

  public static String duration(long seconds) {
    long minutes = Math.max(0, seconds) / 60;
    return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
  }

  public static List<JSONObject> timeline(JSONObject snapshot, long child) {
    List<JSONObject> result = new ArrayList<>();
    for (String endpoint : ENDPOINTS) {
      if (endpoint.equals("timers") || endpoint.equals("tags")) continue;
      JSONArray rows = snapshot.optJSONArray(endpoint);
      if (rows == null) continue;
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.optJSONObject(i);
        if (row != null && row.optLong("child", -1) == child)
          result.add(put(copy(row), "_type", endpoint));
      }
    }
    result.sort((a, b) -> time(b).compareTo(time(a)));
    return result;
  }

  public static String summary(JSONObject row, Units units) {
    String type = text(row, "_type");
    List<String> parts = new ArrayList<>();
    if (type.equals("changes")) {
      if (row.optBoolean("wet")) parts.add("Wet");
      if (row.optBoolean("solid")) parts.add("Solid");
      if (parts.isEmpty()) parts.add("Dry");
    }
    for (String key : new String[] {"type", "method", "name", "note", "milestone"}) {
      if (!text(row, key).isEmpty()) parts.add(text(row, key));
    }
    if (!text(row, "start").isEmpty() && !text(row, "end").isEmpty())
      parts.add(
          duration(
              Duration.between(instant(text(row, "start")), instant(text(row, "end")))
                  .getSeconds()));
    if (!text(row, "amount").isEmpty())
      parts.add(text(row, "amount") + units.label(type, "amount"));
    String[] fields = {"weight", "height", "head_circumference", "temperature", "bmi", "dosage"};
    for (String field : fields)
      if (!text(row, field).isEmpty())
        parts.add(
            text(row, field)
                + (field.equals("dosage")
                    ? " " + text(row, "dosage_unit")
                    : units.label(type, field)));
    if (parts.isEmpty()) parts.add(text(row, "notes").isEmpty() ? title(type) : text(row, "notes"));
    return String.join(" · ", parts);
  }

  public static String clock(Instant time) {
    return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(time);
  }
}
