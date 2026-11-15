package com.mawid.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يستقبل منبّهات التذكير والانتهاء. */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0);
        if (id == 0) return;
        Appointment a = AppointmentStore.get(context, id);
        if (a == null) return;

        if (AlarmScheduler.ACTION_REMIND.equals(intent.getAction())) {
            a.reminded = true;
            AppointmentStore.save(context, a);
            NotificationHelper.showReminder(context, a);
        } else if (AlarmScheduler.ACTION_EXPIRE.equals(intent.getAction())) {
            // انتهى وقت الموعد: يُحذف من التطبيق تلقائياً
            AppointmentStore.delete(context, id);
        }
    }
}
