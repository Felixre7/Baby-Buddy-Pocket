package com.babybuddypocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.json.*;

/** Bounded local technical events. Call on the existing worker; never pass record data. */
final class DiagnosticLog {
  enum Event {
    CONNECT_FAILED,
    SYNC_FAILED,
    WRITE_ACCEPTED,
    WRITE_REJECTED,
    WRITE_UNCERTAIN,
    TIMER_START_REJECTED,
    TIMER_TIME_ADJUSTED,
    TIMER_CONFLICT,
    TIMER_STARTED,
    TIMER_STOPPED,
    TIMER_CANCELLED,
    LOCAL_FAILURE
  }

  private static final long MAX_AGE = 7L * 24 * 60 * 60 * 1000;
  private static final int MAX_BYTES = 65536, MAX_EVENTS = 100;
  private final File file;
  private final LongSupplier clock;

  DiagnosticLog(File file) {
    this(file, System::currentTimeMillis);
  }

  DiagnosticLog(File file, LongSupplier clock) {
    this.file = file;
    this.clock = clock;
  }

  synchronized void record(Event event, Throwable failure) {
    try {
      List<JSONObject> entries = read();
      entries.add(
          new JSONObject()
              .put("time", clock.getAsLong())
              .put("event", event.name())
              .put("detail", failure(failure)));
      while (entries.size() > MAX_EVENTS) entries.remove(0);
      String data = new JSONArray(entries).toString();
      while (data.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES && !entries.isEmpty()) {
        entries.remove(0);
        data = new JSONArray(entries).toString();
      }
      File temporary = new File(file.getPath() + ".tmp");
      Files.write(temporary.toPath(), data.getBytes(StandardCharsets.UTF_8));
      Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ignored) {
      // Logging must not change the outcome of a saved activity or sync.
    }
  }

  synchronized String report() {
    StringBuilder out = new StringBuilder("Recent technical events (no record contents):\n");
    try {
      List<JSONObject> entries = read();
      if (entries.isEmpty()) out.append("No recorded events.\n");
      for (JSONObject entry : entries) {
        out.append(Math.max(0, (clock.getAsLong() - entry.getLong("time")) / 60000))
            .append(" min ago: ")
            .append(entry.getString("event"))
            .append(entry.getString("detail"))
            .append('\n');
      }
    } catch (Exception ignored) {
      out.append("Diagnostics unavailable.\n");
    }
    return out.toString();
  }

  synchronized void clear() {
    file.delete();
    new File(file.getPath() + ".tmp").delete();
  }

  private List<JSONObject> read() throws Exception {
    List<JSONObject> entries = new ArrayList<>();
    if (!file.exists()) return entries;
    if (file.length() > MAX_BYTES) {
      clear();
      return entries;
    }
    JSONArray data;
    try {
      data = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    } catch (JSONException malformed) {
      clear();
      return entries;
    }
    for (int i = 0; i < data.length(); i++) {
      JSONObject entry = data.getJSONObject(i);
      long age = clock.getAsLong() - entry.getLong("time");
      if (age >= 0 && age <= MAX_AGE) entries.add(entry);
    }
    if (entries.isEmpty()) clear();
    else if (entries.size() != data.length())
      Files.write(
          file.toPath(), new JSONArray(entries).toString().getBytes(StandardCharsets.UTF_8));
    return entries;
  }

  static String failure(Throwable failure) {
    if (failure == null) return "";
    StringBuilder out = new StringBuilder();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = failure;
        cause != null && seen.size() < 3 && seen.add(cause);
        cause = cause.getCause()) {
      out.append("; ").append(cause.getClass().getSimpleName());
      if (cause instanceof ApiClient.HttpFailure) {
        ApiClient.HttpFailure http = (ApiClient.HttpFailure) cause;
        out.append(" HTTP ").append(http.status);
        if (!http.fields.isEmpty()) out.append(" fields=").append(http.fields);
        out.append(" reason=").append(SyncFeedback.reason(http.status, http.getMessage()).name());
      }
      int frames = 0;
      for (StackTraceElement frame : cause.getStackTrace()) {
        if (!frame.getClassName().startsWith("com.babybuddypocket.app.")) continue;
        out.append("\n  ")
            .append(frame.getClassName())
            .append('.')
            .append(frame.getMethodName())
            .append(':')
            .append(frame.getLineNumber());
        if (++frames == 4) break;
      }
    }
    return out.toString();
  }
}
