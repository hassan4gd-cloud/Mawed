package com.mawid.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** تخزين المواعيد في ملف JSON داخل مساحة التطبيق الخاصة (لا يحتاج إنترنت ولا صلاحيات). */
public final class AppointmentStore {

    private static final String FILE = "appointments.json";
    private static final Object LOCK = new Object();

    public interface Listener {
        void onChanged();
    }

    private static volatile Listener listener;

    private AppointmentStore() { }

    public static void setListener(Listener l) {
        listener = l;
    }

    private static void notifyChanged() {
        final Listener l = listener;
        if (l != null) new Handler(Looper.getMainLooper()).post(l::onChanged);
    }

    public static List<Appointment> getAll(Context c) {
        synchronized (LOCK) {
            List<Appointment> list = read(c);
            list.sort((a, b) -> Long.compare(a.timeMillis, b.timeMillis));
            return list;
        }
    }

    public static Appointment get(Context c, int id) {
        synchronized (LOCK) {
            for (Appointment a : read(c)) if (a.id == id) return a;
            return null;
        }
    }

    /** إضافة موعد جديد (id = 0) أو تحديث موعد موجود. */
    public static void save(Context c, Appointment a) {
        synchronized (LOCK) {
            List<Appointment> list = read(c);
            if (a.id == 0) a.id = nextId(c);
            boolean replaced = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == a.id) {
                    list.set(i, a);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) list.add(a);
            write(c, list);
        }
        notifyChanged();
    }

    public static void delete(Context c, int id) {
        boolean changed = false;
        synchronized (LOCK) {
            List<Appointment> list = read(c);
            Iterator<Appointment> it = list.iterator();
            while (it.hasNext()) {
                if (it.next().id == id) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) write(c, list);
        }
        if (changed) notifyChanged();
    }

    /** حذف كل المواعيد التي مضى وقتها. */
    public static List<Appointment> removeExpired(Context c, long now) {
        List<Appointment> removed = new ArrayList<>();
        synchronized (LOCK) {
            List<Appointment> list = read(c);
            Iterator<Appointment> it = list.iterator();
            while (it.hasNext()) {
                Appointment a = it.next();
                if (a.timeMillis <= now) {
                    removed.add(a);
                    it.remove();
                }
            }
            if (!removed.isEmpty()) write(c, list);
        }
        if (!removed.isEmpty()) notifyChanged();
        return removed;
    }

    // ------------------------------------------------------------------

    private static int nextId(Context c) {
        SharedPreferences p = c.getSharedPreferences("mawid", Context.MODE_PRIVATE);
        int n = p.getInt("next_id", 1);
        p.edit().putInt("next_id", n + 1).commit();
        return n;
    }

    private static List<Appointment> read(Context c) {
        List<Appointment> list = new ArrayList<>();
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return list;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Appointment.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            // ملف تالف: نبدأ من جديد
        }
        return list;
    }

    private static void write(Context c, List<Appointment> list) {
        File dir = c.getFilesDir();
        File tmp = new File(dir, FILE + ".tmp");
        File dst = new File(dir, FILE);
        try {
            JSONArray arr = new JSONArray();
            for (Appointment a : list) arr.put(a.toJson());
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!tmp.renameTo(dst)) {
                try (FileOutputStream out = new FileOutputStream(dst)) {
                    out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (JSONException | java.io.IOException e) {
            // تجاهل — سيُعاد المحاولة عند الحفظ التالي
        }
    }
}
