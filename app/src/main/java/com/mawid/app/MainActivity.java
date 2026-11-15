package com.mawid.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** الشاشة الرئيسية: قائمة المواعيد القادمة. */
public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 11;

    private LinearLayout listContainer;
    private LinearLayout banners;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ticker.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        NotificationHelper.ensureChannel(this);
        setContentView(buildUi());
        askNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppointmentStore.setListener(this::refresh);
        for (Appointment a : AppointmentStore.removeExpired(this, System.currentTimeMillis())) {
            AlarmScheduler.cancel(this, a.id);
        }
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppointmentStore.setListener(null);
        ticker.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------

    private View buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        frame.setBackgroundColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.vertical(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 110));

        LinearLayout header = Ui.horizontal(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "موعد", 30, Ui.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView settings = Ui.text(this, "الإعدادات", 15, Ui.ACCENT_DARK, true);
        int sp = Ui.dp(this, 12);
        settings.setPadding(sp, Ui.dp(this, 8), sp, Ui.dp(this, 8));
        settings.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.ACCENT_SOFT, 12, 0, 0), 0x224F46E5, 12));
        settings.setClickable(true);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header, Ui.lp(-1, -2));
        TextView sub = Ui.text(this, "مواعيدك القادمة", 15, Ui.MUTED, false);
        root.addView(sub, Ui.lpMargins(this, -1, -2, 2, 16));

        banners = Ui.vertical(this);
        root.addView(banners, Ui.lp(-1, -2));

        listContainer = Ui.vertical(this);
        root.addView(listContainer, Ui.lp(-1, -2));

        scroll.addView(root);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView add = Ui.button(this, "+  موعد جديد", Ui.BTN_PRIMARY);
        add.setElevation(Ui.dp(this, 6));
        add.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-1, Ui.dp(this, 56), Gravity.BOTTOM);
        flp.setMargins(pad, 0, pad, Ui.dp(this, 24));
        frame.addView(add, flp);
        return frame;
    }

    private void refresh() {
        if (listContainer == null) return;
        refreshBanners();
        listContainer.removeAllViews();
        List<Appointment> all = AppointmentStore.getAll(this);
        if (all.isEmpty()) {
            listContainer.addView(emptyState());
            return;
        }
        for (Appointment a : all) {
            listContainer.addView(card(a), Ui.lpMargins(this, -1, -2, 0, 12));
        }
        listContainer.addView(howToHint(), Ui.lpMargins(this, -1, -2, 8, 0));
    }

    private void refreshBanners() {
        banners.removeAllViews();
        if (!NotificationHelper.canPost(this)) {
            banners.addView(banner("الإشعارات متوقفة، لن يظهر التذكير. اضغط هنا لتفعيلها.", v -> openNotificationSettings()),
                    Ui.lpMargins(this, -1, -2, 0, 12));
        }
        if (AiTitle.enabledFlag(this) && AiTitle.apiKey(this).isEmpty()) {
            TextView ai = Ui.text(this, "لعناوين مواعيد أوضح بالذكاء الاصطناعي، أضف مفتاح Gemini المجاني من الإعدادات. اضغط هنا.", 14, Ui.ACCENT_DARK, true);
            int p = Ui.dp(this, 14);
            ai.setPadding(p, p, p, p);
            ai.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.ACCENT_SOFT, 14, 0, 0), 0x224F46E5, 14));
            ai.setClickable(true);
            ai.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
            banners.addView(ai, Ui.lpMargins(this, -1, -2, 0, 12));
        }
        if (!NotificationHelper.canFullScreen(this)) {
            banners.addView(banner("ليظهر التنبيه بملء الشاشة مثل المنبّه، اسمح للتطبيق بـ«الإشعارات بملء الشاشة». اضغط هنا.", v -> openFullScreenSettings()),
                    Ui.lpMargins(this, -1, -2, 0, 12));
        }
        if (!AlarmScheduler.canExact(this)) {
            banners.addView(banner("للتذكير في الوقت الدقيق، اسمح للتطبيق بـ«المنبّهات والتذكيرات». اضغط هنا.", v -> openExactAlarmSettings()),
                    Ui.lpMargins(this, -1, -2, 0, 12));
        }
    }

    private View banner(String msg, View.OnClickListener click) {
        TextView t = Ui.text(this, msg, 14, Ui.WARN, true);
        int p = Ui.dp(this, 14);
        t.setPadding(p, p, p, p);
        t.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.WARN_BG, 14, 0xFFFCD9A0, 1), 0x22B45309, 14));
        t.setClickable(true);
        t.setOnClickListener(click);
        return t;
    }

    private View card(Appointment a) {
        LinearLayout card = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, Ui.dp(this, 12));
        card.setBackground(Ui.ripple(this, Ui.rounded(this, Ui.CARD, 18, Ui.BORDER, 1), 0x11000000, 18));
        card.setClickable(true);
        card.setOnClickListener(v -> openEditor(a.id));

        card.addView(Ui.text(this, a.title, 18, Ui.TEXT, true));

        TextView when = Ui.text(this, Fmt.full(a.timeMillis), 16, Ui.ACCENT_DARK, true);
        card.addView(when, Ui.lpMargins(this, -1, -2, 6, 0));

        String meta = Fmt.until(a.timeMillis) + "  •  "
                + (a.reminded ? "تم التذكير" : (a.ring ? "رنين قبل " : "إشعار قبل ") + Fmt.duration(a.remindMinutes));
        card.addView(Ui.text(this, meta, 13, Ui.MUTED, false), Ui.lpMargins(this, -1, -2, 4, 0));

        LinearLayout actions = Ui.horizontal(this);
        actions.setGravity(Gravity.END);
        TextView edit = smallAction("تعديل", Ui.ACCENT_DARK);
        edit.setOnClickListener(v -> openEditor(a.id));
        TextView del = smallAction("حذف", Ui.DANGER);
        del.setOnClickListener(v -> confirmDelete(a));
        actions.addView(edit);
        actions.addView(del);
        card.addView(actions, Ui.lpMargins(this, -1, -2, 6, 0));
        return card;
    }

    private TextView smallAction(String s, int color) {
        TextView t = Ui.text(this, s, 14, color, true);
        int h = Ui.dp(this, 14);
        int v = Ui.dp(this, 8);
        t.setPadding(h, v, h, v);
        t.setBackground(Ui.ripple(this, Ui.rounded(this, 0x00FFFFFF, 10, 0, 0), 0x22000000, 10));
        t.setClickable(true);
        return t;
    }

    private View emptyState() {
        LinearLayout box = Ui.vertical(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, p, p, p);
        box.setBackground(Ui.rounded(this, Ui.CARD, 18, Ui.BORDER, 1));
        box.addView(Ui.text(this, "لا توجد مواعيد قادمة", 18, Ui.TEXT, true));
        box.addView(Ui.text(this,
                "١. افتح الرسالة التي فيها الموعد (واتساب، تلغرام، البريد…)\n"
                        + "٢. اختر «مشاركة» ثم «موعد»، أو حدّد النص واختر «موعد» من القائمة\n"
                        + "٣. راجع الموعد واضغط «حفظ»\n\n"
                        + "سأذكّرك قبل الموعد بعشر دقائق، ثم يُحذف تلقائياً عند انتهاء وقته.",
                15, Ui.MUTED, false), Ui.lpMargins(this, -1, -2, 10, 0));
        return box;
    }

    private View howToHint() {
        TextView t = Ui.text(this, "نصيحة: شارك أي رسالة مع تطبيق «موعد» لإضافتها بسرعة.", 13, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void openEditor(int id) {
        startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_EDIT_ID, id));
    }

    private void confirmDelete(Appointment a) {
        new AlertDialog.Builder(this)
                .setTitle("حذف الموعد؟")
                .setMessage(a.title + "\n" + Fmt.full(a.timeMillis))
                .setPositiveButton("حذف", (d, w) -> {
                    AlarmScheduler.cancel(this, a.id);
                    AppointmentStore.delete(this, a.id);
                    refresh();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ------------------------------------------------------------------

    private void askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
                // نفتح إعدادات التطبيق بدلاً من ذلك
            }
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }
}
