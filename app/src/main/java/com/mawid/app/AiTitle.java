package com.mawid.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * توليد عنوان مختصر ومفهوم للموعد باستخدام Google Gemini.
 * عند أي فشل (لا إنترنت، مفتاح خاطئ…) يُستخدم العنوان المحلي تلقائياً.
 */
public final class AiTitle {

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    /** نماذج بديلة تُجرَّب إذا لم يكن النموذج الأساسي متاحاً. */
    private static final String[] FALLBACK_MODELS = {"gemini-flash-latest", "gemini-2.5-flash-lite"};

    private static final String PREFS = "mawid";
    private static final String KEY_ENABLED = "ai_enabled";
    private static final String KEY_API = "gemini_key";
    private static final String KEY_MODEL = "gemini_model";

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String SYSTEM_PROMPT =
            "أنت مساعد يكتب عنواناً قصيراً لموعد في تطبيق تذكير.\n"
                    + "ستصلك رسالة (من واتساب أو بريد أو تلغرام) فيها موعد. اكتب عنواناً واحداً يصف الموعد بوضوح.\n"
                    + "القواعد:\n"
                    + "- من 2 إلى 6 كلمات، بلغة الرسالة نفسها (عربية أو إنجليزية).\n"
                    + "- اذكر نوع الموعد (اجتماع، موعد طبيب، مقابلة عمل، مكالمة، تسليم…) ومع من أو بخصوص ماذا أو أين، حسب الأهم.\n"
                    + "- لا تذكر التاريخ ولا الوقت ولا اليوم.\n"
                    + "- لا تحيات ولا عبارات مثل «تذكير» أو «لديك» أو «نود إعلامكم».\n"
                    + "- عربية فصحى بسيطة وسليمة حتى لو كانت الرسالة باللهجة العامية.\n"
                    + "- أعد العنوان فقط، بدون علامات اقتباس أو نقطة أو رموز تعبيرية أو شرح.\n\n"
                    + "أمثلة:\n"
                    + "الرسالة: لدي موعد عند الساعة 2 مع مدير الشركة\nالعنوان: موعد مع مدير الشركة\n\n"
                    + "الرسالة: مرحبا استاذ حسان، نذكركم بموعدكم بكرا الساعة 10 بعيادة الدكتور سامر لطب الأسنان، يرجى الحضور قبل ربع ساعة\n"
                    + "العنوان: موعد طبيب الأسنان د. سامر\n\n"
                    + "الرسالة: يا شباب بكرا الساعة 4 عنا اجتماع لنحكي عن خطة التصوير تبع الحملة الجديدة\n"
                    + "العنوان: اجتماع خطة تصوير الحملة الجديدة\n\n"
                    + "الرسالة: Hi Hassan, your interview with the HR team is confirmed for Monday at 11am via Zoom\n"
                    + "العنوان: Interview with HR team";

    private AiTitle() { }

