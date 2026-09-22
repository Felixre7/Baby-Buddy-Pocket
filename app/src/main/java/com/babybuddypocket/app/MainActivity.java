package com.babybuddypocket.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private static final int SCAN_DEVICE = 31;
  private AppController app;
  private Ui ui;
  private RecordForm form;
  private LinearLayout root, page;
  private ScrollView scroll;
  private LinearLayout timelineRows;
  private int tab = 0, limit = 60;
  private boolean choosingActivity;
  private Long finishingTimer;
  private Bundle pendingForm;
  private String filter = "all", query = "", serverDraft = "", tokenDraft = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          if (pendingForm == null && !form.visible()) app.sync();
          handler.postDelayed(this, 60000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    ui = new Ui(this);
    app = AppController.get(this);
    form = new RecordForm(this, ui, app);
    if (state != null) {
      tab = state.getInt("tab");
      choosingActivity = state.getBoolean("choosingActivity");
      finishingTimer = state.containsKey("finishingTimer") ? state.getLong("finishingTimer") : null;
      filter = state.getString("filter", "all");
      query = state.getString("query", "");
      serverDraft = state.getString("server", "");
    }
    root = ui.column();
    root.setBackgroundColor(ui.background);
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      getWindow()
          .getInsetsController()
          .setSystemBarsAppearance(
              ui.dark
                  ? 0
                  : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                      | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
              WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    } else {
      getWindow().setStatusBarColor(ui.background);
      getWindow().setNavigationBarColor(ui.background);
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              ui.dark
                  ? 0
                  : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                      | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i =
                insets.getInsets(
                    WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
            root.setPadding(i.left, i.top, i.right, i.bottom);
          } else
            root.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
              () -> {
                if (choosingActivity) navigate(tab);
                else finish();
              });
    render();
    if (state != null && state.getBundle("form") != null) {
      pendingForm = state.getBundle("form");
      // Older Android versions need the activity window attached before restoring a dialog.
      root.post(
          () -> {
            if (isFinishing() || isDestroyed() || pendingForm == null) return;
            Bundle saved = pendingForm;
            pendingForm = null;
            form.show(
                saved.getString("endpoint"),
                saved.containsKey("timer") ? saved.getLong("timer") : null,
                saved);
          });
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    app.listener = this::render;
    render();
    if (pendingForm == null && !form.visible()) app.sync();
    handler.postDelayed(refresh, 60000);
  }

  @Override
  protected void onPause() {
    super.onPause();
    app.listener = null;
    handler.removeCallbacks(refresh);
  }

  @Override
  protected void onDestroy() {
    if (form != null) form.dismiss();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putInt("tab", tab);
    out.putBoolean("choosingActivity", choosingActivity);
    if (finishingTimer != null) out.putLong("finishingTimer", finishingTimer);
    out.putString("filter", filter);
    out.putString("query", query);
    out.putString("server", serverDraft);
    Bundle draft = pendingForm == null ? form.state() : pendingForm;
    if (draft != null) out.putBundle("form", draft);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == SCAN_DEVICE && result == RESULT_OK && data != null && !app.connected()) {
      serverDraft = data.getStringExtra("server");
      tokenDraft = data.getStringExtra("token");
      app.error = "";
      render();
      Toast.makeText(
              this, "Code scanned. Review the server address before connecting.", Toast.LENGTH_LONG)
          .show();
    }
  }

  private void render() {
    if (isFinishing() || isDestroyed()) return;
    int oldScroll = scroll == null ? 0 : scroll.getScrollY();
    root.removeAllViews();
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    page = ui.column();
    page.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(22));
    scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (!app.connected() && !app.demo) {
      onboarding();
      return;
    }
    tokenDraft = "";
    header();
    demoBanner();
    if (choosingActivity) activityGrid();
    else
      switch (tab) {
        case 0:
          today();
          break;
        case 1:
          timeline();
          break;
        case 2:
          trends();
          break;
        default:
          settings();
      }
    navigation();
    scroll.post(() -> scroll.scrollTo(0, oldScroll));
    root.post(this::showSyncProblem);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus && root != null) root.post(this::showSyncProblem);
  }

  private void showSyncProblem() {
    if (isFinishing()
        || isDestroyed()
        || !hasWindowFocus()
        || app.busy
        || form.visible()
        || pendingForm != null
        || app.syncProblem == null) return;
    JSONObject problem = app.syncProblem;
    app.syncProblem = null;
    // The entry might have been resolved while another dialog was open.
    for (JSONObject row : app.pending()) {
      if (row.optLong("local_id") != problem.optLong("local_id")
          || !SyncFeedback.needsAttention(row)) continue;
      new AlertDialog.Builder(this)
          .setTitle(Records.title(row.optString("endpoint")) + " needs attention")
          .setMessage(SyncFeedback.pending(row))
          .setPositiveButton(
              "Review entry",
              (d, w) -> {
                navigate(3);
                pendingDetail(row);
              })
          .setNegativeButton("Later", null)
          .show();
      break;
    }
  }

  private void onboarding() {
    ui.gap(page, 28);
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_launcher);
    logo.setBackground(ui.shape(ui.soft, 22));
    logo.setClipToOutline(true);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(ui.dp(78), ui.dp(78));
    page.addView(logo, iconParams);
    ui.gap(page, 24);
    ui.add(page, ui.text(getString(R.string.app_name), 34, ui.ink, true));
    ui.gap(page, 8);
    ui.add(page, ui.text("Your baby’s day,\nin your pocket.", 26, ui.accent, false));
    ui.gap(page, 12);
    ui.add(page, ui.text("Requires an existing Baby Buddy server.", 16, ui.ink, true));
    ui.gap(page, 8);
    ui.add(
        page,
        ui.text(
            "An independent Android companion. Connect with your server address and API token."
                + " Server hosting is not included.",
            14,
            ui.muted,
            false));
    ui.gap(page, 12);
    ui.add(
        page,
        ui.button(
            "Need a server? Try Railway",
            false,
            () -> {
              String url =
                  "https://railway.com/deploy/baby-buddy-self-hosted-baby-tracker--baby-buddy";
              try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
              } catch (ActivityNotFoundException e) {
                message("Open the Railway template in a browser", url);
              }
            }));
    ui.gap(page, 28);
    LinearLayout card = ui.card(page, 20);
    ui.add(card, ui.text("Your Baby Buddy server", 18, ui.ink, true));
    Button scan =
        ui.button(
            "Scan Baby Buddy QR code",
            false,
            () -> startActivityForResult(new Intent(this, QrScanActivity.class), SCAN_DEVICE));
    scan.setEnabled(!app.busy);
    ui.add(card, scan);
    ui.gap(card, 16);
    ui.add(card, ui.text("Server address", 13, ui.muted, true));
    ui.gap(card, 6);
    EditText server = ui.edit("https://your-server.up.railway.app");
    server.setSingleLine(true);
    server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    server.setText(serverDraft);
    ui.add(card, server);
    ui.gap(card, 14);
    ui.add(card, ui.text("API token", 13, ui.muted, true));
    ui.gap(card, 6);
    EditText token = ui.edit("Paste your API token");
    token.setSingleLine(true);
    token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    token.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    token.setSaveEnabled(false);
    token.setText(tokenDraft);
    ui.add(card, token);
    watch(server, value -> serverDraft = value);
    watch(token, value -> tokenDraft = value);
    ui.gap(card, 10);
    ui.add(
        card,
        ui.text(
            "Open Baby Buddy → User settings → API key. Each caregiver can use their own token.",
            12,
            ui.muted,
            false));
    ui.gap(card, 18);
    Button connect =
        ui.button(
            app.busy ? "Connecting…" : "Connect to Baby Buddy",
            true,
            () -> {
              serverDraft = server.getText().toString();
              tokenDraft = token.getText().toString();
              app.connect(serverDraft, tokenDraft);
            });
    connect.setEnabled(!app.busy);
    ui.add(card, connect);
    if (!app.error.isEmpty()) {
      ui.gap(card, 12);
      ui.add(card, ui.text(app.error, 14, Ui.color(ui.dark ? "#FFB4AB" : "#A53830"), false));
    }
    ui.add(
        page,
        ui.button(
            "Try offline demo",
            false,
            () -> {
              tokenDraft = "";
              app.demo();
            }));
    ui.gap(page, 18);
    TextView footer =
        ui.text(
            "Your device. Your server. Your family's data.\n"
                + "No account with us. No ads or analytics.",
            12,
            ui.muted,
            false);
    footer.setGravity(Gravity.CENTER);
    ui.add(page, footer);
    ui.add(page, ui.button("Report a problem", false, this::diagnostics));
  }

  private void header() {
    LinearLayout row = ui.row(), left = ui.column();
    row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
    String date =
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()));
    ui.add(
        left,
        ui.text(
            tab == 0
                ? date
                : new String[] {"", "YOUR SHARED DAY", "THE LITTLE PATTERNS", "MAKE IT YOURS"}[tab],
            12,
            ui.muted,
            false));
    ui.gap(left, 3);
    String name = Records.text(app.childRecord(), "first_name");
    ui.add(
        left,
        ui.text(
            tab == 0
                ? (name.isEmpty() ? "Welcome" : name)
                : new String[] {"", "Timeline", "Trends", "Settings"}[tab],
            32,
            ui.ink,
            true));
    Button child =
        ui.button(
            name.isEmpty() ? "Child" : name.substring(0, 1).toUpperCase(Locale.getDefault()) + " ▾",
            false,
            this::chooseChild);
    child.setContentDescription("Choose child: " + name);
    row.addView(child, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(54)));
    ui.add(page, row);
    ui.gap(page, 14);
  }

  private void demoBanner() {
    if (!app.demo) return;
    LinearLayout banner = ui.card(page, 12);
    banner.setBackground(ui.shape(ui.soft, 16));
    ui.add(banner, ui.text("DEMO · sample data only", 12, ui.accent, true));
    ui.gap(banner, 6);
    ui.add(
        banner,
        ui.text(
            "Try the app freely. Demo entries stay here and are never uploaded.",
            12,
            ui.accent,
            false));
  }

  private void syncStatus(LinearLayout parent) {
    String status;
    if (app.busy) status = "Syncing your shared day…";
    else if (!app.error.isEmpty()) status = "Offline or sync interrupted · cached data";
    else if (app.data.optLong("_synced") == 0) status = "Waiting for your first sync";
    else status = "Last synced " + relative(Instant.ofEpochMilli(app.data.optLong("_synced")));
    ui.add(parent, ui.text(status, 13, ui.muted, false));
    if (!app.error.isEmpty()) {
      ui.gap(parent, 10);
      ui.add(parent, ui.text(app.error, 13, Ui.color(ui.dark ? "#FFB4AB" : "#A53830"), false));
    }
    ui.gap(parent, 14);
  }

  private void today() {
    if (app.child() < 0) {
      empty(
          "A place for your little one",
          "Add a child on your Baby Buddy server, then sync in Settings.");
      return;
    }
    List<JSONObject> rows = visibleRows();
    timers();
    List<String> types = app.activities().visible();
    List<String> summaryTypes = new ArrayList<>(types);
    summaryTypes.retainAll(Arrays.asList("feedings", "sleep", "changes", "tummy-times"));
    if (!summaryTypes.isEmpty()) ui.section(page, "TODAY AT A GLANCE");
    LinearLayout pair = null;
    for (int i = 0; i < summaryTypes.size(); i++) {
      String type = summaryTypes.get(i);
      if (i % 2 == 0) {
        pair = ui.row();
        ui.add(page, pair);
        ui.gap(page, 12);
      }
      stat(
          pair,
          type,
          Records.title(type),
          type.equals("sleep") || type.equals("tummy-times")
              ? Records.duration(total(rows, type, LocalDate.now()))
              : Integer.toString(count(rows, type, LocalDate.now())),
          last(rows, type));
    }
    types.removeIf(type -> app.schema(type) == null);
    if (!types.isEmpty()) {
      ui.section(page, "A LITTLE UPDATE");
      LinearLayout quick = ui.row();
      for (String endpoint : types.subList(0, Math.min(4, types.size()))) {
        LinearLayout button = ui.column();
        button.setGravity(Gravity.CENTER);
        button.setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(8));
        button.addView(ui.icon(endpoint, 42, true));
        ui.gap(button, 6);
        TextView name = ui.text(Records.title(endpoint), 11, ui.muted, true);
        name.setGravity(Gravity.CENTER);
        ui.add(button, name);
        quick.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        ui.clickable(button, () -> log(endpoint, null));
        button.setContentDescription("Log " + Records.title(endpoint));
      }
      ui.add(page, quick);
      ui.gap(page, 12);
    }
    ui.add(page, ui.button("＋  Log activity", true, () -> chooseLog(null)));
    ui.section(page, "LATEST MOMENTS");
    if (rows.isEmpty())
      empty("Your day starts here", "Log a feeding, a nap, or a little moment to remember.");
    else for (int i = 0; i < Math.min(6, rows.size()); i++) recordCard(page, rows.get(i));
    if (rows.size() > 6)
      ui.add(page, ui.button("See the whole timeline", false, () -> navigate(1)));
  }

  private void stat(LinearLayout parent, String type, String label, String value, String detail) {
    LinearLayout c = ui.column();
    c.setBackground(ui.shape(ui.surface, 22));
    c.setPadding(ui.dp(16), ui.dp(16), ui.dp(12), ui.dp(16));
    LinearLayout top = ui.row();
    top.addView(ui.icon(type, 30, true));
    TextView title = ui.text(label, 13, ui.muted, false);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = ui.dp(8);
    top.addView(title, p);
    ui.add(c, top);
    ui.gap(c, 12);
    ui.add(c, ui.text(value, 28, ui.ink, true));
    ui.gap(c, 4);
    ui.add(c, ui.text(detail, 11, ui.muted, false));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    if (parent.getChildCount() > 0) params.leftMargin = ui.dp(12);
    parent.addView(c, params);
    ui.clickable(
        c,
        () -> {
          filter = type;
          navigate(1);
        });
  }

  private void timers() {
    List<JSONObject> pendingRows = app.pending();
    JSONArray timers = app.data.optJSONArray("timers");
    int running = 0;
    for (JSONObject timer : app.timers())
      if (!timer.has("server_id")
          && TimerPolicy.runningLocal(timer, pendingRows)
          && timer.optJSONObject("payload").optLong("child") == app.child()) running++;
    if (timers != null)
      for (int i = 0; i < timers.length(); i++) {
        JSONObject timer = timers.optJSONObject(i);
        if (TimerPolicy.applies(timer, app.child())
            && !TimerPolicy.ending(pendingRows, timer.optLong("id"))) running++;
      }
    boolean conflict = running > 1;
    if (conflict) {
      LinearLayout notice = ui.card(page, 20);
      ui.add(notice, ui.text("Multiple timers found", 18, ui.ink, true));
      ui.add(
          notice,
          ui.text(
              "Another device may have started a timer too. Review the timers below."
                  + " Cancel an extra timer, or stop and save only if it is a separate session."
                  + " Nothing is merged automatically.",
              14));
    }
    for (JSONObject timer : app.timers()) {
      if (timer.has("server_id")
          || timer.has("finish_payload")
          || timer.optBoolean("cancel_requested")) continue;
      JSONObject payload = timer.optJSONObject("payload");
      if (payload.optLong("child") != app.child()) continue;
      LinearLayout card =
          timerCard(
              (conflict ? "Only on this phone · " : "")
                  + Records.title(timer.optString("endpoint")),
              Records.time(payload));
      timerActions(card, timer, false);
    }
    if (timers == null) return;
    for (int i = 0; i < timers.length(); i++) {
      JSONObject timer = timers.optJSONObject(i);
      if (!TimerPolicy.applies(timer, app.child())) continue;
      long serverId = timer.optLong("id");
      boolean pending = TimerPolicy.ending(pendingRows, serverId);
      JSONObject options = app.timerOptions(serverId);
      if (pending || (options != null && options.optBoolean("cancel_requested"))) continue;
      LinearLayout card =
          timerCard(
              (conflict ? "Shared · " : "")
                  + (Records.text(timer, "name").isEmpty() ? "Timer" : Records.text(timer, "name")),
              Records.time(timer));
      if (options == null) {
        ui.add(card, ui.button("Finish & log…", true, () -> chooseLog(serverId)));
        ui.add(card, ui.button("Cancel timer", false, () -> cancelTimer(serverId, true)));
      } else timerActions(card, options, app.demo);
    }
  }

  private void timerActions(LinearLayout card, JSONObject timer, boolean demo) {
    long id = timer.optLong("id");
    JSONObject payload = timer.optJSONObject("payload");
    ui.add(
        card,
        ui.button(
            "Stop & save",
            true,
            () -> {
              try {
                app.finishTimer(id, Records.copy(payload).put("end", Instant.now().toString()));
                Toast.makeText(this, demo ? "Sample added" : "Activity saved", Toast.LENGTH_SHORT)
                    .show();
              } catch (Exception e) {
                app.log(DiagnosticLog.Event.LOCAL_FAILURE, e);
                message("Could not save timer", SyncEngine.friendly(e));
              }
            }));
    ui.add(
        card,
        ui.button(
            "Edit times",
            false,
            () -> {
              Bundle draft = new Bundle();
              draft.putLong("localTimer", id);
              draft.putLong("child", payload.optLong("child"));
              payload
                  .keys()
                  .forEachRemaining(key -> draft.putString("field:" + key, payload.optString(key)));
              draft.putString("field:end", Instant.now().toString());
              form.show(timer.optString("endpoint"), null, draft);
            }));
    ui.add(card, ui.button("Cancel timer", false, () -> cancelTimer(id, false)));
  }

  private void cancelTimer(long id, boolean shared) {
    new AlertDialog.Builder(this)
        .setTitle("Cancel this timer?")
        .setMessage(
            "No activity will be saved. A shared timer will be removed when syncing succeeds.")
        .setNegativeButton("Keep timer", null)
        .setPositiveButton(
            "Cancel timer",
            (dialog, which) -> {
              try {
                if (shared) app.cancelSharedTimer(id);
                else app.cancelTimer(id);
                Toast.makeText(this, "Timer cancelled on this device", Toast.LENGTH_SHORT).show();
              } catch (Exception e) {
                app.log(DiagnosticLog.Event.LOCAL_FAILURE, e);
                message("Could not cancel timer", SyncEngine.friendly(e));
              }
            })
        .show();
  }

  private LinearLayout timerCard(String title, Instant start) {
    LinearLayout card = ui.card(page, 20);
    card.setBackground(ui.shape(ui.soft, 24));
    ui.add(card, ui.text("●  " + title, 14, ui.accent, true));
    ui.gap(card, 12);
    Chronometer clock = new Chronometer(this);
    clock.setTextColor(ui.ink);
    clock.setTextSize(36);
    clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    long elapsed = Math.max(0, Duration.between(start, Instant.now()).toMillis());
    clock.setBase(SystemClock.elapsedRealtime() - elapsed);
    clock.start();
    ui.add(card, clock);
    ui.gap(card, 6);
    ui.add(card, ui.text("Started " + Records.clock(start), 12, ui.muted, false));
    ui.gap(card, 14);
    return card;
  }

  void logged() {
    navigate(0);
  }

  private void timeline() {
    EditText search = ui.edit("Search notes, tags, activities…");
    search.setSingleLine(true);
    search.setText(query);
    ui.add(page, search);
    ui.gap(page, 12);
    HorizontalScrollView filters = new HorizontalScrollView(this);
    filters.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = ui.row();
    if (!filter.equals("all") && !app.activities().visible(filter)) filter = "all";
    List<String> types = app.activities().visible();
    types.add(0, "all");
    for (String type : types) {
      if (!type.equals("all") && !app.data.has(type)) continue;
      TextView chip =
          ui.text(
              type.equals("all") ? "All activities" : Records.title(type),
              12,
              type.equals(filter) ? (ui.dark ? ui.background : Color.WHITE) : ui.muted,
              true);
      chip.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
      chip.setMinHeight(ui.dp(48));
      chip.setGravity(Gravity.CENTER);
      chip.setBackground(ui.shape(type.equals(filter) ? ui.accent : ui.surface, 18));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
      p.rightMargin = ui.dp(8);
      chips.addView(chip, p);
      ui.clickable(
          chip,
          () -> {
            filter = type;
            limit = 60;
            render();
          });
    }
    filters.addView(chips);
    ui.add(page, filters);
    ui.gap(page, 12);
    ui.add(page, ui.button("＋  Log activity", true, () -> chooseLog(null)));
    ui.gap(page, 14);
    timelineRows = ui.column();
    ui.add(page, timelineRows);
    fillTimeline();
    watch(
        search,
        value -> {
          query = value;
          limit = 60;
          fillTimeline();
        });
  }

  private void fillTimeline() {
    if (timelineRows == null) return;
    timelineRows.removeAllViews();
    List<JSONObject> rows = visibleRows();
    for (JSONObject queued : app.pending()) {
      JSONObject payload = queued.optJSONObject("payload");
      if (payload != null
          && payload.optLong("child") == app.child()
          && app.activities().visible(queued.optString("endpoint"))) {
        JSONObject row = Records.put(Records.copy(payload), "_type", queued.optString("endpoint"));
        Records.put(row, "_pending", SyncFeedback.label(queued));
        rows.add(row);
      }
    }
    rows.sort((a, b) -> Records.time(b).compareTo(Records.time(a)));
    int shown = 0, matching = 0;
    String previous = "";
    for (JSONObject row : rows) {
      if (!filter.equals("all") && !filter.equals(row.optString("_type"))) continue;
      String searchable =
          Records.summary(row, app.units())
              + " "
              + Records.title(row.optString("_type"))
              + " "
              + Records.text(row, "notes")
              + " "
              + row.optString("tags", "");
      if (!searchable
          .toLowerCase(Locale.getDefault())
          .contains(query.toLowerCase(Locale.getDefault()))) continue;
      matching++;
      if (shown >= limit) continue;
      String date = dayLabel(Records.time(row));
      if (!date.equals(previous)) {
        ui.section(timelineRows, date.toUpperCase(Locale.getDefault()));
        previous = date;
      }
      recordCard(timelineRows, row);
      shown++;
    }
    if (shown == 0) {
      LinearLayout c = ui.card(timelineRows, 22);
      ui.add(c, ui.text("No moments here yet", 18, ui.ink, true));
      ui.gap(c, 8);
      ui.add(c, ui.text("Try another filter, or add an activity.", 14, ui.muted, false));
    }
    if (matching > shown)
      ui.add(
          timelineRows,
          ui.button(
              "Show more (" + (matching - shown) + " remaining)",
              false,
              () -> {
                limit += 60;
                fillTimeline();
              }));
  }

  private void recordCard(LinearLayout parent, JSONObject row) {
    String type = row.optString("_type");
    LinearLayout card = ui.card(parent, 15), line = ui.row();
    line.addView(ui.icon(type, 42, true));
    LinearLayout text = ui.column();
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.leftMargin = ui.dp(12);
    line.addView(text, p);
    ui.add(text, ui.text(Records.title(type), 15, ui.ink, true));
    TextView summary = ui.text(Records.summary(row, app.units()), 12, ui.muted, false);
    summary.setMaxLines(2);
    summary.setEllipsize(TextUtils.TruncateAt.END);
    ui.add(text, summary);
    LinearLayout right = ui.column();
    right.setGravity(Gravity.END);
    ui.add(right, ui.text(Records.clock(Records.time(row)), 12, ui.ink, true));
    TextView ago = ui.text(relative(Records.time(row)), 10, ui.muted, false);
    ago.setGravity(Gravity.END);
    ui.add(right, ago);
    line.addView(right);
    ui.add(card, line);
    JSONArray tags = row.optJSONArray("tags");
    if (tags != null && tags.length() > 0) {
      ui.gap(card, 8);
      List<String> names = new ArrayList<>();
      for (int i = 0; i < tags.length(); i++) names.add(tags.optString(i));
      ui.add(card, ui.text("• " + String.join("   • ", names), 11, ui.accent, false));
    }
    if (row.has("_pending")) {
      ui.gap(card, 8);
      ui.add(card, ui.text(row.optString("_pending"), 12, ui.accent, true));
    }
    ui.clickable(
        card,
        () -> {
          if (row.has("_pending")) pending();
          else details(row);
        });
  }

  private void trends() {
    if (app.child() < 0) {
      empty("No child selected", "Add a child on the server, then sync.");
      return;
    }
    ui.add(page, ui.text("The past seven days", 21, ui.ink, true));
    ui.gap(page, 6);
    ui.add(
        page,
        ui.text(
            "A gentle look at your routine. Totals use confirmed entries and this device's time"
                + " zone.",
            13,
            ui.muted,
            false));
    ui.gap(page, 20);
    List<JSONObject> rows = visibleRows();
    for (String type : app.activities().visible()) {
      if (!Arrays.asList("sleep", "feedings", "changes", "tummy-times").contains(type)) continue;
      LinearLayout card = ui.card(page, 20);
      ui.add(
          card,
          ui.text(
              type.equals("feedings")
                  ? "Feedings"
                  : type.equals("changes") ? "Diaper changes" : Records.title(type),
              18,
              ui.ink,
              true));
      double[] values = new double[7];
      double sum = 0;
      for (int i = 0; i < 7; i++) {
        LocalDate day = LocalDate.now().minusDays(6 - i);
        values[i] =
            type.equals("sleep") || type.equals("tummy-times")
                ? total(rows, type, day) / 3600.0
                : count(rows, type, day);
        sum += values[i];
      }
      ui.gap(card, 4);
      ui.add(
          card,
          ui.text(
              (type.equals("sleep") || type.equals("tummy-times")
                      ? Records.duration(Math.round(sum * 3600))
                      : Integer.toString((int) sum) + " entries")
                  + " this week",
              13,
              ui.muted,
              false));
      BarChart chart = new BarChart(values, ui.tone(type));
      chart.setContentDescription(
          Records.title(type)
              + " by day: "
              + Arrays.toString(values)
              + (type.equals("sleep") || type.equals("tummy-times") ? " hours" : " entries"));
      card.addView(chart, new LinearLayout.LayoutParams(-1, ui.dp(145)));
    }
    boolean measurements = false;
    for (String type : app.activities().visible()) {
      if (!Arrays.asList("weight", "height", "head-circumference", "temperature", "bmi")
          .contains(type)) continue;
      for (JSONObject row : rows)
        if (type.equals(row.optString("_type"))) {
          if (!measurements) ui.section(page, "LATEST MEASUREMENTS");
          measurements = true;
          recordCard(page, row);
          break;
        }
    }
  }

  private List<JSONObject> visibleRows() {
    List<JSONObject> rows = Records.timeline(app.data, app.child());
    ActivityOptions options = app.activities();
    rows.removeIf(row -> !options.visible(row.optString("_type")));
    return rows;
  }

  private void settings() {
    LinearLayout connection = ui.card(page, 20);
    ui.add(
        connection,
        ui.text(
            app.demo ? "You're exploring the demo" : "Connected to Baby Buddy", 19, ui.ink, true));
    ui.gap(connection, 8);
    ui.add(
        connection,
        ui.text(
            app.demo
                ? "Sample data is separate from your family's records."
                : app.credentials.server(),
            13,
            ui.muted,
            false));
    ui.gap(connection, 14);
    if (!app.demo) syncStatus(connection);
    Button sync =
        ui.button(
            app.demo ? "Connect my server" : "Sync now",
            true,
            () -> {
              if (app.demo) disconnect();
              else app.sync();
            });
    sync.setEnabled(!app.busy);
    sync.setAlpha(app.busy ? .4f : 1);
    ui.add(connection, sync);
    if (!app.demo) {
      ui.gap(connection, 10);
      List<JSONObject> attention = new ArrayList<>();
      for (JSONObject row : app.pending()) if (SyncFeedback.needsAttention(row)) attention.add(row);
      if (!attention.isEmpty()) {
        ui.add(
            connection,
            ui.text(
                attention.size()
                    + (attention.size() == 1
                        ? " entry needs attention"
                        : " entries need attention"),
                16,
                ui.ink,
                true));
        ui.add(connection, ui.text(SyncFeedback.pending(attention.get(0)), 14));
        ui.gap(connection, 10);
      }
      ui.add(
          connection,
          ui.button("Pending entries (" + app.pending().size() + ")", false, this::pending));
    }
    ui.add(page, ui.button("Report a problem", false, this::diagnostics));
    ui.section(page, "MEASUREMENT LABELS");
    LinearLayout units = ui.card(page, 18);
    ui.add(
        units,
        ui.text(
            "Metric labels are the default. Match the units you use for your server records. Baby"
                + " Buddy stores numbers without units; these settings change labels, not values.",
            13,
            ui.muted,
            false));
    ui.gap(units, 14);
    unit(units, "Volume", "ounces", new String[] {"Milliliters (ml)", "Fluid ounces (fl oz)"});
    unit(units, "Weight", "pounds", new String[] {"Kilograms (kg)", "Pounds (lb)"});
    unit(units, "Length", "inches", new String[] {"Centimeters (cm)", "Inches (in)"});
    unit(units, "Temperature", "fahrenheit", new String[] {"Celsius (°C)", "Fahrenheit (°F)"});
    activitySettings();
    ui.section(page, "SYNC & STORAGE");
    LinearLayout storage = ui.card(page, 18);
    ui.add(
        storage,
        ui.text(
            "Records are available offline after syncing. New entries wait on this device until"
                + " they can be sent. Sync runs while the app is open, when returning to it, and"
                + " when you tap Sync.",
            14,
            ui.muted,
            false));
    ui.section(page, "ABOUT");
    LinearLayout about = ui.card(page, 18);
    String version = "";
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
    }
    ui.add(about, ui.text(getString(R.string.app_name) + " " + version, 16, ui.ink, true));
    ui.gap(about, 8);
    ui.add(
        about,
        ui.text(
            "An independent Android companion for Baby Buddy. No ads, analytics, or extra cloud"
                + " service. Your token is protected by Android Keystore. Local records stay in"
                + " app-private storage; Android backup is disabled.",
            13,
            ui.muted,
            false));
    ui.gap(about, 10);
    ui.add(
        about,
        ui.text(
            "Manage existing records, children, and photos on your Baby Buddy server. This version"
                + " creates entries and reads shared data.",
            13,
            ui.muted,
            false));
    ui.add(
        page,
        ui.button(
            app.demo ? "Leave demo" : "Disconnect & clear local data", false, this::disconnect));
  }

  private void unit(LinearLayout parent, String title, String key, String[] choices) {
    LinearLayout row = ui.row();
    row.addView(ui.text(title, 14), new LinearLayout.LayoutParams(0, -2, 1));
    Button b =
        ui.button(
            choices[app.preferences.getBoolean(key, false) ? 1 : 0],
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle(title + " labels")
                    .setSingleChoiceItems(
                        choices,
                        app.preferences.getBoolean(key, false) ? 1 : 0,
                        (d, n) -> {
                          app.preferences.edit().putBoolean(key, n == 1).apply();
                          d.dismiss();
                          render();
                        })
                    .setNegativeButton("Cancel", null)
                    .show());
    row.addView(b);
    ui.add(parent, row);
    ui.gap(parent, 10);
  }

  private void navigation() {
    LinearLayout nav = ui.row();
    nav.setBackground(ui.shape(ui.surface, 28));
    nav.setPadding(ui.dp(5), ui.dp(6), ui.dp(5), ui.dp(6));
    String[] ids = {"today", "timeline", "trends", "settings"},
        names = {"Today", "Timeline", "Trends", "Settings"};
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout button = ui.column();
      button.setPadding(0, ui.dp(7), 0, ui.dp(7));
      button.setGravity(Gravity.CENTER);
      button.addView(ui.icon(ids[i], 24, false));
      TextView t = ui.text(names[i], 11, i == tab ? ui.accent : ui.muted, i == tab);
      t.setGravity(Gravity.CENTER);
      t.setSingleLine();
      t.setEllipsize(TextUtils.TruncateAt.END);
      ui.add(button, t);
      if (i == tab) button.setBackground(ui.shape(ui.soft, 22));
      button.setContentDescription(names[i]);
      ui.clickable(button, () -> navigate(index));
      nav.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(ui.dp(14), ui.dp(5), ui.dp(14), ui.dp(8));
    root.addView(nav, p);
  }

  private void navigate(int destination) {
    choosingActivity = false;
    finishingTimer = null;
    tab = destination;
    if (scroll != null) scroll.scrollTo(0, 0);
    render();
  }

  private void chooseChild() {
    JSONArray children = app.data.optJSONArray("children");
    if (children == null || children.length() == 0) {
      message("No children yet", "Add a child on your Baby Buddy server, then sync.");
      return;
    }
    String[] names = new String[children.length()];
    for (int i = 0; i < children.length(); i++)
      names[i] =
          Records.text(children.optJSONObject(i), "first_name")
              + " "
              + Records.text(children.optJSONObject(i), "last_name");
    new AlertDialog.Builder(this)
        .setTitle("Your little ones")
        .setItems(names, (d, n) -> app.selectChild(children.optJSONObject(n).optLong("id")))
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void chooseLog(Long timer) {
    if (app.child() < 0) {
      message("Add a child first", "Create a child on your server, then sync.");
      return;
    }
    choosingActivity = true;
    finishingTimer = timer;
    scroll.scrollTo(0, 0);
    render();
  }

  @Override
  // Android 13+ uses the platform OnBackInvoked callback registered in onCreate.
  @android.annotation.SuppressLint("GestureBackNavigation")
  public void onBackPressed() {
    if (choosingActivity) navigate(tab);
    else super.onBackPressed();
  }

  private void activityGrid() {
    ui.add(page, ui.button("‹  Back", false, () -> navigate(tab)));
    ui.gap(page, 16);
    ui.add(
        page,
        ui.text(finishingTimer == null ? "Log an activity" : "Finish timer as…", 26, ui.ink, true));
    ui.gap(page, 6);
    ui.add(
        page, ui.text("Choose your activities and their order in Settings.", 13, ui.muted, false));
    ui.gap(page, 20);
    int count = 0;
    LinearLayout row = null;
    for (String type : app.activities().visible()) {
      if (app.schema(type) == null || (finishingTimer != null && !Records.timed(type))) continue;
      if (count++ % 2 == 0) {
        row = ui.row();
        ui.add(page, row);
        ui.gap(page, 12);
      }
      LinearLayout card = ui.column();
      card.setGravity(Gravity.CENTER);
      card.setPadding(ui.dp(12), ui.dp(20), ui.dp(12), ui.dp(20));
      card.setMinimumHeight(ui.dp(128));
      card.setBackground(ui.shape(ui.surface, 22));
      card.addView(ui.icon(type, 42, true));
      ui.gap(card, 12);
      TextView title = ui.text(Records.title(type), 15, ui.ink, true);
      title.setGravity(Gravity.CENTER);
      ui.add(card, title);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
      if (row.getChildCount() > 0) params.leftMargin = ui.dp(12);
      row.addView(card, params);
      card.setContentDescription("Log " + Records.title(type));
      ui.clickable(card, () -> log(type, finishingTimer));
    }
    if (count == 0)
      empty(
          "No activities to show",
          "Enable activities in Settings. If none are writable, sync with a user who can add"
              + " records.");
    else if (count % 2 != 0) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
  }

  private void activitySettings() {
    ui.section(page, "YOUR ACTIVITIES");
    ui.add(
        page,
        ui.text(
            "Uncheck activities to hide their logs and summaries. Move your favorites up. Server"
                + " records stay intact.",
            13,
            ui.muted,
            false));
    ui.gap(page, 12);
    ActivityOptions options = app.activities();
    List<String> types = options.ordered();
    for (int i = 0; i < types.size(); i++) {
      String type = types.get(i);
      LinearLayout card = ui.card(page, 12);
      CheckBox visible = new CheckBox(this);
      visible.setText(Records.title(type));
      visible.setTextColor(ui.ink);
      visible.setTextSize(16);
      visible.setMinHeight(ui.dp(48));
      visible.setChecked(options.visible(type));
      visible.setOnCheckedChangeListener((button, checked) -> options.visible(type, checked));
      LinearLayout row = ui.row(), controls = ui.column();
      row.addView(visible, new LinearLayout.LayoutParams(0, -2, 1));
      LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(0, -2, 1);
      position.leftMargin = ui.dp(12);
      row.addView(controls, position);
      Button up =
          ui.button(
              "↑  Move up",
              false,
              () -> {
                options.move(type, -1);
                render();
              });
      Button down =
          ui.button(
              "↓  Move down",
              false,
              () -> {
                options.move(type, 1);
                render();
              });
      up.setContentDescription("Move " + Records.title(type) + " up");
      down.setContentDescription("Move " + Records.title(type) + " down");
      up.setEnabled(i > 0);
      down.setEnabled(i < types.size() - 1);
      up.setAlpha(up.isEnabled() ? 1 : .35f);
      down.setAlpha(down.isEnabled() ? 1 : .35f);
      ui.add(controls, up);
      ui.gap(controls, 6);
      ui.add(controls, down);
      ui.add(card, row);
    }
  }

  private void log(String endpoint, Long timer) {
    if (app.child() < 0) {
      message("Add a child first", "Create a child on your server, then sync.");
      return;
    }
    form.show(endpoint, timer, null);
  }

  private void details(JSONObject row) {
    StringBuilder body = new StringBuilder();
    body.append(dayLabel(Records.time(row)))
        .append(" · ")
        .append(Records.clock(Records.time(row)))
        .append("\n\n")
        .append(Records.summary(row, app.units()));
    Iterator<String> keys = row.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (key.startsWith("_")
          || key.equals("child")
          || row.isNull(key)
          || Records.text(row, key).isEmpty()) continue;
      body.append("\n\n")
          .append(Character.toUpperCase(key.charAt(0)))
          .append(key.substring(1).replace('_', ' '))
          .append(": ")
          .append(row.opt(key));
    }
    TextView text = ui.text(body.toString(), 15);
    text.setTextIsSelectable(true);
    text.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(22));
    ScrollView view = new ScrollView(this);
    view.addView(text);
    new AlertDialog.Builder(this)
        .setTitle(Records.title(row.optString("_type")))
        .setView(view)
        .setPositiveButton("Done", null)
        .show();
  }

  private void diagnostics() {
    String version;
    try {
      android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
      version = info.versionName + " (" + info.versionCode + ")";
    } catch (Exception e) {
      version = "unknown";
    }
    android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
    String header =
        "Baby Buddy Pocket "
            + version
            + "\nAndroid "
            + Build.VERSION.RELEASE
            + " / API "
            + Build.VERSION.SDK_INT
            + "\nDevice: "
            + Build.MANUFACTURER
            + " "
            + Build.MODEL
            + "\nDisplay: "
            + metrics.widthPixels
            + "x"
            + metrics.heightPixels
            + " / "
            + metrics.densityDpi
            + " dpi; font "
            + getResources().getConfiguration().fontScale
            + "; dark="
            + ui.dark
            + "\n\n";
    app.diagnosticReport(
        body -> {
          if (isFinishing() || isDestroyed()) return;
          String report = header + body;
          LinearLayout content = ui.column();
          content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(16));
          ui.add(
              content,
              ui.text(
                  "Technical details stay on this phone until you share them. Review below. Include"
                      + " what you expected and the steps that caused the problem. GitHub issues"
                      + " are public.",
                  14));
          TextView text = ui.text(report, 12);
          text.setTextIsSelectable(true);
          ui.add(content, text);
          ui.add(
              content,
              ui.button(
                  "Copy report",
                  true,
                  () -> {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(
                            ClipData.newPlainText("Baby Buddy Pocket diagnostics", report));
                    Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
                  }));
          ui.add(
              content,
              ui.button(
                  "Share report",
                  false,
                  () -> {
                    Intent send =
                        new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Baby Buddy Pocket problem report")
                            .putExtra(Intent.EXTRA_TEXT, report);
                    try {
                      startActivity(Intent.createChooser(send, "Share report"));
                    } catch (ActivityNotFoundException e) {
                      message("No sharing app", "Use Copy report instead.");
                    }
                  }));
          ui.add(
              content,
              ui.button(
                  "Open GitHub issues",
                  false,
                  () -> {
                    try {
                      startActivity(
                          new Intent(
                              Intent.ACTION_VIEW,
                              android.net.Uri.parse(
                                  "https://github.com/Felixre7/Baby-Buddy-Pocket/issues")));
                    } catch (ActivityNotFoundException e) {
                      message(
                          "No browser",
                          "Visit github.com/Felixre7/Baby-Buddy-Pocket/issues in a browser.");
                    }
                  }));
          ScrollView reportScroll = new ScrollView(this);
          reportScroll.addView(content);
          AlertDialog dialog =
              new AlertDialog.Builder(this)
                  .setTitle("Report a problem")
                  .setView(reportScroll)
                  .create();
          ui.add(
              content,
              ui.button(
                  "Clear diagnostics",
                  false,
                  () -> {
                    app.clearDiagnostics();
                    dialog.dismiss();
                  }));
          ui.add(content, ui.button("Close report", false, dialog::dismiss));
          dialog.show();
        });
  }

  private void pending() {
    List<JSONObject> rows = app.pending();
    if (rows.isEmpty()) {
      message("All caught up", "There are no pending entries on this device.");
      return;
    }
    String[] names = new String[rows.size()];
    for (int i = 0; i < rows.size(); i++)
      names[i] =
          (rows.get(i).optString("method").equals("DELETE")
                  ? "Timer cleanup"
                  : Records.title(rows.get(i).optString("endpoint")))
              + " · "
              + SyncFeedback.label(rows.get(i));
    new AlertDialog.Builder(this)
        .setTitle("Pending entries · tap for details")
        .setItems(names, (d, n) -> pendingDetail(rows.get(n)))
        .setNegativeButton("Close", null)
        .show();
  }

  private void pendingDetail(JSONObject row) {
    long id = row.optLong("local_id");
    JSONObject payload =
        Records.put(Records.copy(row.optJSONObject("payload")), "_type", row.optString("endpoint"));
    boolean cleanup = row.optString("method").equals("DELETE");
    LinearLayout content = ui.column();
    content.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(20));
    ui.add(content, ui.text(SyncFeedback.pending(row), 16));
    ui.gap(content, 16);
    ui.add(content, ui.text(Records.summary(payload, app.units()), 14));
    if (payload.has("start") || payload.has("time") || payload.has("date")) {
      Instant start = Records.time(payload);
      ui.add(content, ui.text(dayLabel(start) + " · " + Records.clock(start), 14));
      if (payload.has("end")) {
        Instant end = Records.instant(payload.optString("end"));
        ui.add(content, ui.text("Until " + dayLabel(end) + " · " + Records.clock(end), 14));
      }
    }
    ui.gap(content, 16);
    ui.add(
        content,
        ui.text(
            cleanup
                ? "Deleting this pending entry leaves the timer on the server. Any saved activity"
                    + " is kept."
                : "Delete pending entry removes only this device's unsynced copy. Server records"
                    + " stay intact.",
            14,
            ui.muted,
            false));
    ScrollView view = new ScrollView(this);
    view.addView(content);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(Records.title(row.optString("endpoint")) + " · " + SyncFeedback.label(row))
            .setView(view)
            .create();
    if (SyncFeedback.canEdit(row) && app.schema(row.optString("endpoint")) != null) {
      ui.add(
          content,
          ui.button(
              "Edit activity",
              true,
              () -> {
                if (app.busy) {
                  message("Sync in progress", "Wait until syncing finishes.");
                  return;
                }
                Bundle draft = new Bundle();
                draft.putLong("pendingEntry", id);
                draft.putLong("child", payload.optLong("child"));
                draft.putBoolean("startingTimer", false);
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
                dialog.dismiss();
                form.show(row.optString("endpoint"), null, draft);
              }));
    }
    ui.add(
        content,
        ui.button(
            "Review retry",
            false,
            () -> {
              dialog.dismiss();
              new AlertDialog.Builder(this)
                  .setTitle("Retry this entry?")
                  .setMessage(
                      cleanup
                          ? "Retry removing this timer? Any already saved activity is kept."
                          : "Confirm that the entry is missing from your server. If it is already"
                              + " there, delete the pending copy instead.")
                  .setPositiveButton(
                      cleanup ? "Retry cleanup" : "Entry is missing - retry",
                      (a, b) -> {
                        if (!app.busy) {
                          try {
                            app.store.state(id, "queued", "Waiting to sync");
                            app.sync();
                            render();
                          } catch (Exception e) {
                            app.log(DiagnosticLog.Event.LOCAL_FAILURE, e);
                            message("Could not retry", SyncEngine.friendly(e));
                          }
                        } else
                          message(
                              "Sync in progress",
                              "Wait until syncing finishes before changing pending entries.");
                      })
                  .setNegativeButton("Cancel", null)
                  .show();
            }));
    ui.add(
        content,
        ui.button(
            "Delete pending entry",
            false,
            () -> {
              dialog.dismiss();
              new AlertDialog.Builder(this)
                  .setTitle("Delete pending entry?")
                  .setMessage(
                      "This removes the unsynced copy from this device. It does not delete a server"
                          + " record."
                          + (cleanup || row.has("cleanup_timer")
                              ? " The shared timer stays on the server."
                              : ""))
                  .setPositiveButton(
                      "Delete pending copy",
                      (a, b) -> {
                        if (!app.busy) {
                          try {
                            app.store.discard(id);
                            render();
                          } catch (Exception e) {
                            app.log(DiagnosticLog.Event.LOCAL_FAILURE, e);
                            message("Could not delete", SyncEngine.friendly(e));
                          }
                        } else message("Sync in progress", "Wait until syncing finishes.");
                      })
                  .setNegativeButton("Keep", null)
                  .show();
            }));
    ui.add(content, ui.button("Close", false, dialog::dismiss));
    dialog.show();
  }

  private void disconnect() {
    if (app.busy) {
      message("Sync in progress", "Wait until syncing finishes before disconnecting.");
      return;
    }
    if (app.demo) {
      app.disconnect();
      tab = 0;
      render();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Disconnect this device?")
        .setMessage(
            "Remove the saved token, downloaded records, preferences, and "
                + app.pending().size()
                + " pending entries, plus "
                + app.timers().size()
                + " saved timer setups from this device? Shared timers and server records stay on"
                + " Baby Buddy.")
        .setPositiveButton(
            "Disconnect",
            (d, w) -> {
              if (!app.busy) {
                app.disconnect();
                tokenDraft = "";
                serverDraft = "";
                tab = 0;
                render();
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void message(String title, String body) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(body)
        .setPositiveButton("OK", null)
        .show();
  }

  private void empty(String title, String description) {
    LinearLayout c = ui.card(page, 24);
    ui.add(c, ui.text(title, 20, ui.ink, true));
    ui.gap(c, 8);
    ui.add(c, ui.text(description, 14, ui.muted, false));
  }

  private interface TextChanged {
    void accept(String text);
  }

  private void watch(EditText edit, TextChanged callback) {
    edit.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.accept(s.toString());
          }

          public void afterTextChanged(Editable value) {}
        });
  }

  private static String relative(Instant instant) {
    long seconds = Duration.between(instant, Instant.now()).getSeconds();
    if (seconds < 60) return "just now";
    if (seconds < 3600) return (seconds / 60) + "m ago";
    if (seconds < 86400) return (seconds / 3600) + "h ago";
    return (seconds / 86400) + "d ago";
  }

  private static String dayLabel(Instant time) {
    LocalDate day = time.atZone(ZoneId.systemDefault()).toLocalDate();
    if (day.equals(LocalDate.now())) return "Today";
    if (day.equals(LocalDate.now().minusDays(1))) return "Yesterday";
    return day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()));
  }

  private static String last(List<JSONObject> rows, String type) {
    for (JSONObject row : rows)
      if (type.equals(row.optString("_type"))) return "Last " + relative(Records.time(row));
    return "Nothing logged yet";
  }

  private static int count(List<JSONObject> rows, String type, LocalDate day) {
    int n = 0;
    for (JSONObject row : rows)
      if (type.equals(row.optString("_type"))
          && Records.time(row).atZone(ZoneId.systemDefault()).toLocalDate().equals(day)) n++;
    return n;
  }

  private static long total(List<JSONObject> rows, String type, LocalDate day) {
    long total = 0;
    for (JSONObject row : rows)
      if (type.equals(row.optString("_type")))
        total += Records.overlap(row, day, ZoneId.systemDefault());
    return total;
  }

  private final class BarChart extends View {
    private final double[] values;
    private final int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    BarChart(double[] values, int color) {
      super(MainActivity.this);
      this.values = values;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float w = getWidth(), h = getHeight(), cell = w / 7, baseline = h - ui.dp(25);
      double max = 1;
      for (double value : values) max = Math.max(max, value);
      for (int i = 0; i < 7; i++) {
        float x = cell * i + cell / 2, bar = (float) (values[i] / max) * (baseline - ui.dp(28));
        paint.setColor(color);
        paint.setAlpha(i == 6 ? 255 : 140);
        canvas.drawRoundRect(
            x - cell * .26f,
            baseline - Math.max(ui.dp(3), bar),
            x + cell * .26f,
            baseline,
            ui.dp(5),
            ui.dp(5),
            paint);
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(ui.dp(10));
        paint.setColor(ui.muted);
        canvas.drawText(
            LocalDate.now()
                .minusDays(6 - i)
                .format(DateTimeFormatter.ofPattern("EE", Locale.getDefault())),
            x,
            h - ui.dp(5),
            paint);
        String value =
            values[i] == Math.floor(values[i])
                ? Integer.toString((int) values[i])
                : String.format(Locale.getDefault(), "%.1f", values[i]);
        canvas.drawText(value, x, baseline - bar - ui.dp(7), paint);
      }
    }
  }
}
