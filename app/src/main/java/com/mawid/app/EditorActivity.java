package com.mawid.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * شاشة إضافة/تعديل موعد.
 * تُفتح من: زر «موعد جديد» ، أو «مشاركة» نص من أي تطبيق ، أو تحديد نص واختيار «موعد».
 */
public class EditorActivity extends Activity {

    public static final String EXTRA_EDIT_ID = "edit_id";
    private static final int[] REMIND_OPTIONS = {5, 10, 15, 30, 60};
    private static final int REQ_NOTIF = 12;

    private EditText sourceEt;
    private EditText titleEt;
    private TextView dateBtn;
    private TextView timeBtn;
    private TextView warning;
    private TextView reminderPreview;
    private LinearLayout chips;

    private LocalDate date;
    private LocalTime time;
    private int remindMinutes = 10;
    private Appointment editing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        NotificationHelper.ensureChannel(this);

        setContentView(buildUi());

        Intent in = getIntent();
        int editId = in.getIntExtra(EXTRA_EDIT_ID, 0);
        String shared = extractSharedText(in);

        if (editId != 0 && (editing = AppointmentStore.get(this, editId)) != null) {
            LocalDateTime dt = Fmt.toLocal(editing.timeMillis);
            date = dt.toLocalDate();
            time = dt.toLocalTime();
            remindMinutes = editing.remindMinutes;
            sourceEt.setText(editing.note);
            titleEt.setText(editing.title);
            warning.setVisibility(View.GONE);
        } else if (shared != null && !shared.trim().isEmpty()) {
            sourceEt.setText(shared.trim());
            analyze();
        } else {
            LocalDateTime d = SmartParser.parse("").when;
            date = d.toLocalDate();
            time = d.toLocalTime();
            warning.setVisibility(View.GONE);
        }
        updateLabels();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private static String extractSharedText(Intent in) {
        if (in == null || in.getAction() == null) return null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String subject = in.getStringExtra(Intent.EXTRA_SUBJECT);
            String body = t == null ? "" : t.toString();
            if (subject != null && !subject.trim().isEmpty() && !body.contains(subject.trim())) {
                body = subject.trim() + "\n" + body;
            }
            return body;
        }
        if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            return t == null ? null : t.toString();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // الواجهة
    // ------------------------------------------------------------------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.vertical(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 24));
        scroll.addView(root);

        boolean isEdit = getIntent().getIntExtra(EXTRA_EDIT_ID, 0) != 0;
        root.addView(Ui.text(this, isEdit ? "تعديل الموعد" : "موعد جديد", 26, Ui.TEXT, true));

        // نص الرسالة
        root.addView(label("نص الرسالة"), Ui.lpMargins(this, -1, -2, 18, 6));
        sourceEt = input(true);
        sourceEt.setHint("الصق هنا نص الرسالة التي فيها الموعد…");
        sourceEt.setMinLines(3);
        sourceEt.setMaxLines(8);
        root.addView(sourceEt, Ui.lp(-1, -2));

