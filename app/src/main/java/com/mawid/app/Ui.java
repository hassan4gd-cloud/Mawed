package com.mawid.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** أدوات صغيرة لبناء الواجهة برمجياً بشكل موحّد. */
final class Ui {

    static final int ACCENT = 0xFF4F46E5;
    static final int ACCENT_DARK = 0xFF3730A3;
    static final int ACCENT_SOFT = 0xFFEEF0FF;
    static final int BG = 0xFFF4F5FA;
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF1F2937;
    static final int MUTED = 0xFF6B7280;
    static final int BORDER = 0xFFE5E7EB;
    static final int DANGER = 0xFFDC2626;
    static final int WARN_BG = 0xFFFFF7E6;
    static final int WARN = 0xFFB45309;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    static Drawable ripple(Context c, Drawable content, int rippleColor, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** زر: primary = خلفية ملوّنة ، وإلا زر بإطار. */
    static TextView button(Context c, String s, int style) {
        TextView b = new TextView(c);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(c, 50));
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        b.setClickable(true);
        b.setFocusable(true);
        Drawable bg;
        switch (style) {
            case BTN_PRIMARY:
                b.setTextColor(0xFFFFFFFF);
                bg = ripple(c, rounded(c, ACCENT, 14, 0, 0), 0x33FFFFFF, 14);
                break;
            case BTN_DANGER:
                b.setTextColor(DANGER);
                bg = ripple(c, rounded(c, 0xFFFFFFFF, 14, 0xFFF3C4C4, 1), 0x22DC2626, 14);
                break;
            case BTN_SOFT:
                b.setTextColor(ACCENT_DARK);
                bg = ripple(c, rounded(c, ACCENT_SOFT, 14, 0, 0), 0x224F46E5, 14);
                break;
            default:
                b.setTextColor(TEXT);
                bg = ripple(c, rounded(c, 0xFFFFFFFF, 14, BORDER, 1), 0x22000000, 14);
        }
        b.setBackground(bg);
        return b;
    }

    static final int BTN_PRIMARY = 0;
    static final int BTN_OUTLINE = 1;
    static final int BTN_DANGER = 2;
    static final int BTN_SOFT = 3;

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpMargins(Context c, int w, int h, float top, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, top);
        p.bottomMargin = dp(c, bottom);
        return p;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    static View spacer(Context c, float dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, dp)));
        v.setBackground(new ColorDrawable(0));
        return v;
    }
}
