package com.mawid.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

/** تنسيق التواريخ والأوقات بالعربية. */
public final class Fmt {

    private static final String[] DAYS = {
            "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] MONTHS = {
            "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
            "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};

    private Fmt() { }

    public static LocalDateTime toLocal(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    public static long toMillis(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String time(long ms) {
        return time(toLocal(ms).toLocalTime());
    }

    public static String time(LocalTime t) {
        int h = t.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d", h12, t.getMinute()) + (h < 12 ? " ص" : " م");
    }

    /** مثال: «الأحد 15 تشرين الثاني» */
    public static String date(LocalDate d) {
        String s = DAYS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
        if (d.getYear() != LocalDate.now().getYear()) s += " " + d.getYear();
        return s;
    }

    /** مثال: «اليوم» / «غداً» / «الخميس 19 تشرين الثاني» */
    public static String relativeDay(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "اليوم";
        if (d.equals(today.plusDays(1))) return "غداً";
        if (d.equals(today.plusDays(2))) return "بعد غد";
        return date(d);
    }

    /** مثال: «اليوم · 2:00 م» */
    public static String full(long ms) {
        LocalDateTime dt = toLocal(ms);
        return relativeDay(dt.toLocalDate()) + " · " + time(dt.toLocalTime());
    }

    /** مدة بالدقائق بصيغة عربية سليمة. */
    public static String duration(long minutes) {
        if (minutes < 60) return count(minutes, "دقيقة", "دقيقتين", "دقائق", "دقيقة");
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            String s = count(days, "يوم", "يومين", "أيام", "يوماً");
            if (hours > 0 && days < 3) s += " و" + count(hours, "ساعة", "ساعتين", "ساعات", "ساعة");
            return s;
        }
        String s = count(hours, "ساعة", "ساعتين", "ساعات", "ساعة");
        if (mins > 0) s += " و" + count(mins, "دقيقة", "دقيقتين", "دقائق", "دقيقة");
        return s;
    }

    /** «بعد ساعتين و5 دقائق» */
    public static String until(long ms) {
        long diff = ms - System.currentTimeMillis();
        if (diff <= 60_000) return "الآن";
        return "بعد " + duration((diff + 59_999) / 60_000);
    }

    private static String count(long n, String one, String two, String few, String many) {
        if (n == 1) return one;
        if (n == 2) return two;
        if (n >= 3 && n <= 10) return n + " " + few;
        return n + " " + many;
    }
}
