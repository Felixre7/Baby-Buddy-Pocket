package com.babybuddypocket.app;

import java.util.*;
import org.json.JSONObject;

/** User-facing explanations and safe diagnostic categories; never echo server response values. */
final class SyncFeedback {
  enum Reason {
    OVERLAP,
    FUTURE_TIME,
    TIME_ORDER,
    DURATION,
    REQUIRED,
    CHOICE,
    AUTH,
    PERMISSION,
    NOT_FOUND,
    RATE_LIMIT,
    UNKNOWN
  }

  static Reason reason(int status, String message) {
    if (status == 401) return Reason.AUTH;
    if (status == 403) return Reason.PERMISSION;
    if (status == 404) return Reason.NOT_FOUND;
    if (status == 429) return Reason.RATE_LIMIT;
    String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (text.contains("another entry intersects the specified time period")
        || text.contains("period_intersection")) return Reason.OVERLAP;
    if (text.contains("can not be in the future") || text.contains("cannot be in the future"))
      return Reason.FUTURE_TIME;
    if (text.contains("start time must come before end time") || text.contains("end_before_start"))
      return Reason.TIME_ORDER;
    if (text.contains("duration too long") || text.contains("max_duration")) return Reason.DURATION;
    if (text.contains("this field is required")
        || text.contains("may not be null")
        || text.contains("may not be blank")) return Reason.REQUIRED;
    if (text.contains("is not a valid choice") || text.contains("invalid_choice"))
      return Reason.CHOICE;
    if (text.contains("access denied")) return Reason.PERMISSION;
    return Reason.UNKNOWN;
  }

  private static int status(String message) {
    java.util.regex.Matcher match =
        java.util.regex.Pattern.compile("Server returned (\\d{3})").matcher(message);
    return match.find() ? Integer.parseInt(match.group(1)) : 0;
  }

  static String explain(int status, String message) {
    switch (reason(status, message)) {
      case OVERLAP:
        return "These times overlap another activity of the same type for this child."
            + " Check the timeline. If this is a duplicate, delete this pending copy;"
            + " otherwise edit its times.";
      case FUTURE_TIME:
        return "A date or time is ahead of the server's clock. Check the entry's times"
            + " and the phone/server clocks before retrying.";
      case TIME_ORDER:
        return "The end time must be after the start time. Edit the activity to correct its times.";
      case DURATION:
        return "This activity is longer than the server allows. Check its start and end times.";
      case REQUIRED:
        return "A required value is missing. Edit the activity and fill in all required fields.";
      case CHOICE:
        return "A selected option is not accepted by this server. Sync to refresh the choices,"
            + " then edit the activity.";
      case AUTH:
        return "The server did not accept your API token. Check your connection details in"
            + " Settings.";
      case PERMISSION:
        return "Your account does not have permission to do this. Check its permissions on the"
            + " server.";
      case NOT_FOUND:
        return "The item could not be found. It may have been changed or removed on another"
            + " device.";
      case RATE_LIMIT:
        return "The server is receiving too many requests. Wait a little before retrying.";
      default:
        return "Sync failed for an unexpected reason. Use Report a problem in Settings to share"
            + " technical details.";
    }
  }

  static String pending(JSONObject row) {
    String state = row.optString("state"), raw = row.optString("message");
    if (state.equals("queued")) return "Waiting to sync.";
    if (state.equals("review"))
      return "We couldn't confirm whether this entry reached the server. It is still saved here."
          + " Check the timeline or server before retrying, so it isn't logged twice.";
    if (raw.equals(TimerPolicy.CONFLICT)
        || raw.equals(ActivityEdits.CONFLICT)
        || raw.equals(ActivityEdits.REMOVED)
        || raw.startsWith("The shared timer changed.")
        || raw.startsWith("The shared timer was already finished or removed.")
        || raw.startsWith("The timer was restarted or changed.")) return raw;
    return explain(status(raw), raw);
  }

  static String label(JSONObject row) {
    if (row.optString("state").equals("queued")) return "Waiting to sync";
    if (row.optString("state").equals("review")) return "Check before retrying";
    if (reason(status(row.optString("message")), row.optString("message")) == Reason.OVERLAP)
      return "Times overlap";
    return "Needs attention";
  }

  static boolean needsAttention(JSONObject row) {
    return row.optString("state").equals("rejected") || row.optString("state").equals("review");
  }

  static boolean canEdit(JSONObject row) {
    return (row.optString("state").equals("rejected") || row.optString("state").equals("queued"))
        && !row.optString("message").equals(ActivityEdits.CONFLICT)
        && !row.optString("message").equals(ActivityEdits.REMOVED)
        && (row.optString("method", "POST").equals("POST")
            || row.optString("method").equals("PATCH"))
        && !row.optString("endpoint").equals("timers")
        && row.optJSONObject("payload") != null
        && !row.optJSONObject("payload").has("timer");
  }

  static JSONObject newProblem(List<JSONObject> before, List<JSONObject> after) {
    Map<Long, String> previous = new HashMap<>();
    for (JSONObject row : before)
      previous.put(
          row.optLong("local_id"), row.optString("state") + "\n" + row.optString("message"));
    for (JSONObject row : after)
      if (needsAttention(row)
          && !(row.optString("state") + "\n" + row.optString("message"))
              .equals(previous.get(row.optLong("local_id")))) return row;
    return null;
  }
}
