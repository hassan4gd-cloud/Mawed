package com.mawid.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

/**
 * خدمة الرنين: تشغّل نغمة المنبّه بشكل متكرر مع اهتزاز، وتعرض شاشة التنبيه
 * (مثل المنبّه أو المكالمة الواردة) حتى يضغط المستخدم «إيقاف».
 */
public class AlarmService extends Service {

    public static final String ACTION_START = "com.mawid.app.action.RING_START";
    public static final String ACTION_STOP = "com.mawid.app.action.RING_STOP";

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TIME = "time";

    static final String RING_CHANNEL_ID = "appointment_alarm_ring";
    private static final int NOTIF_ID = 777_000;
    /** يتوقف الرنين وحده بعد هذه المدة إن لم يضغط المستخدم إيقاف. */
    private static final long AUTO_STOP_MS = 5 * 60_000L;

    /** واجهة شاشة التنبيه تُغلق نفسها عند توقف الرنين. */
    interface UiListener {
        void onRingStopped();
    }

    private static volatile UiListener uiListener;
    private static volatile boolean ringing;
    private static volatile int ringingId;
    private static volatile String ringingTitle = "";
    private static volatile long ringingTime;

    private MediaPlayer player;
    private Vibrator vibrator;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = () -> stopRinging(true);

    // ------------------------------------------------------------------
    // واجهة الاستخدام من باقي التطبيق
    // ------------------------------------------------------------------

    /** يبدأ الرنين لموعد. يرجع false إذا منع النظام بدء الخدمة. */
    public static boolean start(Context c, int id, String title, long timeMillis) {
        Intent i = new Intent(c, AlarmService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TIME, timeMillis);
        try {
            c.startForegroundService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void stop(Context c) {
        if (!ringing) return;
        try {
            c.startService(new Intent(c, AlarmService.class).setAction(ACTION_STOP));
        } catch (Exception ignored) {
            // الخدمة متوقفة أصلاً
        }
    }

    static void setUiListener(UiListener l) {
        uiListener = l;
    }

    static boolean isRinging() {
        return ringing;
    }

    static String currentTitle() {
        return ringingTitle;
    }

    static long currentTime() {
        return ringingTime;
    }

    static int currentId() {
        return ringingId;
    }

    // ------------------------------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action) || intent == null) {
            stopRinging(false);
            return START_NOT_STICKY;
        }

        int id = intent.getIntExtra(EXTRA_ID, 0);
        String title = intent.getStringExtra(EXTRA_TITLE);
        long time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis());
        if (title == null || title.isEmpty()) title = "موعد";

        // في حال كان هناك رنين سابق: نوقف الصوت ونبدأ الجديد
        releaseSound();
        ringingId = id;
        ringingTitle = title;
        ringingTime = time;
        ringing = true;

        Notification n = buildNotification(title, time);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        acquireWakeLock();
        startSound();
        startVibration();

        // إظهار شاشة التنبيه (إن سمح النظام) — وإلا تبقى الشاشة متاحة من الإشعار
        try {
            startActivity(alarmScreenIntent());
        } catch (Exception ignored) {
            // يُعرض عبر fullScreenIntent في الإشعار
        }

        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, AUTO_STOP_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        releaseSound();
        releaseWakeLock();
        ringing = false;
        super.onDestroy();
    }

    // ------------------------------------------------------------------

    private void stopRinging(boolean timedOut) {
        handler.removeCallbacks(autoStop);
        boolean wasRinging = ringing;
        int id = ringingId;
        String title = ringingTitle;
        long time = ringingTime;

        releaseSound();
        releaseWakeLock();
        ringing = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();

        UiListener l = uiListener;
        if (l != null) l.onRingStopped();

        if (timedOut && wasRinging && id != 0) {
            // لم يُوقف المستخدم الرنين: نترك إشعاراً عادياً حتى لا يفوته الموعد
            Appointment a = AppointmentStore.get(this, id);
            if (a == null) {
                a = new Appointment();
                a.id = id;
                a.title = title;
                a.timeMillis = time;
            }
            NotificationHelper.showReminder(this, a);
        }
    }

    private Intent alarmScreenIntent() {
        return new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    }

    private Notification buildNotification(String title, long time) {
        ensureRingChannel(this);

        PendingIntent fullScreen = PendingIntent.getActivity(this, 1, alarmScreenIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, AlarmService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long mins = Math.max(0, Math.round((time - System.currentTimeMillis()) / 60000.0));
        String text = (mins <= 0 ? "الآن" : "بعد " + Fmt.duration(mins)) + " — الساعة " + Fmt.time(time);

        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notify), "إيقاف", stopPi).build();

        return new Notification.Builder(this, RING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Ui.ACCENT)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .setDeleteIntent(stopPi)
                .addAction(stopAction)
                .build();
    }

    static void ensureRingChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(RING_CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(RING_CHANNEL_ID, "رنين التنبيه",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("تنبيه بملء الشاشة مع نغمة حتى تضغط إيقاف");
        ch.setSound(null, null);          // الصوت تشغّله الخدمة نفسها
        ch.enableVibration(false);        // والاهتزاز كذلك
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    // ------------------------------------------------------------------
    // الصوت والاهتزاز
    // ------------------------------------------------------------------

    private static final AudioAttributes ALARM_ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private void startSound() {
        audioManager = getSystemService(AudioManager.class);
        if (audioManager != null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(ALARM_ATTRS)
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        }

        Uri[] candidates = {
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        };
        for (Uri uri : candidates) {
            if (uri == null) continue;
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(ALARM_ATTRS);
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
                return;
            } catch (Exception e) {
                // نجرب النغمة التالية
            }
        }
    }

    private void startVibration() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 900, 700};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), ALARM_ATTRS);
    }

    private void releaseSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
                // لا شيء
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mawid:ring");
        wakeLock.acquire(AUTO_STOP_MS + 30_000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }
}