    // ------------------------------------------------------------------
    // الإعدادات
    // ------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true) && !apiKey(c).isEmpty();
    }

    public static boolean enabledFlag(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    public static String apiKey(Context c) {
        return prefs(c).getString(KEY_API, "").trim();
    }

    public static String model(Context c) {
        String m = prefs(c).getString(KEY_MODEL, "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    public static void save(Context c, boolean enabled, String key, String model) {
        prefs(c).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_API, key == null ? "" : key.trim())
                .putString(KEY_MODEL, model == null ? "" : model.trim())
                .apply();
    }

    // ------------------------------------------------------------------
    // الطلب
    // ------------------------------------------------------------------

    /** خطأ برسالة مفهومة للمستخدم. */
    public static final class AiException extends Exception {
        AiException(String msg) {
            super(msg);
        }
    }

    /** يُستدعى من خيط خلفي فقط. */
    public static String generate(Context c, String message) throws AiException {
        return generate(apiKey(c), model(c), message);
    }

    public static String generate(String key, String model, String message) throws AiException {
        if (key == null || key.trim().isEmpty()) throw new AiException("لم تُدخل مفتاح Gemini بعد");
        String text = message == null ? "" : message.trim();
        if (text.length() > 4000) text = text.substring(0, 4000);

        List<String> models = new ArrayList<>();
        models.add(model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim());
        for (String m : FALLBACK_MODELS) if (!models.contains(m)) models.add(m);

        AiException last = null;
        for (String m : models) {
            try {
                return call(key.trim(), m, text, m.startsWith("gemini-3"));
            } catch (ModelUnavailable e) {
                last = new AiException("النموذج " + m + " غير متاح");
            } catch (ThinkingUnsupported e) {
                try {
                    return call(key.trim(), m, text, false);
                } catch (ModelUnavailable | ThinkingUnsupported e2) {
                    last = new AiException("النموذج " + m + " غير متاح");
                }
            }
        }
        throw last != null ? last : new AiException("تعذّر الاتصال بـ Gemini");
    }

    private static final class ModelUnavailable extends Exception { }

    private static final class ThinkingUnsupported extends Exception { }

    private static String call(String key, String model, String message, boolean lowThinking)
            throws AiException, ModelUnavailable, ThinkingUnsupported {
        HttpURLConnection con = null;
        try {
            JSONObject body = new JSONObject();
            body.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT))));
            body.put("contents", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject()
                            .put("text", "الرسالة: " + message + "\nالعنوان:")))));
            JSONObject gen = new JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 1024);
            if (lowThinking) gen.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
            body.put("generationConfig", gen);

            URL url = new URL(String.format(ENDPOINT, model));
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("x-goog-api-key", key);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = con.getResponseCode();
            String resp = readAll(code >= 400 ? con.getErrorStream() : con.getInputStream());

            if (code == 404) throw new ModelUnavailable();
            if (code == 400 && lowThinking && resp.toLowerCase().contains("thinking")) throw new ThinkingUnsupported();
            if (code >= 400) throw new AiException(explainError(code, resp));

            return extractTitle(resp);
        } catch (AiException | ModelUnavailable | ThinkingUnsupported e) {
            throw e;
        } catch (java.net.UnknownHostException | java.net.ConnectException e) {
            throw new AiException("لا يوجد اتصال بالإنترنت");
        } catch (java.net.SocketTimeoutException e) {
            throw new AiException("انتهت مهلة الاتصال بـ Gemini");
        } catch (Exception e) {
            throw new AiException("تعذّر الاتصال بـ Gemini");
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static String extractTitle(String resp) throws AiException {
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray cands = o.optJSONArray("candidates");
            if (cands == null || cands.length() == 0) throw new AiException("لم يُرجع Gemini أي نتيجة");
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            if (content == null) throw new AiException("لم يُرجع Gemini أي نتيجة");
            JSONArray parts = content.optJSONArray("parts");
            StringBuilder sb = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject p = parts.getJSONObject(i);
                    if (p.optBoolean("thought", false)) continue;
                    sb.append(p.optString("text", ""));
                }
            }
            String t = cleanTitle(sb.toString());
            if (t.isEmpty()) throw new AiException("لم يُرجع Gemini عنواناً");
            return t;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("ردّ غير مفهوم من Gemini");
        }
    }

    static String cleanTitle(String raw) {
        String t = raw == null ? "" : raw.trim();
        // أول سطر غير فارغ فقط
        for (String line : t.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                t = line.trim();
                break;
            }
        }
        t = t.replaceFirst("^(?:العنوان|Title)\\s*[:：]\\s*", "");
        t = t.replaceAll("^[\"'«»“”*`#\\-\\s]+|[\"'«»“”*`.\\s]+$", "").trim();
        if (t.length() > 60) t = t.substring(0, 60).trim() + "…";
        return t;
    }

    private static String explainError(int code, String resp) {
        String msg = "";
        try {
            JSONObject err = new JSONObject(resp).optJSONObject("error");
            if (err != null) msg = err.optString("message", "");
        } catch (Exception ignored) {
            // لا شيء
        }
        String low = msg.toLowerCase();
        if (low.contains("location") || low.contains("region") || low.contains("country")) {
            return "خدمة Gemini غير متاحة في منطقتك الحالية (قد تحتاج VPN)";
        }
        if (code == 400 && (low.contains("api key") || low.contains("api_key"))) return "مفتاح Gemini غير صحيح";
        if (code == 401 || code == 403) return "مفتاح Gemini غير صحيح أو غير مفعّل";
        if (code == 429) return "تجاوزت الحد المسموح مؤقتاً، حاول بعد قليل";
        if (code >= 500) return "خوادم Gemini مشغولة حالياً";
        return "خطأ من Gemini (" + code + ")";
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
