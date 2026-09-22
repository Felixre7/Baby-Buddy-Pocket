package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.time.*;
import org.json.*;
import org.junit.Test;

public class TimerClockTest {
  private final Instant server = Instant.parse("2026-01-01T12:00:00Z");

  private ApiClient api() {
    ApiClient api = new ApiClient("https://example.invalid", "synthetic");
    api.observeServerDate(server.toEpochMilli());
    return api;
  }

  @Test
  public void aheadPhoneStartStaysBehindServerWithoutChangingLocalInput() throws Exception {
    JSONObject input = new JSONObject().put("start", server.plusSeconds(30).toString());
    JSONObject output = api().timerTimes(input, false);
    assertTrue(Instant.parse(output.getString("start")).isBefore(server));
    assertEquals(server.plusSeconds(30).toString(), input.getString("start"));
  }

  @Test
  public void offlineSessionRetainsDurationWhenEndIsInFuture() throws Exception {
    JSONObject input =
        new JSONObject()
            .put("start", server.minusSeconds(300).toString())
            .put("end", server.plusSeconds(30).toString());
    JSONObject output = api().timerTimes(input, false);
    Instant start = Instant.parse(output.getString("start")),
        end = Instant.parse(output.getString("end"));
    assertTrue(end.isBefore(server));
    assertEquals(330, Duration.between(start, end).getSeconds());
  }

  @Test
  public void sharedFinishKeepsConfirmedStartAndCapsEnd() throws Exception {
    JSONObject input =
        new JSONObject()
            .put("start", server.minusSeconds(120).toString())
            .put("end", server.plusSeconds(30).toString());
    JSONObject output = api().timerTimes(input, true);
    assertEquals(input.getString("start"), output.getString("start"));
    assertTrue(Instant.parse(output.getString("end")).isBefore(server));
  }

  @Test
  public void pastTimesAndMissingHeaderRemainUnchanged() throws Exception {
    JSONObject past =
        new JSONObject()
            .put("start", server.minusSeconds(120).toString())
            .put("end", server.minusSeconds(60).toString());
    assertEquals(past.toString(), api().timerTimes(past, false).toString());
    ApiClient missing = new ApiClient("https://example.invalid", "synthetic");
    JSONObject future = new JSONObject().put("start", server.plusSeconds(60).toString());
    missing.observeServerDate(0);
    assertEquals(future.toString(), missing.timerTimes(future, false).toString());
  }

  @Test
  public void impossibleSharedClockWaitsBeforeSending() {
    JSONObject future =
        Records.put(
            Records.put(new JSONObject(), "start", server.plusSeconds(60).toString()),
            "end",
            server.plusSeconds(120).toString());
    assertThrows(java.io.IOException.class, () -> api().timerTimes(future, true));
  }
}
