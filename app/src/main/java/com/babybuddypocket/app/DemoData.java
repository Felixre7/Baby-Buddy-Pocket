package com.babybuddypocket.app;

import java.time.*;
import org.json.*;

/** Synthetic records only; never sent to a server. */
public final class DemoData {
  private DemoData() {}

  public static JSONObject create() {
    try {
      JSONObject data = new JSONObject();
      data.put(
          "children",
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("id", 1)
                      .put("first_name", "Maya")
                      .put("last_name", "")
                      .put("birth_date", LocalDate.now().minusMonths(3).toString())));
      for (String type : Records.ENDPOINTS) data.put(type, new JSONArray());
      Instant now = Instant.now();
      for (int day = 0; day < 7; day++) {
        Instant dayTime = now.minus(Duration.ofDays(day));
        record(data, "feedings", dayTime.minusSeconds(85 * 60), 20 * 60)
            .put("type", "breast milk")
            .put("method", "bottle")
            .put("amount", 120);
        record(data, "feedings", dayTime.minusSeconds(4 * 3600), 18 * 60)
            .put("type", "breast milk")
            .put("method", "left breast");
        record(data, "sleep", dayTime.minusSeconds(3 * 3600), 95 * 60).put("nap", true);
        record(data, "sleep", dayTime.minusSeconds(10 * 3600), (5 + day % 3) * 3600)
            .put("nap", false);
        record(data, "changes", dayTime.minusSeconds(30 * 60), 0)
            .put("wet", true)
            .put("solid", false);
        record(data, "changes", dayTime.minusSeconds(3 * 3600 + 120), 0)
            .put("wet", true)
            .put("solid", true);
        record(data, "tummy-times", dayTime.minusSeconds(5 * 3600), 12 * 60);
      }
      record(data, "notes", now.minusSeconds(2 * 3600), 0)
          .put("note", "A little more curious about the world today.")
          .put("tags", new JSONArray().put("milestone"));
      record(data, "weight", now.minusSeconds(86400), 0).put("weight", 5.8);
      data.put("_synced", System.currentTimeMillis());
      JSONObject schemas = new JSONObject();
      for (String type : Records.ENDPOINTS) {
        if (type.equals("tags") || type.equals("timers")) continue;
        JSONObject fields = new JSONObject().put("child", field("integer", "Child", true));
        if (type.equals("feedings")
            || type.equals("sleep")
            || type.equals("pumping")
            || type.equals("tummy-times")) {
          fields.put("start", field("datetime", "Start", true));
          fields.put("end", field("datetime", "End", true));
        } else
          fields.put(
              type.equals("weight")
                      || type.equals("height")
                      || type.equals("bmi")
                      || type.equals("head-circumference")
                  ? "date"
                  : "time",
              field(
                  type.equals("weight")
                          || type.equals("height")
                          || type.equals("bmi")
                          || type.equals("head-circumference")
                      ? "date"
                      : "datetime",
                  "When",
                  true));
        if (type.equals("feedings")) {
          fields.put(
              "type",
              choice("Type", "breast milk", "formula", "fortified breast milk", "solid food"));
          fields.put(
              "method",
              choice(
                  "Method",
                  "bottle",
                  "left breast",
                  "right breast",
                  "both breasts",
                  "parent fed",
                  "self fed"));
        }
        if (type.equals("feedings") || type.equals("pumping"))
          fields.put("amount", field("float", "Amount", type.equals("pumping")));
        if (type.equals("sleep")) fields.put("nap", field("boolean", "Nap", false));
        if (type.equals("changes")) {
          fields.put("wet", field("boolean", "Wet", false));
          fields.put("solid", field("boolean", "Solid", false));
        }
        if (type.equals("notes")) fields.put("note", field("string", "Note", true));
        if (type.equals("weight")
            || type.equals("height")
            || type.equals("head-circumference")
            || type.equals("temperature")
            || type.equals("bmi"))
          fields.put(type.replace('-', '_'), field("float", Records.title(type), true));
        if (type.equals("medications")) {
          fields.put("name", field("string", "Medication name", true));
          fields.put("dosage", field("float", "Dosage", false));
          fields.put("dosage_unit", choice("Dose unit", "mg", "ml", "tablets", "drops"));
        }
        if (!type.equals("notes")) fields.put("notes", field("string", "Notes", false));
        fields.put("tags", field("list", "Tags", false));
        schemas.put(type, fields);
      }
      return data.put("_schemas", schemas);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  private static JSONObject field(String type, String label, boolean required)
      throws JSONException {
    return new JSONObject().put("type", type).put("label", label).put("required", required);
  }

  private static JSONObject choice(String label, String... values) throws JSONException {
    JSONObject f = field("choice", label, true);
    JSONArray choices = new JSONArray();
    for (String value : values)
      choices.put(new JSONObject().put("value", value).put("display_name", value));
    return f.put("choices", choices);
  }

  private static JSONObject record(JSONObject data, String type, Instant start, long duration)
      throws JSONException {
    JSONObject row =
        new JSONObject()
            .put("id", data.getJSONArray(type).length() + 1)
            .put("child", 1)
            .put("tags", new JSONArray());
    if (duration > 0)
      row.put("start", start.toString()).put("end", start.plusSeconds(duration).toString());
    else
      row.put(
          type.equals("weight") ? "date" : "time",
          type.equals("weight")
              ? start.atZone(ZoneId.systemDefault()).toLocalDate().toString()
              : start.toString());
    data.getJSONArray(type).put(row);
    return row;
  }
}
