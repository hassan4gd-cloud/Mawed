package com.mawid.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "appointment_reminders";

    private NotificationHelper() { }

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "تذكير المواعيد",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("إشعار قبل موعدك بعدة دقائق");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 200, 400});
        ch.enableLights(true);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        nm.createNotificationChannel(ch);
    }

    /** Android 14+: هل يُسمح بعرض التنبيه بملء الشاشة؟ */
    public static boolean canFullScreen(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.canUseFullScreenIntent();
    }

    public static boolean canPost(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    public static void showReminder(Context c, Appointment a) {
        ensureChannel(c);
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        long now = System.currentTimeMillis();
        long mins = Math.max(0, Math.round((a.timeMillis - now) / 60000.0));
        String when = mins <= 0 ? "الآن" : "بعد " + Fmt.duration(mins);
        String text = when + " — الساعة " + Fmt.time(a.timeMillis);

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, a.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String big = text;
        if (a.note != null && !a.note.trim().isEmpty()) big = text + "\n\n" + a.note.trim();

        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(a.title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setColor(Ui.ACCENT)
                .setWhen(a.timeMillis)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(a.id, n);
    }
}