        LinearLayout srcActions = Ui.horizontal(this);
        TextView paste = Ui.button(this, "لصق", Ui.BTN_OUTLINE);
        paste.setOnClickListener(v -> pasteFromClipboard());
        TextView analyzeBtn = Ui.button(this, "استخراج الموعد", Ui.BTN_SOFT);
        analyzeBtn.setOnClickListener(v -> {
            analyze();
            updateLabels();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1.6f);
        l2.setMarginStart(Ui.dp(this, 10));
        srcActions.addView(paste, l1);
        srcActions.addView(analyzeBtn, l2);
        root.addView(srcActions, Ui.lpMargins(this, -1, -2, 10, 0));

        warning = Ui.text(this, "", 14, Ui.WARN, true);
        int wp = Ui.dp(this, 12);
        warning.setPadding(wp, wp, wp, wp);
        warning.setBackground(Ui.rounded(this, Ui.WARN_BG, 12, 0xFFFCD9A0, 1));
        warning.setVisibility(View.GONE);
        root.addView(warning, Ui.lpMargins(this, -1, -2, 14, 0));

        // العنوان
        root.addView(label("عنوان الموعد"), Ui.lpMargins(this, -1, -2, 18, 6));
        titleEt = input(false);
        titleEt.setHint("مثال: موعد مع مدير الشركة");
        root.addView(titleEt, Ui.lp(-1, -2));

        // التاريخ والوقت
        root.addView(label("التاريخ والوقت"), Ui.lpMargins(this, -1, -2, 18, 6));
        LinearLayout dt = Ui.horizontal(this);
        dateBtn = Ui.button(this, "", Ui.BTN_OUTLINE);
        dateBtn.setOnClickListener(v -> pickDate());
        timeBtn = Ui.button(this, "", Ui.BTN_OUTLINE);
        timeBtn.setOnClickListener(v -> pickTime());
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(0, -2, 1.5f);
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(0, -2, 1f);
        d2.setMarginStart(Ui.dp(this, 10));
        dt.addView(dateBtn, d1);
        dt.addView(timeBtn, d2);
        root.addView(dt, Ui.lp(-1, -2));

        // التذكير
        root.addView(label("ذكّرني قبل الموعد بـ"), Ui.lpMargins(this, -1, -2, 18, 6));
        chips = Ui.horizontal(this);
        for (int m : REMIND_OPTIONS) {
            TextView chip = Ui.text(this, m == 60 ? "ساعة" : m + " د", 15, Ui.TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            chip.setTag(m);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                remindMinutes = (Integer) v.getTag();
                updateLabels();
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            cl.setMarginEnd(Ui.dp(this, 6));
            chips.addView(chip, cl);
        }
        root.addView(chips, Ui.lp(-1, -2));

        reminderPreview = Ui.text(this, "", 14, Ui.MUTED, false);
        root.addView(reminderPreview, Ui.lpMargins(this, -1, -2, 10, 0));

        // الأزرار
        TextView save = Ui.button(this, "حفظ الموعد", Ui.BTN_PRIMARY);
        save.setOnClickListener(v -> save());
        root.addView(save, Ui.lpMargins(this, -1, Ui.dp(this, 56), 24, 0));

        TextView cancel = Ui.button(this, "إلغاء", Ui.BTN_OUTLINE);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel, Ui.lpMargins(this, -1, -2, 10, 0));

        if (isEdit) {
            TextView del = Ui.button(this, "حذف الموعد", Ui.BTN_DANGER);
            del.setOnClickListener(v -> confirmDelete());
            root.addView(del, Ui.lpMargins(this, -1, -2, 10, 0));
        }
        return scroll;
    }

    private TextView label(String s) {
        return Ui.text(this, s, 14, Ui.MUTED, true);
    }

