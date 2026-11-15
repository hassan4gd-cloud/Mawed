package com.mawid.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.List;

/**
 * جدولة منبّهين لكل موعد:
 *  1) منبّه التذكير: قبل الموعد بـ remindMinutes دقيقة → إشعار.
 *  2) منبّه الانتهاء: عند وقت الموعد → حذف الموعد تلقائياً من التطبيق.
 */
public final class AlarmScheduler {

    public static final String ACTION_REMIND = "com.mawid.app.action.REMIND";
    public static final String ACTION_EXPIRE = "com.mawid.app.action.EXPIRE";
    public static final String EXTRA_ID = "appointment_id";

    private AlarmScheduler() { }

    public static void schedule(Context c, Appointment a) {
        cancelAlarms(c, a.id);
        long now = System.currentTimeMillis();

        if (a.timeMillis <= now) {
            AppointmentStore.delete(c, a.id);
            return;
        }

        if (!a.reminded) {
            if (a.remindAtMillis() > now) {
                setAlarm(c, a.remindAtMillis(), pendingIntent(c, a.id, ACTION_REMIND));
            } else {
                // الموعد أقرب من مدة التذكير: نذكّر فوراً
                a.reminded = true;
                AppointmentStore.save(c, a);
                NotificationHelper.showReminder(c, a);
            }
        }
        setAlarm(c, a.timeMillis, pendingIntent(c, a.id, ACTION_EXPIRE));
    }

    /** إلغاء المنبّهات والإشعار الخاص بموعد. */
    public static void cancel(Context c, int id) {
        cancelAlarms(c, id);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(id);
    }

    /** يُستدعى بعد إعادة تشغيل الهاتف أو تغيير الوقت. */
    public static void rescheduleAll(Context c) {
        AppointmentStore.removeExpired(c, System.currentTimeMillis());
        List<Appointment> all = AppointmentStore.getAll(c);
        for (Appointment a : all) schedule(c, a);
    }

    // ------------------------------------------------------------------

    private static void cancelAlarms(Context c, int id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(pendingIntent(c, id, ACTION_REMIND));
        am.cancel(pendingIntent(c, id, ACTION_EXPIRE));
    }

    private static PendingIntent pendingIntent(Context c, int id, String action) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(action)
                .setData(Uri.parse("mawid://appointment/" + id))
                .putExtra(EXTRA_ID, id);
        int requestCode = id * 2 + (ACTION_REMIND.equals(action) ? 0 : 1);
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAlarm(Context c, long atMillis, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        try {
            if (canExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
        }
    }

    public static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }
}
