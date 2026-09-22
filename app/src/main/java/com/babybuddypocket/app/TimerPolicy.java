package com.babybuddypocket.app;

import java.util.List;
import org.json.*;

/** A timer has no reliable activity type, so the running slot belongs to its child. */
final class TimerPolicy {
  static final String CONFLICT =
      "Another timer is running for this child. This local timer was not shared."
          + " Use Today to review the timers; cancel the extra one or save a separate session.";

  static boolean applies(JSONObject timer, long child) {
    // An unassigned server timer may belong to this child; do not silently ignore it.
    return timer.isNull("child") || timer.optLong("child", -1) == child;
  }

  static boolean ending(List<JSONObject> pending, long timerId) {
    for (JSONObject row : pending)
      if (row.optLong("cleanup_timer", row.optJSONObject("payload").optLong("timer", -1))
          == timerId) return true;
    return false;
  }

  static boolean runningLocal(JSONObject timer, List<JSONObject> pending) {
    return !timer.optBoolean("cancel_requested")
        && !timer.has("finish_payload")
        && (!timer.has("server_id") || !ending(pending, timer.optLong("server_id")));
  }
}