    private EditText input(boolean multi) {
        EditText e = new EditText(this);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0xFF9CA3AF);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        int p = Ui.dp(this, 14);
        e.setPadding(p, p, p, p);
        e.setBackground(Ui.rounded(this, Ui.CARD, 14, Ui.BORDER, 1));
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setSingleLine(true);
        }
        return e;
    }

    private void updateLabels() {
        dateBtn.setText(Fmt.relativeDay(date));
        timeBtn.setText(Fmt.time(time));
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            boolean sel = (Integer) chip.getTag() == remindMinutes;
            chip.setTextColor(sel ? 0xFFFFFFFF : Ui.TEXT);
            chip.setBackground(Ui.ripple(this,
                    Ui.rounded(this, sel ? Ui.ACCENT : Ui.CARD, 12, sel ? Ui.ACCENT : Ui.BORDER, 1),
                    0x22000000, 12));
        }
        long when = Fmt.toMillis(LocalDateTime.of(date, time));
        long remindAt = when - remindMinutes * 60_000L;
        long now = System.currentTimeMillis();
        String s;
        if (when <= now) {
            s = "هذا الوقت قد مضى — اختر وقتاً لاحقاً.";
            reminderPreview.setTextColor(Ui.DANGER);
        } else {
            reminderPreview.setTextColor(Ui.MUTED);
            s = "الموعد " + Fmt.until(when) + "، ";
            s += remindAt > now
                    ? "وسيظهر التذكير " + Fmt.relativeDay(Fmt.toLocal(remindAt).toLocalDate()) + " الساعة " + Fmt.time(remindAt) + "."
                    : "وسيظهر التذكير فوراً بعد الحفظ.";
            s += "\nيُحذف الموعد تلقائياً عند انتهاء وقته.";
        }
        reminderPreview.setText(s);
    }

    // ------------------------------------------------------------------
    // الإجراءات
    // ------------------------------------------------------------------

    private void analyze() {
        String text = sourceEt.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "الصق نص الرسالة أولاً", Toast.LENGTH_SHORT).show();
            if (date == null) {
                LocalDateTime d = SmartParser.parse("").when;
                date = d.toLocalDate();
                time = d.toLocalTime();
            }
            return;
        }
        SmartParser.Result r = SmartParser.parse(text, LocalDateTime.now());
        date = r.when.toLocalDate();
        time = r.when.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        titleEt.setText(r.title);

        if (!r.timeFound && !r.dateFound) {
            warning.setText("لم أجد تاريخاً أو وقتاً واضحاً في النص. حدّد التاريخ والوقت يدوياً.");
            warning.setVisibility(View.VISIBLE);
        } else if (!r.timeFound) {
            warning.setText("وجدت التاريخ لكن لم أجد الساعة. تأكد من الوقت قبل الحفظ.");
            warning.setVisibility(View.VISIBLE);
        } else {
            warning.setVisibility(View.GONE);
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
        CharSequence t = item.coerceToText(this);
        if (t == null || t.toString().trim().isEmpty()) {
            Toast.makeText(this, "الحافظة لا تحتوي نصاً", Toast.LENGTH_SHORT).show();
            return;
        }
        sourceEt.setText(t.toString().trim());
        analyze();
        updateLabels();
    }

    private void pickDate() {
        DatePickerDialog d = new DatePickerDialog(this, (view, y, m, day) -> {
            date = LocalDate.of(y, m + 1, day);
            updateLabels();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        d.show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, h, m) -> {
            time = LocalTime.of(h, m);
            updateLabels();
        }, time.getHour(), time.getMinute(), false).show();
    }

    private void save() {
        long when = Fmt.toMillis(LocalDateTime.of(date, time));
        if (when <= System.currentTimeMillis()) {
            Toast.makeText(this, "هذا الوقت قد مضى، اختر وقتاً لاحقاً", Toast.LENGTH_LONG).show();
            return;
        }
        String title = titleEt.getText().toString().trim();
        if (title.isEmpty()) title = "موعد";

        Appointment a = editing != null ? editing : new Appointment();
        a.title = title;
        a.note = sourceEt.getText().toString().trim();
        a.timeMillis = when;
        a.remindMinutes = remindMinutes;
        a.reminded = false;
        if (editing != null) AlarmScheduler.cancel(this, a.id);
        AppointmentStore.save(this, a);
        AlarmScheduler.schedule(this, a);

        String msg = "تم حفظ الموعد: " + Fmt.full(when);
        if (!NotificationHelper.canPost(this)) msg += "\nتنبيه: الإشعارات متوقفة، فعّلها ليظهر التذكير.";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }

    private void confirmDelete() {
        if (editing == null) return;
        new AlertDialog.Builder(this)
                .setTitle("حذف الموعد؟")
                .setMessage(editing.title)
                .setPositiveButton("حذف", (d, w) -> {
                    AlarmScheduler.cancel(this, editing.id);
                    AppointmentStore.delete(this, editing.id);
                    finish();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}
