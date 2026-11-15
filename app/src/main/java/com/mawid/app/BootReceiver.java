package com.mawid.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يعيد جدولة التذكيرات بعد إعادة تشغيل الهاتف أو تحديث التطبيق أو تغيير الوقت. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AlarmScheduler.rescheduleAll(context.getApplicationContext());
            } finally {
                result.finish();
            }
        }).start();
    }
}
