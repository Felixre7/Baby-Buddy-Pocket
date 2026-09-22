package com.babybuddypocket.app;

import android.app.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import org.json.*;

final class RecordForm {
  private final MainActivity activity;
  private final Ui ui;
  private final AppController app;
  private final Map<String, Supplier<String>> readers = new LinkedHashMap<>();
  private AlertDialog dialog;
  private String endpoint;
  private Long timer;
  private long child;
  private boolean startingTimer;
  private Long localTimer;
  private Long pendingEntry;
  private JSONObject original;

  RecordForm(MainActivity activity, Ui ui, AppController app) {
    this.activity = activity;
    this.ui = ui;
    this.app = app;
  }

  boolean visible() {
    return dialog != null && dialog.isShowing();
  }

  void dismiss() {
    if (dialog != null) dialog.dismiss();
  }

  Bundle state() {
    if (!visible()) return null;
    Bundle out = new Bundle();
    out.putString("endpoint", endpoint);
    out.putLong("child", child);
    out.putBoolean("startingTimer", startingTimer);
    if (localTimer != null) out.putLong("localTimer", localTimer);
    if (timer != null) out.putLong("timer", timer);
    if (pendingEntry != null) out.putLong("pendingEntry", pendingEntry);
    if (original != null) out.putString("original", original.toString());
    for (Map.Entry<String, Supplier<String>> entry : readers.entrySet())
      out.putString("field:" + entry.getKey(), entry.getValue().get());
    return out;
  }

