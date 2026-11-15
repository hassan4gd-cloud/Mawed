package com.mawid.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** شاشة التنبيه بملء الشاشة (تظهر فوق قفل الشاشة) مع زر «إيقاف» كبير. */
public class AlarmActivity extends Activity implements AlarmService.UiListener {

    private TextView titleTv;
    private TextView timeTv;
    private TextView untilTv;
    private ObjectAnimator pulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        setContentView(buildUi());
        bind();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        bind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmService.setUiListener(this);
        if (!AlarmService.isRinging()) {
            finish();
            return;
        }
        bind();
        if (pulse != null && !pulse.isStarted()) pulse.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlarmService.setUiListener(null);
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }

    @Override
    public void onRingStopped() {
        runOnUiThread(this::finish);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // زر الرجوع لا يوقف التنبيه — يجب الضغط على «إيقاف»
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(0xFF1E1B4B);
        w.setNavigationBarColor(0xFF312E81);
    }

    private void bind() {
        if (titleTv == null) return;
        long t = AlarmService.currentTime();
        titleTv.setText(AlarmService.currentTitle());
        timeTv.setText(Fmt.time(t));
        untilTv.setText(Fmt.until(t).equals("الآن") ? "الموعد الآن" : "الموعد " + Fmt.until(t));
    }

    private View buildUi() {
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1E1B4B, 0xFF312E81, 0xFF4338CA});
        frame.setBackground(bg);

        LinearLayout col = Ui.vertical(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        col.setPadding(pad, Ui.dp(this, 64), pad, Ui.dp(this, 40));
        frame.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView label = Ui.text(this, "تذكير بموعد", 16, 0xCCFFFFFF, true);
        label.setGravity(Gravity.CENTER);
        col.addView(label, Ui.lp(-1, -2));

        timeTv = Ui.text(this, "", 58, Color.WHITE, true);
        timeTv.setGravity(Gravity.CENTER);
        col.addView(timeTv, Ui.lpMargins(this, -1, -2, 10, 0));

        untilTv = Ui.text(this, "", 17, 0xDDFFFFFF, false);
        untilTv.setGravity(Gravity.CENTER);
        col.addView(untilTv, Ui.lpMargins(this, -1, -2, 2, 0));

        // جرس نابض في المنتصف
        FrameLayout bellWrap = new FrameLayout(this);
        int ring = Ui.dp(this, 150);
        View halo = new View(this);
        GradientDrawable haloBg = new GradientDrawable();
        haloBg.setShape(GradientDrawable.OVAL);
        haloBg.setColor(0x33FFFFFF);
        halo.setBackground(haloBg);
        bellWrap.addView(halo, new FrameLayout.LayoutParams(ring, ring, Gravity.CENTER));

        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_notify);
        bell.setColorFilter(Color.WHITE);
        int inner = Ui.dp(this, 100);
        GradientDrawable innerBg = new GradientDrawable();
        innerBg.setShape(GradientDrawable.OVAL);
        innerBg.setColor(0x44FFFFFF);
        bell.setBackground(innerBg);
        int ip = Ui.dp(this, 24);
        bell.setPadding(ip, ip, ip, ip);
        bellWrap.addView(bell, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));

        LinearLayout.LayoutParams bwl = new LinearLayout.LayoutParams(-1, 0, 1f);
        col.addView(bellWrap, bwl);

        pulse = ObjectAnimator.ofPropertyValuesHolder(halo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.15f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.35f));
        pulse.setDuration(900);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);

        titleTv = Ui.text(this, "", 26, Color.WHITE, true);
        titleTv.setGravity(Gravity.CENTER);
        col.addView(titleTv, Ui.lpMargins(this, -1, -2, 0, 36));

        // زر الإيقاف
        TextView stop = new TextView(this);
        stop.setText("إيقاف");
        stop.setTextColor(Color.WHITE);
        stop.setTextSize(22);
        stop.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        stop.setGravity(Gravity.CENTER);
        stop.setClickable(true);
        stop.setFocusable(true);
        stop.setBackground(Ui.ripple(this, Ui.rounded(this, 0xFFDC2626, 36, 0, 0), 0x44FFFFFF, 36));
        stop.setElevation(Ui.dp(this, 8));
        stop.setOnClickListener(v -> {
            AlarmService.stop(this);
            finish();
        });
        col.addView(stop, new LinearLayout.LayoutParams(-1, Ui.dp(this, 72)));

        return frame;
    }
}
