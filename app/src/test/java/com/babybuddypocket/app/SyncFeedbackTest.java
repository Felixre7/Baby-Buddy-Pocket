package com.babybuddypocket.app;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class SyncFeedbackTest {
  private JSONObject row(String state, String message) throws Exception {
    return new JSONObject()
        .put("local_id", 7)
        .put("state", state)
        .put("message", message)
        .put("endpoint", "sleep")
        .put("payload", new JSONObject().put("child", 1));
  }

  @Test
  public void translatesOldOverlapWithoutEchoingServerHtmlOrFamilyValues() throws Exception {
    JSONObject row =
        row(
            "rejected",
            "Server returned 400. {\"non_field_errors\":[\"Another entry intersects the specified"
                + " time period. <a href='https://private.invalid/sleep/42'>Private Child September"
                + " 21</a>\"]}");
    assertEquals("Times overlap", SyncFeedback.label(row));
    String message = SyncFeedback.pending(row);
    assertTrue(message.contains("same type for this child"));
    assertTrue(message.contains("edit its times"));
    for (String value :
        Arrays.asList(
            "Private", "private.invalid", "42", "September", "<a", "400", "non_field_errors"))
      assertFalse(message.contains(value));
  }

  @Test
  public void translatesCommonValidationAndAccountErrors() {
    String[][] cases = {
      {"Date/time can not be in the future.", "ahead of the server's clock"},
      {"Start time must come before end time.", "end time must be after"},
      {"Duration too long.", "longer than the server allows"},
      {"This field is required.", "required value is missing"},
      {"xyz is not a valid choice.", "option is not accepted"}
    };
    for (String[] item : cases) assertTrue(SyncFeedback.explain(400, item[0]).contains(item[1]));
    assertTrue(SyncFeedback.explain(401, "secret").contains("API token"));
    assertTrue(SyncFeedback.explain(403, "secret").contains("permission"));
    assertTrue(SyncFeedback.explain(404, "secret").contains("could not be found"));
    assertTrue(SyncFeedback.explain(429, "secret").contains("Wait a little"));
    assertTrue(SyncFeedback.explain(500, "secret").contains("Report a problem"));
    assertFalse(SyncFeedback.explain(500, "secret").contains("secret"));
  }

  @Test
  public void uncertainWritesStayReviewOnlyAndTimerOperationsCannotBeEdited() throws Exception {
    JSONObject row = row("review", "Server returned 400. Date/time can not be in the future.");
    assertTrue(SyncFeedback.pending(row).contains("couldn't confirm"));
    assertFalse(SyncFeedback.canEdit(row));
    row.put("state", "queued");
    assertFalse(SyncFeedback.canEdit(row));
    row.put("state", "rejected");
    assertTrue(SyncFeedback.canEdit(row));
    row.put("endpoint", "timers");
    assertFalse(SyncFeedback.canEdit(row));
    row.put("endpoint", "sleep").put("method", "DELETE");
    assertFalse(SyncFeedback.canEdit(row));
    row.put("method", "POST").getJSONObject("payload").put("timer", 8);
    assertFalse(SyncFeedback.canEdit(row));
  }

  @Test
  public void newProblemDoesNotRepeatAnUnchangedRejection() throws Exception {
    JSONObject queued = row("queued", "Waiting to sync");
    JSONObject rejected = row("rejected", "Server returned 400. Duration too long.");
    assertSame(rejected, SyncFeedback.newProblem(Arrays.asList(queued), Arrays.asList(rejected)));
    assertNull(SyncFeedback.newProblem(Arrays.asList(rejected), Arrays.asList(rejected)));
    assertNull(SyncFeedback.newProblem(Arrays.asList(rejected), Collections.emptyList()));
    JSONObject changed = row("rejected", "Server returned 400. This field is required.");
    assertSame(changed, SyncFeedback.newProblem(Arrays.asList(rejected), Arrays.asList(changed)));
    assertSame(rejected, SyncFeedback.newProblem(Arrays.asList(queued), Arrays.asList(rejected)));
  }
}