  void show(String endpoint, Long timer, Bundle restored) {
    JSONObject schema = app.schema(endpoint);
    if (schema == null) {
      activity.message(
          "Logging unavailable",
          "Connect and sync with a user who has permission to add these records.");
      return;
    }
    this.endpoint = endpoint;
    this.timer = timer;
    original = null;
    if (restored != null && restored.containsKey("original")) {
      try {
        original = new JSONObject(restored.getString("original"));
      } catch (JSONException e) {
        activity.message("Unable to restore edit", "Open this activity again.");
        return;
      }
    }
    pendingEntry =
        restored != null && restored.containsKey("pendingEntry")
            ? restored.getLong("pendingEntry")
            : null;
    startingTimer =
        restored == null
            ? timer == null && Records.timed(endpoint)
            : restored.getBoolean("startingTimer");
    localTimer =
        restored != null && restored.containsKey("localTimer")
            ? restored.getLong("localTimer")
            : null;
    this.child = restored == null ? app.child() : restored.getLong("child", app.child());
    String childName = "this child";
    JSONArray children = app.data.optJSONArray("children");
    if (children != null)
      for (int i = 0; i < children.length(); i++)
        if (children.optJSONObject(i).optLong("id") == child)
          childName = Records.text(children.optJSONObject(i), "first_name");
    readers.clear();
    LinearLayout content = ui.column();
    content.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(20));
    ui.add(
        content,
        ui.text(
            app.demo ? "Sample entry · stays in demo" : "For " + childName, 13, ui.muted, false));
    ui.gap(content, 16);
    if (timer != null) {
      ui.add(
          content,
          ui.text(
              "Finishing server timer #"
                  + timer
                  + ". Its start time will be used. Saving records the end time on this phone,"
                  + " then removes the shared timer after the activity syncs.",
              14));
      ui.gap(content, 16);
    }
    if (timer == null
        && localTimer == null
        && pendingEntry == null
        && original == null
        && Records.timed(endpoint)) {
      Switch mode = new Switch(activity);
      mode.setText(R.string.start_timer_now);
      mode.setTextColor(ui.ink);
      mode.setMinHeight(ui.dp(48));
      mode.setChecked(startingTimer);
      mode.setOnCheckedChangeListener(
          (button, checked) -> {
            Bundle draft = state();
            draft.putBoolean("startingTimer", checked);
            dialog.dismiss();
            show(endpoint, null, draft);
          });
      ui.add(content, mode);
      ui.add(
          content,
          ui.text(
              startingTimer
                  ? "Choose your options, then start. Stop & save works offline too."
                  : "Enter start and end times for an earlier activity, or turn on the timer.",
              13,
              ui.muted,
              false));
      ui.gap(content, 16);
    }
    JSONObject defaults = app.activities().defaults(endpoint, child);
    List<String> keys = FormValues.orderedKeys(schema);
    for (String key : keys) {
      JSONObject field = schema.optJSONObject(key);
      if (field == null
          || field.optBoolean("read_only")
          || Arrays.asList("id", "child", "timer", "user", "image", "url").contains(key)
          || ((timer != null || startingTimer) && (key.equals("start") || key.equals("end"))))
        continue;
      String type = field.optString("type"),
          label =
              field.optString("label", key.replace('_', ' '))
                  + app.units().label(endpoint, key)
                  + (FormValues.required(schema, key) ? " *" : "");
      TextView fieldLabel = ui.text(label, 13, ui.muted, true);
      ui.add(content, fieldLabel);
      ui.gap(content, 6);
      String initial =
          restored == null ? defaults.optString(key, "") : restored.getString("field:" + key, "");
      if (field.has("choices")) {
        JSONArray choices = field.optJSONArray("choices");
        List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        labels.add("Choose…");
        values.add("");
        if (choices != null)
          for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            labels.add(choice.optString("display_name"));
            values.add(choice.optString("value"));
          }
        Spinner spinner = ui.choice(fieldLabel, labels, Math.max(0, values.indexOf(initial)));
        ui.add(content, spinner);
        readers.put(key, () -> values.get(spinner.getSelectedItemPosition()));
      } else if (type.equals("boolean")) {
        String[] options = {field.optBoolean("required") ? "Choose…" : "Automatic", "Yes", "No"};
        Spinner spinner =
            ui.choice(
                fieldLabel,
                Arrays.asList(options),
                initial.equals("true") ? 1 : initial.equals("false") ? 2 : 0);
        ui.add(content, spinner);
        readers.put(
            key,
            () ->
                spinner.getSelectedItemPosition() == 1
                    ? "true"
                    : spinner.getSelectedItemPosition() == 2 ? "false" : "");
      } else if (type.equals("date") || type.equals("datetime")) {
        ZonedDateTime defaultTime = ZonedDateTime.now().withSecond(0).withNano(0);
        if (key.equals("start")) defaultTime = defaultTime.minusMinutes(20);
        final ZonedDateTime[] chosen = {
          initial.isEmpty()
              ? defaultTime
              : (type.equals("date")
                  ? LocalDate.parse(initial).atStartOfDay(ZoneId.systemDefault())
                  : Records.instant(initial).atZone(ZoneId.systemDefault()))
        };
        Button date = ui.button("", false, () -> {});
        Runnable update =
            () ->
                date.setText(
                    chosen[0].format(
                        DateTimeFormatter.ofPattern(
                            type.equals("date") ? "EEE, MMM d, yyyy" : "MMM d, yyyy · h:mm a",
                            Locale.getDefault())));
        update.run();
        date.setOnClickListener(
            v -> {
              ZonedDateTime t = chosen[0];
              DatePickerDialog picker =
                  new DatePickerDialog(
                      activity,
                      (view, y, m, d) -> {
                        LocalDate day = LocalDate.of(y, m + 1, d);
                        if (type.equals("date")) {
                          chosen[0] = day.atStartOfDay(ZoneId.systemDefault());
                          update.run();
                        } else
                          new TimePickerDialog(
                                  activity,
                                  (clock, h, min) -> {
                                    chosen[0] = day.atTime(h, min).atZone(ZoneId.systemDefault());
                                    update.run();
                                  },
                                  t.getHour(),
                                  t.getMinute(),
                                  android.text.format.DateFormat.is24HourFormat(activity))
                              .show();
                      },
                      t.getYear(),
                      t.getMonthValue() - 1,
                      t.getDayOfMonth());
              picker.getDatePicker().setMaxDate(System.currentTimeMillis());
              picker.show();
            });
        ui.add(content, date);
        readers.put(
            key,
            () ->
                type.equals("date")
                    ? chosen[0].toLocalDate().toString()
                    : chosen[0].toOffsetDateTime().toString());
      } else {
        EditText input =
            ui.edit(
                key.equals("tags")
                    ? "e.g. night, milestone"
                    : field.optBoolean("required") ? "Required" : "Optional");
        input.setText(initial);
        if (type.equals("float")
            || type.equals("decimal")
            || type.equals("number")
            || type.equals("integer"))
          input.setInputType(
              InputType.TYPE_CLASS_NUMBER
                  | InputType.TYPE_NUMBER_FLAG_DECIMAL
                  | InputType.TYPE_NUMBER_FLAG_SIGNED);
        else {
          input.setInputType(
              InputType.TYPE_CLASS_TEXT
                  | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                  | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
          if (key.equals("note") || key.equals("notes")) input.setMinLines(2);
        }
        ui.add(content, input);
        readers.put(key, () -> input.getText().toString());
      }
      ui.gap(content, 14);
    }
    TextView error = ui.text("", 14, Ui.color(ui.dark ? "#FFB4AB" : "#A53830"), false);
    error.setVisibility(View.GONE);
    ui.add(content, error);
    View runningTimer =
        ui.button(
            "View running timer",
            false,
            () -> {
              dialog.dismiss();
              activity.logged();
            });
    runningTimer.setVisibility(View.GONE);
    ui.add(content, runningTimer);
    ScrollView scroll = new ScrollView(activity);
    scroll.setFillViewport(false);
    scroll.addView(content);
    dialog =
        new AlertDialog.Builder(activity)
            .setTitle(
                (pendingEntry != null || original != null
                        ? "Edit "
                        : startingTimer ? "Time " : timer == null ? "Log " : "Finish as ")
                    + Records.title(endpoint).toLowerCase(Locale.ROOT))
            .setView(scroll)
            .create();
    ui.add(
        content,
        ui.button(
            pendingEntry != null || original != null
                ? "Save changes"
                : startingTimer ? "Start timer" : app.demo ? "Add sample" : "Save entry",
            true,
            () -> {
              try {
                Map<String, String> values = new LinkedHashMap<>();
                readers.forEach((key, reader) -> values.put(key, reader.get()));
                if (startingTimer) {
                  String now = Instant.now().toString();
                  values.put("start", now);
                  values.put("end", now);
                }
                JSONObject payload =
                    original == null
                        ? FormValues.parse(schema, values, child, timer)
                        : FormValues.parseEdit(schema, values, child, original);
                if (pendingEntry != null) app.editPending(pendingEntry, payload);
                else if (original != null) app.editActivity(endpoint, original, payload);
                else if (startingTimer) app.startTimer(endpoint, payload);
                else if (localTimer != null) app.finishTimer(localTimer, payload);
                else app.add(endpoint, payload);
                app.activities().remember(endpoint, child, schema, values);
                dialog.dismiss();
                activity.logged();
                Toast.makeText(
                        activity,
                        startingTimer
                            ? (app.demo ? "Sample timer started" : "Timer started")
                            : app.demo ? "Sample added" : "Activity saved",
                        Toast.LENGTH_SHORT)
                    .show();
              } catch (Exception e) {
                app.log(DiagnosticLog.Event.LOCAL_FAILURE, e);
                error.setText(SyncEngine.friendly(e));
                error.setVisibility(View.VISIBLE);
                runningTimer.setVisibility(
                    e instanceof AppController.TimerAlreadyRunning ? View.VISIBLE : View.GONE);
                scroll.post(() -> scroll.smoothScrollTo(0, error.getTop()));
              }
            }));
    ui.gap(content, 8);
    ui.add(content, ui.button("Cancel", false, () -> dialog.dismiss()));
    dialog.show();
    dialog.getWindow().setBackgroundDrawable(ui.shape(ui.surface, 24));
    dialog.getWindow().getDecorView().setClipToOutline(true);
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  void edit(String endpoint, JSONObject payload, Long pendingId, JSONObject serverOriginal) {
    Bundle draft = new Bundle();
    draft.putLong("child", payload.optLong("child"));
    draft.putBoolean("startingTimer", false);
    if (pendingId != null) draft.putLong("pendingEntry", pendingId);
    if (serverOriginal != null) draft.putString("original", serverOriginal.toString());
    Iterator<String> keys = payload.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      String value = payload.isNull(key) ? "" : payload.optString(key);
      if (key.equals("tags") && payload.optJSONArray(key) != null) {
        List<String> tags = new ArrayList<>();
        JSONArray array = payload.optJSONArray(key);
        for (int i = 0; i < array.length(); i++) tags.add(array.optString(i));
        value = String.join(", ", tags);
      }
      draft.putString("field:" + key, value);
    }
    show(endpoint, null, draft);
  }
}
