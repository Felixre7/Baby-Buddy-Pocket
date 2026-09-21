package com.babybuddypocket.app;

import java.time.*;
import java.util.*;
import org.json.*;

/** Schema-driven conversion/validation shared by the form and JVM tests. */
public final class FormValues {
  private FormValues() {}

  static boolean required(JSONObject schema, String key) {
    // The API marks these optional because a server timer can supply them instead.
    return schema.optJSONObject(key).optBoolean("required")
        || ((key.equals("start") || key.equals("end")) && schema.has("start") && schema.has("end"));
  }

  static List<String> orderedKeys(JSONObject schema) {
    List<String> keys = new ArrayList<>();
    for (String key :
        new String[] {
          "type",
          "method",
          "amount",
          "start",
          "end",
          "time",
          "date",
          "wet",
          "solid",
          "nap",
          "name",
          "weight",
          "height",
          "head_circumference",
          "temperature",
          "bmi",
          "dosage",
          "dosage_unit",
          "next_dose_interval",
          "color",
          "milestone",
          "note",
          "notes",
          "tags"
        }) if (schema.optJSONObject(key) != null) keys.add(key);
    schema
        .keys()
        .forEachRemaining(
            key -> {
              if (!keys.contains(key) && schema.optJSONObject(key) != null) keys.add(key);
            });
    keys.sort(Comparator.comparing(key -> !required(schema, key)));
    return keys;
  }

  static JSONObject choices(JSONObject schema, Map<String, String> values) {
    JSONObject saved = new JSONObject();
    values.forEach(
        (key, value) -> {
          JSONObject field = schema.optJSONObject(key);
          if (field != null
              && !field.optBoolean("read_only")
              && (field.has("choices") || field.optString("type").equals("boolean")))
            Records.put(saved, key, value);
        });
    return saved;
  }

  public static JSONObject parse(
      JSONObject schema, Map<String, String> values, long child, Long timer) throws Exception {
    JSONObject payload = new JSONObject().put("child", child);
    if (timer != null) payload.put("timer", timer);
    Iterator<String> keys = schema.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      JSONObject field = schema.optJSONObject(key);
      if (field == null
          || field.optBoolean("read_only")
          || key.equals("child")
          || key.equals("timer")
          || key.equals("user")
          || key.equals("image")) continue;
      if (timer != null && (key.equals("start") || key.equals("end"))) continue;
      String value = values.getOrDefault(key, "").trim();
      String label = field.optString("label", key);
      if (value.isEmpty()) {
        if (required(schema, key) && !field.optBoolean("allow_blank") && !field.has("default"))
          throw new IllegalArgumentException(label + " is required.");
        continue;
      }
      String type = field.optString("type");
      try {
        if (field.has("choices")) {
          JSONArray choices = field.getJSONArray("choices");
          boolean found = false;
          for (int i = 0; i < choices.length(); i++)
            if (value.equals(choices.getJSONObject(i).optString("value"))) {
              payload.put(key, choices.getJSONObject(i).get("value"));
              found = true;
              break;
            }
          if (!found)
            throw new IllegalArgumentException(
                "Choose a valid " + label.toLowerCase(Locale.ROOT) + ".");
        } else if (key.equals("tags")) {
          JSONArray tags = new JSONArray();
          Set<String> unique = new LinkedHashSet<>();
          for (String tag : value.split(",")) if (!tag.trim().isEmpty()) unique.add(tag.trim());
          for (String tag : unique) tags.put(tag);
          payload.put(key, tags);
        } else if (type.equals("boolean")) {
          if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("Choose yes or no for " + label + ".");
          payload.put(key, Boolean.parseBoolean(value));
        } else if (type.equals("integer")) {
          payload.put(key, Long.parseLong(value));
        } else if (type.equals("float") || type.equals("decimal") || type.equals("number")) {
          double number = Double.parseDouble(value);
          if (!Double.isFinite(number)) throw new NumberFormatException();
          if (field.has("min_value") && number < field.getDouble("min_value"))
            throw new IllegalArgumentException(label + " is too small.");
          if (field.has("max_value") && number > field.getDouble("max_value"))
            throw new IllegalArgumentException(label + " is too large.");
          if (Arrays.asList("amount", "weight", "height", "head_circumference", "dosage", "bmi")
                  .contains(key)
              && number < 0) throw new IllegalArgumentException(label + " cannot be negative.");
          payload.put(key, number);
        } else if (type.equals("datetime")) {
          Instant instant = OffsetDateTime.parse(value).toInstant();
          if (instant.isAfter(Instant.now().plusSeconds(30)))
            throw new IllegalArgumentException(label + " cannot be in the future.");
          payload.put(key, instant.toString());
        } else if (type.equals("date")) {
          LocalDate date = LocalDate.parse(value);
          if (date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException(label + " cannot be in the future.");
          payload.put(key, date.toString());
        } else {
          if (field.has("max_length") && value.length() > field.getInt("max_length"))
            throw new IllegalArgumentException(label + " is too long.");
          payload.put(key, value);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Enter a valid number for " + label.toLowerCase(Locale.ROOT) + ".");
      } catch (java.time.format.DateTimeParseException e) {
        throw new IllegalArgumentException(
            "Choose a valid date/time for " + label.toLowerCase(Locale.ROOT) + ".");
      }
    }
    validateDuration(payload);
    return payload;
  }

  static void validateDuration(JSONObject payload) throws Exception {
    if (payload.has("start") && payload.has("end")) {
      long seconds =
          Duration.between(
                  Records.instant(payload.getString("start")),
                  Records.instant(payload.getString("end")))
              .getSeconds();
      if (seconds < 0) throw new IllegalArgumentException("End must be after start.");
      if (seconds > 86400)
        throw new IllegalArgumentException("A session cannot be longer than 24 hours.");
    }
  }
}
