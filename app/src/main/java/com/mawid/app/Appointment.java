package com.mawid.app;

import org.json.JSONException;
import org.json.JSONObject;

/** موعد واحد محفوظ داخل التطبيق. */
public final class Appointment {
    public int id;
    public String title = "";
    /** نص الرسالة الأصلية (للرجوع إليه). */
    public String note = "";
    /** وقت الموعد (epoch millis). */
    public long timeMillis;
    /** كم دقيقة قبل الموعد يظهر التذكير. */
    public int remindMinutes = 10;
    /** هل ظهر إشعار التذكير بالفعل؟ */
    public boolean reminded;

    public long remindAtMillis() {
        return timeMillis - remindMinutes * 60_000L;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("note", note);
        o.put("time", timeMillis);
        o.put("remind", remindMinutes);
        o.put("reminded", reminded);
        return o;
    }

    static Appointment fromJson(JSONObject o) {
        Appointment a = new Appointment();
        a.id = o.optInt("id");
        a.title = o.optString("title", "");
        a.note = o.optString("note", "");
        a.timeMillis = o.optLong("time");
        a.remindMinutes = o.optInt("remind", 10);
        a.reminded = o.optBoolean("reminded", false);
        return a;
    }
}
