package com.babybuddypocket.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import java.util.*;
import org.json.*;

/** Android renders elapsed time; no ticking app service or network work is needed. */
final class TimerNotifications {
  static final String CHANNEL = "running-timers";
  static final String OPEN = "com.babybuddypocket.app.OPEN_TIMER";
  static final String DISMISS = "com.babybuddypocket.app.DISMISS_TIMER_NOTIFICATION";
  // Public framework extra; its named constant/Builder setter arrived after compile SDK 36.
  static final String REQUEST_PROMOTION = "android.requestPromotedOngoing";
  private static final String DISMISSED = "dismissed_timer_promotions";
  private static final String TAG = "timer:";
  private final SharedPreferences preferences;
  private final Context context;
  private final NotificationManager manager;

  TimerNotifications(Context context) {
    this.context = context.getApplicationContext();
    preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE);
    manager = context.getSystemService(NotificationManager.class);
    NotificationChannel channel =
        new NotificationChannel(CHANNEL, "Running timers", NotificationManager.IMPORTANCE_LOW);
    channel.setDescription("Elapsed time for running activity timers");
    channel.setSound(null, null);
    channel.enableVibration(false);
    manager.createNotificationChannel(channel);
  }

  static Map<String, JSONObject> running(
      JSONObject data, List<JSONObject> local, List<JSONObject> pending) {
    Map<String, JSONObject> result = new LinkedHashMap<>();
    for (JSONObject timer : local) {
      if (!TimerPolicy.runningLocal(timer, pending)) continue;
      JSONObject payload = Records.copy(timer.optJSONObject("payload"));
      Records.put(payload, "name", Records.title(timer.optString("endpoint")));
      result.put(notificationKey(timer), payload);
    }
    JSONArray shared = data.optJSONArray("timers");
    if (shared != null)
      for (int i = 0; i < shared.length(); i++) {
        JSONObject timer = shared.optJSONObject(i);
        if (timer == null || TimerPolicy.ending(pending, timer.optLong("id"))) continue;
        result.putIfAbsent("server:" + timer.optLong("id"), timer);
      }
    return result;
  }

  private static String notificationKey(JSONObject timer) {
    return timer.has("server_id")
        ? "server:" + timer.optLong("server_id")
        : "local:" + timer.optLong("id");
  }

  private static String promotionKey(JSONObject timer, boolean demo) {
    return (demo ? "demo:" : "real:")
        + timer.optLong("id")
        + ":"
        + Records.time(timer.optJSONObject("payload")).toEpochMilli();
  }

  void dismiss(String key, List<JSONObject> local, List<JSONObject> pending, boolean demo) {
    if (key == null) return;
    for (JSONObject timer : local) {
      if (!TimerPolicy.runningLocal(timer, pending) || !key.equals(promotionKey(timer, demo)))
        continue;
      Set<String> dismissed =
          new HashSet<>(preferences.getStringSet(DISMISSED, Collections.emptySet()));
      dismissed.add(key);
      if (!preferences.edit().putStringSet(DISMISSED, dismissed).commit())
        throw new IllegalStateException("Could not save notification dismissal");
      return;
    }
  }

  void update(JSONObject data, List<JSONObject> local, List<JSONObject> pending, boolean demo) {
    Map<String, JSONObject> running = running(data, local, pending);
    Map<String, String> localKeys = new HashMap<>();
    for (JSONObject timer : local)
      if (TimerPolicy.runningLocal(timer, pending))
        localKeys.put(notificationKey(timer), promotionKey(timer, demo));
    Set<String> dismissed =
        new HashSet<>(preferences.getStringSet(DISMISSED, Collections.emptySet()));
    if (dismissed.retainAll(localKeys.values()))
      preferences.edit().putStringSet(DISMISSED, dismissed).apply();
    boolean canPromote = Build.VERSION.SDK_INT >= 36 && manager.canPostPromotedNotifications();
    Set<String> wanted = new HashSet<>();
    for (Map.Entry<String, JSONObject> entry : running.entrySet()) {
      JSONObject timer = entry.getValue();
      String tag = TAG + entry.getKey();
      wanted.add(tag);
      if (!manager.areNotificationsEnabled()) continue;
      long child = timer.optLong("child", -1);
      String childName = "";
      JSONArray children = data.optJSONArray("children");
      if (children != null)
        for (int i = 0; i < children.length(); i++) {
          JSONObject row = children.optJSONObject(i);
          if (row != null && row.optLong("id") == child) {
            childName = row.optString("first_name").trim();
            break;
          }
        }
      String name = timer.optString("name", "Timer").trim();
      if (name.isEmpty()) name = "Timer";
      String title =
          (demo ? "Demo · " : "") + name + (childName.isEmpty() ? "" : " · " + childName);
      Intent open =
          new Intent(context, MainActivity.class)
              .setAction(OPEN)
              .setData(android.net.Uri.parse("babybuddypocket://timer/" + entry.getKey()))
              .putExtra("child", child)
              .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      PendingIntent tap =
          PendingIntent.getActivity(
              context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Notification publicView =
          new Notification.Builder(context, CHANNEL)
              .setSmallIcon(R.drawable.ic_timer_notification)
              .setContentTitle("Activity timer running")
              .setContentText("Open Baby Buddy Pocket to view")
              .build();
      Notification.Builder builder =
          new Notification.Builder(context, CHANNEL)
              .setSmallIcon(R.drawable.ic_timer_notification)
              .setColor(Color.rgb(66, 119, 93))
              .setContentTitle(title)
              .setContentText("Timer running · tap to open")
              .setWhen(Records.time(timer).toEpochMilli())
              .setShowWhen(true)
              .setUsesChronometer(true)
              .setOngoing(true)
              .setOnlyAlertOnce(true)
              .setCategory(Notification.CATEGORY_PROGRESS)
              .setVisibility(Notification.VISIBILITY_PRIVATE)
              .setPublicVersion(publicView)
              .setContentIntent(tap);
      String localKey = localKeys.get(entry.getKey());
      if (Build.VERSION.SDK_INT >= 36 && localKey != null) {
        Bundle extras = new Bundle();
        extras.putBoolean(REQUEST_PROMOTION, canPromote && !dismissed.contains(localKey));
        builder.addExtras(extras);
        Intent dismiss =
            new Intent(context, TimerNotificationReceiver.class)
                .setAction(DISMISS)
                .setData(android.net.Uri.parse("babybuddypocket://dismiss/" + localKey))
                .putExtra("timer", localKey);
        builder.setDeleteIntent(
            PendingIntent.getBroadcast(
                context,
                0,
                dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
      }
      Notification notification = builder.build();
      try {
        manager.notify(tag, 1, notification);
      } catch (SecurityException ignored) {
        // Permission can be revoked between checking it and posting. The timer stays saved.
      }
    }
    for (StatusBarNotification notification : manager.getActiveNotifications())
      if (notification.getTag() != null
          && notification.getTag().startsWith(TAG)
          && !wanted.contains(notification.getTag()))
        manager.cancel(notification.getTag(), notification.getId());
  }
}
