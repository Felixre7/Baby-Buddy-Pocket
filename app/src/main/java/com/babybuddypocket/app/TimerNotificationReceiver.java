package com.babybuddypocket.app;

import android.content.*;

/** Restore saved timer notifications and remember dismissal, without making network calls. */
public final class TimerNotificationReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (TimerNotifications.DISMISS.equals(intent.getAction())) {
      AppController.get(context).dismissTimerNotification(intent.getStringExtra("timer"));
      return;
    }
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
      AppController.get(context).refreshNotifications();
  }
}
