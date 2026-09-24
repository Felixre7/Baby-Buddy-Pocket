package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class RecordsAndFormsTest {
  @Test
  public void activityAgesUseCompletedMinutesThroughTheFirstDay() {
    Instant now = Instant.parse("2026-09-24T12:00:00Z");
    long[] ages = {
      -60, 0, 59, 60, 119, 3599, 3600, 3601, 3899, 7200, 8099, 43199, 43200, 44899, 67199, 86399,
      86400, 172799, 172800
    };
    String[] expected = {
      "just now",
      "just now",
      "just now",
      "1m ago",
      "1m ago",
      "59m ago",
      "1h 0m ago",
      "1h 0m ago",
      "1h 4m ago",
      "2h 0m ago",
      "2h 14m ago",
      "11h 59m ago",
      "12h 0m ago",
      "12h 28m ago",
      "18h 39m ago",
      "23h 59m ago",
      "1d ago",
      "1d ago",
      "2d ago"
    };
    for (int i = 0; i < ages.length; i++)
      assertEquals(
          "Age in seconds: " + ages[i],
          expected[i],
          Records.relative(now.minusSeconds(ages[i]), now));
  }

  @Test
  public void requiredFieldsLeadAndDurationTimesAreRequiredWithoutTimer() throws Exception {
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("feedings");
    schema.getJSONObject("start").put("required", false);
    schema.getJSONObject("end").put("required", false);
    List<String> keys = FormValues.orderedKeys(schema);
    for (String required : new String[] {"type", "method", "start", "end"})
      assertTrue(keys.indexOf(required) < keys.indexOf("amount"));
    assertThrows(
        IllegalArgumentException.class,
        () -> FormValues.parse(schema, Map.of("type", "breast milk", "method", "bottle"), 1, null));
  }

  @Test
  public void remembersOnlyChoicesAndTogglesIncludingExplicitFalse() throws Exception {
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("feedings");
    schema.put("nap", new JSONObject().put("type", "boolean"));
    JSONObject saved =
        FormValues.choices(
            schema,
            Map.of(
                "type",
                "formula",
                "method",
                "bottle",
                "amount",
                "90",
                "notes",
                "private note",
                "start",
                "2026-01-01T00:00:00Z",
                "nap",
                "false"));
    assertEquals(3, saved.length());
    assertEquals("formula", saved.getString("type"));
    assertEquals("false", saved.getString("nap"));
    assertFalse(saved.has("amount"));
    assertFalse(saved.has("notes"));
    assertFalse(saved.has("start"));
  }

  @Test
  public void metricLabelsByDefault() throws Exception {
    JSONObject record = new JSONObject().put("_type", "feedings").put("amount", 4);
    assertEquals("4 ml", Records.summary(record, new Units(false, false, false, false)));
  }

  @Test
  public void splitsSleepAcrossMidnight() throws Exception {
    JSONObject sleep =
        new JSONObject()
            .put("start", "2026-09-19T23:30:00-07:00")
            .put("end", "2026-09-20T01:00:00-07:00");
    assertEquals(
        1800, Records.overlap(sleep, LocalDate.parse("2026-09-19"), ZoneId.of("America/Phoenix")));
    assertEquals(
        3600, Records.overlap(sleep, LocalDate.parse("2026-09-20"), ZoneId.of("America/Phoenix")));
    assertEquals(
        0, Records.overlap(sleep, LocalDate.parse("2026-09-21"), ZoneId.of("America/Phoenix")));
  }

  @Test
  public void usesActualDstDayDuration() throws Exception {
    JSONObject sleep =
        new JSONObject()
            .put("start", "2026-03-08T00:00:00-05:00")
            .put("end", "2026-03-09T00:00:00-04:00");
    assertEquals(
        23 * 3600,
        Records.overlap(sleep, LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York")));
  }

  @Test
  public void sortsChronologicallyAndIsolatesChildren() throws Exception {
    JSONObject data =
        new JSONObject()
            .put(
                "notes",
                new JSONArray()
                    .put(new JSONObject().put("child", 1).put("time", "2026-01-01T10:00:00Z"))
                    .put(new JSONObject().put("child", 2).put("time", "2026-01-02T10:00:00Z")))
            .put(
                "sleep",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("child", 1)
                            .put("start", "2026-01-01T09:00:00-02:00")));
    List<JSONObject> rows = Records.timeline(data, 1);
    assertEquals(2, rows.size());
    assertEquals("sleep", rows.get(0).getString("_type"));
    assertFalse(data.getJSONArray("sleep").getJSONObject(0).has("_type"));
  }

  @Test
  public void unitsAreIndependentAndNeverConvert() throws Exception {
    JSONObject record = new JSONObject().put("_type", "feedings").put("amount", 4);
    assertEquals("4 fl oz", Records.summary(record, new Units(true, false, false, false)));
    assertEquals(" kg", new Units(true, false, true, false).label("weight", "weight"));
    assertEquals("", new Units(true, true, true, true).label("changes", "amount"));
  }

  @Test
  public void validatesRequiredServerChoices() throws Exception {
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("feedings");
    assertThrows(
        IllegalArgumentException.class,
        () -> FormValues.parse(schema, Map.of("type", "made up"), 1, null));
    assertThrows(IllegalArgumentException.class, () -> FormValues.parse(schema, Map.of(), 1, null));
  }

  @Test
  public void buildsFeedingAndNormalizesTags() throws Exception {
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("feedings");
    Map<String, String> values = new HashMap<>();
    values.put("type", "breast milk");
    values.put("method", "bottle");
    values.put("amount", "120.5");
    values.put("start", "2026-01-01T10:00:00-07:00");
    values.put("end", "2026-01-01T10:20:00-07:00");
    values.put("tags", "night, milk, night");
    JSONObject payload = FormValues.parse(schema, values, 4, null);
    assertEquals(4, payload.getInt("child"));
    assertEquals(120.5, payload.getDouble("amount"), .001);
    assertEquals("2026-01-01T17:00:00Z", payload.getString("start"));
    assertEquals(2, payload.getJSONArray("tags").length());
  }

  @Test
  public void preventsNegativeAndNonFiniteAmounts() throws Exception {
    JSONObject schema = new JSONObject().put("amount", new JSONObject().put("type", "float"));
    for (String value : new String[] {"NaN", "Infinity", "-1", "a lot"})
      assertThrows(
          IllegalArgumentException.class,
          () -> FormValues.parse(schema, Map.of("amount", value), 1, null));
  }

  @Test
  public void rejectsReversedOrExcessiveDurations() throws Exception {
    JSONObject schema =
        new JSONObject()
            .put("start", new JSONObject().put("type", "datetime"))
            .put("end", new JSONObject().put("type", "datetime"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FormValues.parse(
                schema,
                Map.of("start", "2026-01-01T12:00:00Z", "end", "2026-01-01T11:00:00Z"),
                1,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FormValues.parse(
                schema,
                Map.of("start", "2026-01-01T12:00:00Z", "end", "2026-01-03T12:00:00Z"),
                1,
                null));
  }

  @Test
  public void timerReplacesTimes() throws Exception {
    JSONObject schema = DemoData.create().getJSONObject("_schemas").getJSONObject("sleep");
    JSONObject payload =
        FormValues.parse(
            schema, Map.of("start", "not needed", "end", "not needed", "nap", "true"), 2, 55L);
    assertEquals(55, payload.getLong("timer"));
    assertFalse(payload.has("start"));
    assertTrue(payload.getBoolean("nap"));
  }

  @Test
  public void rejectsFutureDates() throws Exception {
    JSONObject schema = new JSONObject().put("date", new JSONObject().put("type", "date"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FormValues.parse(
                schema, Map.of("date", LocalDate.now().plusDays(1).toString()), 1, null));
  }
}
