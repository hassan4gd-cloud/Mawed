package com.mawid.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** الإعدادات: مفتاح Google Gemini لتوليد عنوان الموعد بالذكاء الاصطناعي. */
public class SettingsActivity extends Activity {

    private Switch aiSwitch;
    private EditText keyEt;
    private EditText modelEt;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildUi());

        aiSwitch.setChecked(AiTitle.enabledFlag(this));
        keyEt.setText(AiTitle.apiKey(this));
        modelEt.setText(AiTitle.model(this));
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.vertical(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 24));
        scroll.addView(root);

        root.addView(Ui.text(this, "الإعدادات", 26, Ui.TEXT, true));

        // ---- العنوان الذكي ----
        LinearLayout card = Ui.vertical(this);
        int cp = Ui.dp(this, 16);
        card.setPadding(cp, Ui.dp(this, 8), cp, cp);
        card.setBackground(Ui.rounded(this, Ui.CARD, 18, Ui.BORDER, 1));
        root.addView(card, Ui.lpMargins(this, -1, -2, 18, 0));

        aiSwitch = new Switch(this);
        aiSwitch.setText("عنوان الموعد بالذكاء الاصطناعي (Gemini)");
        aiSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        aiSwitch.setTextColor(Ui.TEXT);
        aiSwitch.setMinHeight(Ui.dp(this, 52));
        card.addView(aiSwitch, Ui.lp(-1, -2));

        card.addView(Ui.text(this,
                "يكتب Gemini عنواناً مختصراً وواضحاً من نص الرسالة. يحتاج إنترنت ومفتاحاً مجانياً من Google. "
                        + "التاريخ والوقت يُستخرجان داخل الهاتف كما هو. إذا فشل الاتصال يُستخدم العنوان المحلي.",
                13, Ui.MUTED, false), Ui.lp(-1, -2));

        card.addView(Ui.text(this, "مفتاح Gemini API", 14, Ui.MUTED, true), Ui.lpMargins(this, -1, -2, 16, 6));
        keyEt = input();
        keyEt.setHint("AIza…");
        keyEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyEt.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(keyEt, Ui.lp(-1, -2));

        TextView getKey = Ui.button(this, "احصل على مفتاح مجاني", Ui.BTN_SOFT);
        getKey.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")));
            } catch (Exception e) {
                Toast.makeText(this, "افتح: aistudio.google.com/apikey", Toast.LENGTH_LONG).show();
            }
        });
        card.addView(getKey, Ui.lpMargins(this, -1, -2, 10, 0));

        card.addView(Ui.text(this, "النموذج (اتركه كما هو)", 14, Ui.MUTED, true), Ui.lpMargins(this, -1, -2, 16, 6));
        modelEt = input();
        modelEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        modelEt.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(modelEt, Ui.lp(-1, -2));

        TextView test = Ui.button(this, "اختبار الاتصال", Ui.BTN_OUTLINE);
        test.setOnClickListener(v -> testConnection());
        card.addView(test, Ui.lpMargins(this, -1, -2, 14, 0));

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        card.addView(status, Ui.lpMargins(this, -1, -2, 10, 0));

        // ---- الحفظ ----
        TextView save = Ui.button(this, "حفظ", Ui.BTN_PRIMARY);
        save.setOnClickListener(v -> {
            persist();
            Toast.makeText(this, "تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(save, Ui.lpMargins(this, -1, Ui.dp(this, 56), 24, 0));

        TextView back = Ui.button(this, "رجوع", Ui.BTN_OUTLINE);
        back.setOnClickListener(v -> finish());
        root.addView(back, Ui.lpMargins(this, -1, -2, 10, 0));

        root.addView(Ui.text(this,
                "المفتاح يُحفظ داخل هاتفك فقط، ولا يُرسل نص الرسالة إلا إلى Google لتوليد العنوان.",
                12, Ui.MUTED, false), Ui.lpMargins(this, -1, -2, 16, 0));
        return scroll;
    }

    private EditText input() {
        EditText e = new EditText(this);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0xFF9CA3AF);
        e.setSingleLine(true);
        int p = Ui.dp(this, 14);
        e.setPadding(p, p, p, p);
        e.setBackground(Ui.rounded(this, Ui.BG, 12, Ui.BORDER, 1));
        return e;
    }

    private void persist() {
        AiTitle.save(this, aiSwitch.isChecked(), keyEt.getText().toString(), modelEt.getText().toString());
    }

    private void testConnection() {
        final String key = keyEt.getText().toString().trim();
        final String model = modelEt.getText().toString().trim();
        if (key.isEmpty()) {
            status.setTextColor(Ui.DANGER);
            status.setText("أدخل المفتاح أولاً");
            return;
        }
        status.setTextColor(Ui.MUTED);
        status.setText("جارٍ الاختبار…");
        new Thread(() -> {
            String result;
            boolean ok;
            try {
                String t = AiTitle.generate(key, model,
                        "يا أستاذ بكرا الساعة 3 عنا اجتماع مع شركة الإعلانات لنتفق على تصميم الحملة");
                result = "يعمل ✓  مثال على عنوان: " + t;
                ok = true;
            } catch (AiTitle.AiException e) {
                result = e.getMessage();
                ok = false;
            }
            final String r = result;
            final boolean success = ok;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                status.setTextColor(success ? 0xFF15803D : Ui.DANGER);
                status.setText(r);
                if (success) persist();
            });
        }).start();
    }
}
