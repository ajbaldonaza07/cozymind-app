package com.example.cozymind;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Shows the gentle daily check-in reminder, and re-schedules it after the phone restarts. */
public class ReminderReceiver extends BroadcastReceiver {

    static final String ACTION_REMIND = "com.example.cozymind.ACTION_REMIND";
    private static final String CHANNEL_ID = "daily_checkin";
    private static final int NOTIFICATION_ID = 77;

    private static final String[] MESSAGES = {
            "Time for a 1-minute check-in 💚",
            "How are you feeling right now? Take a slow breath with Cozymind.",
            "A small pause can change your whole evening. Open Cozymind 🌿",
            "Have you had some water today? 💧 Check in with yourself.",
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WellnessStore.scheduleReminder(context);
            return;
        }
        if (!ACTION_REMIND.equals(action) || !WellnessStore.reminderOn(context)) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Daily check-in",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("A gentle once-a-day reminder to check in with yourself.");
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        Intent open = new Intent(context, CalmStartActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String message = MESSAGES[(int) ((System.currentTimeMillis() / 86_400_000L) % MESSAGES.length)];
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Cozymind")
                .setContentText(message)
                .setContentIntent(tap)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
