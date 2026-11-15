package com.mawid.app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * محلّل نصوص يعمل بالكامل داخل الهاتف (بدون إنترنت).
 * يستخرج من نص الرسالة: التاريخ، الوقت، وعنواناً مختصراً للموعد.
 * يدعم العربية الفصحى واللهجات الشائعة (خاصة الشامية) والإنجليزية.
 *
 * ملاحظة: هذا الملف Java خالص (لا يعتمد على Android) لذلك يمكن اختباره مباشرة.
 */
public final class SmartParser {

    /** نتيجة التحليل. */
    public static final class Result {
        /** الوقت المستخرج (أو اقتراح افتراضي إن لم يُعثر على وقت). */
        public LocalDateTime when;
        /** هل وُجد وقت (ساعة) صريح في النص؟ */
        public boolean timeFound;
        /** هل وُجد تاريخ/يوم صريح في النص؟ */
        public boolean dateFound;
        /** عنوان مقترح للموعد. */
        public String title;

        @Override
        public String toString() {
            return "Result{when=" + when + ", timeFound=" + timeFound + ", dateFound=" + dateFound
                    + ", title='" + title + "'}";
        }
    }

    private SmartParser() { }

    // ---------------------------------------------------------------------------------------
    // أنماط مساعدة
    // ---------------------------------------------------------------------------------------

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** فاصل يوضع مكان الأجزاء التي تمت قراءتها (تاريخ/وقت) حتى لا تدخل في العنوان. */
    private static final char SENTINEL = '¦'; // ¦

    private static final String NB = "(?<![\\p{L}\\d])";   // بداية كلمة
    private static final String NE = "(?![\\p{L}\\d])";    // نهاية كلمة

    // الساعات بالكلمات (بعد التطبيع: ة→ه ، ى→ي ، أإآ→ا) — الأطول أولاً
    private static final String H11 = "احدعش|حداعش|احداعش|حدعش|احدي\\s?عشر[ه]?|احد\\s?عشر[ه]?|حاديه\\s?عشر[ه]?";
    private static final String H12 = "اثناعش|اتناعش|اتنعش|اتنعشر|طنعش|اطنعش|ثنعش|اثن[اي]\\s?عشر[ه]?|ثانيه\\s?عشر[ه]?";
    private static final String H10 = "عشره|عشر|عاشره";
    private static final String H1 = "واحده|الوحده";
    private static final String H2 = "ثنتين|اثنتين|اثنين|ثنين|تنتين|ثانيه|تانيه";
    private static final String H3 = "ثلاثه|ثلاث|ثالثه|تلاته|تلات|تالته";
    private static final String H4 = "اربعه|اربع|رابعه";
    private static final String H5 = "خمسه|خمس|خامسه";
    private static final String H6 = "سته|ست|سادسه";
    private static final String H7 = "سبعه|سبع|سابعه";
    private static final String H8 = "ثمانيه|ثماني|ثمانه|ثمان|ثامنه|تمانيه|تمانه|تمان|تمنه|تامنه";
    private static final String H9 = "تسعه|تسع|تاسعه";

    private static final String[] HOUR_WORDS = {H11, H12, H10, H1, H2, H3, H4, H5, H6, H7, H8, H9};
    private static final int[] HOUR_VALUES = {11, 12, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final String HOUR_WORD_ALT;
    private static final Pattern[] HOUR_WORD_PATTERNS = new Pattern[HOUR_WORDS.length];

    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HOUR_WORDS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(HOUR_WORDS[i]);
            HOUR_WORD_PATTERNS[i] = Pattern.compile("^(?:ال)?(?:" + HOUR_WORDS[i] + ")$", FLAGS);
        }
        HOUR_WORD_ALT = sb.toString();
    }

    private static final String AM_WORDS =
            "قبل\\s?الظهر|قبل\\s?الضهر|صباحا|صباح|الصباح|الصبح|بالصبح|صبحا|فجرا|الفجر|بالفجر|a\\.?m\\.?|ص";
    private static final String PM_WORDS =
            "مساءا|مساء|المساء|مسا|المسا|بالمسا|بالمساء|ظهرا|الظهر|بعد\\s?الظهر|الضهر|ضهرا|"
                    + "عصرا|العصر|بالعصر|ليلا|الليل|بالليل|p\\.?m\\.?|م";
    private static final String PERIOD = "(?:(?<am>" + AM_WORDS + ")|(?<pm>" + PM_WORDS + "))";

    private static final String MIN_PART =
            "(?:"
                    + "\\s*[:.]\\s*(?<mm>\\d{2})"
                    + "|\\s*و\\s*(?:ال)?(?<half>نص|نصف)"
                    + "|\\s*و\\s*(?:ال)?(?<quarter>ربع)"
                    + "|\\s*(?:الا|غير)\\s*(?:ال)?(?<lquarter>ربع)"
                    + "|\\s*و\\s*(?:ال)?(?<third>ثلث)"
                    + "|\\s*(?:الا|غير)\\s*(?:ال)?(?<lthird>ثلث)"
                    + "|\\s*و\\s*(?<plusm>\\d{1,2})\\s*(?:دقيقه|دقائق|دقايق|د)?"
                    + "|\\s*(?:الا|غير)\\s*(?<minusm>\\d{1,2})\\s*(?:دقيقه|دقائق|دقايق|د)?"
                    + ")?";

    /** "الساعة 2" ، "عند الساعة الثانية والنصف مساءً" ، "عالساعة 3 ونص" */
    private static final Pattern TIME_TRIGGERED = Pattern.compile(
            NB + "[وف]?(?:(?:عند|في|بتمام|تمام|حوالي|حوالى|قرابه)\\s+)?(?:ال|بال|عال|ع\\s?ال)?ساعه\\s*"
                    + "(?<h>\\d{1,2}|(?:ال)?(?:" + HOUR_WORD_ALT + "))"
                    + MIN_PART
                    + "(?:\\s*" + PERIOD + ")?" + NE, FLAGS);

    /** "at 3" ، "@ 14:30" ، "at 3:15pm" */
    private static final Pattern TIME_AT = Pattern.compile(
            NB + "(?:at|@)\\s*(?<h>\\d{1,2})(?:\\s*:\\s*(?<mm>\\d{2}))?(?:\\s*" + PERIOD + ")?" + NE, FLAGS);

    /** "14:30" ، "2:30 م" */
    private static final Pattern TIME_HM = Pattern.compile(
            "(?<![\\d/.:\\-])(?<h>\\d{1,2})\\s*:\\s*(?<mm>\\d{2})(?![\\d])(?![./\\-]\\d)(?:\\s*" + PERIOD + ")?"
                    + "(?![\\p{L}])", FLAGS);

    /** "3pm" ، "5 مساءً" ، "10 ص" */
    private static final Pattern TIME_PERIOD = Pattern.compile(
            "(?<![\\p{L}\\d/.:\\-])(?<h>\\d{1,2})\\s*" + PERIOD + NE, FLAGS);

    /** الوقت النسبي: "بعد ساعتين" ، "بعد نص ساعة" ، "in 30 minutes" */
    private static final Pattern REL_TIME = Pattern.compile(
            NB + "(?:بعد|خلال|in|within)\\s+"
                    + "(?:"
                    + "(?<halfh>نص|نصف)\\s*ساعه"
                    + "|(?<quarterh>ربع)\\s*ساعه"
                    + "|(?<n>\\d{1,3})?\\s*(?<u>ساعتين|ساعات|ساعه|دقيقتين|دقائق|دقايق|دقيقه|hours|hour|hrs|hr|minutes|minute|mins|min)"
                    + "(?<andhalf>\\s*و\\s*(?:ال)?(?:نص|نصف))?"
                    + ")" + NE, FLAGS);

    /** اليوم/بكرا/بعد بكرا... */
    private static final Pattern DAY_REL = Pattern.compile(
            NB + "[وف]?(?<k>"
                    + "بعد\\s+(?:بكره|بكرا|غد|غدا|الغد)|بعد\\s+يومين|day after tomorrow"
                    + "|بكره|بكرا|غدا|الغد|بالغد|غد"
                    + "|اليوم|الليله|هالليله|هاليوم|today|tonight|tomorrow"
                    + ")" + NE, FLAGS);

    /** بعد N أيام */
    private static final Pattern REL_DAYS = Pattern.compile(
            NB + "(?:بعد|خلال|in)\\s+(?<n>\\d{1,2})\\s*(?:يوم|ايام|days?)" + NE, FLAGS);

    /** يوم الخميس (القادم) / next monday */
    private static final Pattern WEEKDAY = Pattern.compile(
            NB + "[وف]?(?:(?:يوم|نهار|بيوم)\\s+)?(?:ال)"
                    + "(?<d>احد|اثنين|اتنين|ثلاثاء|ثلاثا|تلاتا|تلات|اربعاء|اربعا|خميس|جمعه|سبت)"
                    + "(?:\\s+(?<nx>القادم|القادمه|المقبل|المقبله|الجاي|الجايه|الياي))?" + NE
                    + "|" + NB + "(?<nxe>next\\s+|this\\s+|on\\s+)?"
                    + "(?<de>sunday|monday|tuesday|wednesday|thursday|friday|saturday)" + NE, FLAGS);

    /** 20/11 ، 20/11/2026 ، 2026-11-20 */
    private static final Pattern NUM_DATE = Pattern.compile(
            "(?:(?:بتاريخ|تاريخ|يوم|on)\\s*)?(?<![\\d/.\\-:])(?<a>\\d{1,4})\\s*[/.\\-]\\s*(?<b>\\d{1,2})"
                    + "(?:\\s*[/.\\-]\\s*(?<c>\\d{2,4}))?(?![\\d/.\\-:])", FLAGS);

    private static final String MONTHS_AR =
            "كانون\\s?الثاني|كانون\\s?الاول|تشرين\\s?الثاني|تشرين\\s?الاول|"
                    + "يناير|فبراير|شباط|مارس|اذار|ابريل|نيسان|مايو|ايار|يونيو|يونيه|حزيران|"
                    + "يوليو|يوليه|تموز|اغسطس|ايلول|سبتمبر|اكتوبر|نوفمبر|ديسمبر|اب";
    private static final String MONTHS_EN =
            "january|february|march|april|may|june|july|august|september|october|november|december|"
                    + "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec";

    /** 25 تشرين الثاني ، 3 من شهر آب ، 25 nov */
    private static final Pattern NAMED_DATE = Pattern.compile(
            "(?:(?:بتاريخ|تاريخ|يوم|on)\\s*)?(?<![\\d])(?<d>\\d{1,2})\\s*(?:th|st|nd|rd)?\\s*(?:من\\s+)?(?:شهر\\s+)?"
                    + "(?<mn>" + MONTHS_AR + "|" + MONTHS_EN + ")\\.?(?:\\s*(?<y>\\d{4}))?" + NE, FLAGS);

    /** nov 25 */
    private static final Pattern NAMED_DATE_EN = Pattern.compile(
            NB + "(?<mn>" + MONTHS_EN + ")\\.?\\s+(?<d>\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s*(?<y>\\d{4}))?" + NE, FLAGS);

    /** 15 شهر 11 */
    private static final Pattern MONTH_NUM_DATE = Pattern.compile(
            "(?<![\\d])(?<d>\\d{1,2})\\s*(?:من\\s+)?شهر\\s*(?<m>\\d{1,2})" + NE, FLAGS);

    /** كلمات تدل على نوع الموعد */
    private static final Pattern KEYWORD = Pattern.compile(
            "(?<![\\p{L}])(?:وبال|فبال|بال|وال|فال|لل|ال|و|ف|ب|ل)?"
                    + "(?<k>موعد|اجتماع|مقابله|لقاء|جلسه|زياره|محاضره|درس|مكالمه|اتصال|عشاء|غداء|"
                    + "فطور|حفله|مناسبه|امتحان|اختبار|تسليم|مراجعه|"
                    + "meeting|call|interview|appointment|dinner|lunch|session|class|exam)"
                    + "(?:[\\p{L}]{0,3})(?![\\p{L}])", FLAGS);

    private static final Pattern WITH = Pattern.compile("(?<![\\p{L}])(?:مع|with)(?![\\p{L}])", FLAGS);

    private static final String[][] KEYWORD_CANON = {
            {"موعد", "موعد"}, {"اجتماع", "اجتماع"}, {"مقابله", "مقابلة"}, {"لقاء", "لقاء"},
            {"جلسه", "جلسة"}, {"زياره", "زيارة"}, {"محاضره", "محاضرة"}, {"درس", "درس"},
            {"مكالمه", "مكالمة"}, {"اتصال", "اتصال"}, {"عشاء", "عشاء"}, {"غداء", "غداء"},
            {"فطور", "فطور"}, {"حفله", "حفلة"}, {"مناسبه", "مناسبة"}, {"امتحان", "امتحان"},
            {"اختبار", "اختبار"}, {"تسليم", "تسليم"}, {"مراجعه", "مراجعة"},
    };

    /** كلمات تنهي العنوان */
    private static final java.util.Set<String> TITLE_STOP = new java.util.HashSet<>(java.util.Arrays.asList(
            "بتاريخ", "تاريخ", "لمناقشه", "لبحث", "لمتابعه", "بخصوص", "حول", "بشان", "بشأن", "وذلك", "يرجي",
            "الرجاء", "ارجو", "نرجو", "يرجى", "الرجا", "please", "regarding", "about", "to", "on", "at",
            "يوم", "نهار", "الساعه", "ساعه", "سيكون", "سيعقد", "ستعقد", "سيتم", "يكون", "هو", "هي", "is", "will", "be", "has", "starts", "لاجل", "علشان", "مشان", "منشان", "لكي", "حتى", "كي", "و"));

    /** كلمات افتتاحية تُحذف من بداية العنوان الاحتياطي */
    private static final java.util.Set<String> LEADING_FILLER = new java.util.HashSet<>(java.util.Arrays.asList(
            "لدي", "لديك", "لديكم", "لديه", "لديها", "عندي", "عندك", "عندكم", "عندو", "عنده", "عندها", "عنا",
            "عندنا", "لدينا", "يوجد", "هناك", "في", "تذكير", "نذكركم", "نذكرك", "نود", "نحيطكم", "علما", "بان",
            "ان", "انه", "انو", "مرحبا", "اهلا", "السلام", "عليكم", "صباح", "الخير", "مساء", "استاذ", "اخي",
            "hi", "hello", "dear", "reminder", "you", "have", "a", "an", "the", "there", "is", "your"));

    // ---------------------------------------------------------------------------------------
    // التحليل
    // ---------------------------------------------------------------------------------------

    public static Result parse(String text) {
        return parse(text, LocalDateTime.now());
    }

    public static Result parse(String rawText, LocalDateTime now) {
        Result r = new Result();
        String clean = clean(rawText == null ? "" : rawText);
        String norm = normalize(clean);
        char[] residual = clean.toCharArray();       // النص الأصلي مع إخفاء ما تمت قراءته
        char[] residualNorm = norm.toCharArray();

        // ---------- 1) وقت نسبي (بعد ساعتين...) ----------
        LocalDateTime relative = null;
        Matcher m = REL_TIME.matcher(norm);
        if (m.find()) {
            long minutes;
            if (m.group("halfh") != null) {
                minutes = 30;
            } else if (m.group("quarterh") != null) {
                minutes = 15;
            } else {
                String u = m.group("u").toLowerCase(Locale.ROOT);
                int n = m.group("n") != null ? Integer.parseInt(m.group("n")) : 1;
                if (u.equals("ساعتين")) { minutes = 120; }
                else if (u.equals("دقيقتين")) { minutes = 2; }
                else if (u.startsWith("ساع") || u.startsWith("h")) { minutes = n * 60L; }
                else { minutes = n; }
                if (m.group("andhalf") != null) minutes += 30;
            }
            relative = now.plusMinutes(minutes).truncatedTo(ChronoUnit.MINUTES);
            mask(residual, residualNorm, m.start(), m.end());
        }

        // ---------- 2) الوقت ----------
        TimeHit time = null;
        Pattern[] timePatterns = {TIME_TRIGGERED, TIME_AT, TIME_HM, TIME_PERIOD};
        for (Pattern p : timePatterns) {
            Matcher tm = p.matcher(new String(residualNorm));
            while (tm.find()) {
                TimeHit hit = readTime(tm);
                if (hit != null) {
                    time = hit;
                    mask(residual, residualNorm, tm.start(), tm.end());
                    break;
                }
            }
            if (time != null) break;
        }

        // ---------- 3) التاريخ ----------
        LocalDate today = now.toLocalDate();
        LocalDate date = null;
        boolean nightHint = false;
        DayOfWeek weekdayToday = null; // اسم يوم يطابق اليوم الحالي بدون "القادم"
        String resNorm = new String(residualNorm);

        Matcher dm = NUM_DATE.matcher(resNorm);
        while (date == null && dm.find()) {
            date = readNumericDate(dm, today);
            if (date != null) mask(residual, residualNorm, dm.start(), dm.end());
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher nm = NAMED_DATE.matcher(resNorm);
            if (nm.find()) {
                date = namedDate(nm.group("d"), nm.group("mn"), nm.group("y"), today);
                if (date != null) mask(residual, residualNorm, nm.start(), nm.end());
            }
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher nm = NAMED_DATE_EN.matcher(resNorm);
            if (nm.find()) {
                date = namedDate(nm.group("d"), nm.group("mn"), nm.group("y"), today);
                if (date != null) mask(residual, residualNorm, nm.start(), nm.end());
            }
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher nm = MONTH_NUM_DATE.matcher(resNorm);
            if (nm.find()) {
                date = dayMonth(Integer.parseInt(nm.group("d")), Integer.parseInt(nm.group("m")), null, today);
                if (date != null) mask(residual, residualNorm, nm.start(), nm.end());
            }
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher rm = DAY_REL.matcher(resNorm);
            if (rm.find()) {
                String k = rm.group("k").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (k.startsWith("بعد") || k.equals("day after tomorrow")) {
                    date = today.plusDays(2);
                } else if (k.equals("اليوم") || k.equals("هاليوم") || k.equals("today")) {
                    date = today;
                } else if (k.equals("الليله") || k.equals("هالليله") || k.equals("tonight")) {
                    date = today;
                    nightHint = true;
                } else {
                    date = today.plusDays(1);
                }
                mask(residual, residualNorm, rm.start(), rm.end());
            }
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher rd = REL_DAYS.matcher(resNorm);
            if (rd.find()) {
                date = today.plusDays(Integer.parseInt(rd.group("n")));
                mask(residual, residualNorm, rd.start(), rd.end());
            }
        }
        if (date == null) {
            resNorm = new String(residualNorm);
            Matcher wm = WEEKDAY.matcher(resNorm);
            if (wm.find()) {
                DayOfWeek dow;
                boolean next;
                if (wm.group("d") != null) {
                    dow = arabicDay(wm.group("d"));
                    next = wm.group("nx") != null;
                } else {
                    dow = DayOfWeek.valueOf(wm.group("de").toUpperCase(Locale.ROOT));
                    String nx = wm.group("nxe");
                    next = nx != null && nx.trim().equalsIgnoreCase("next");
                }
                if (dow != null) {
                    if (dow == today.getDayOfWeek()) {
                        if (next) {
                            date = today.plusDays(7);
                        } else {
                            date = today;
                            weekdayToday = dow;
                        }
                    } else {
                        date = today.with(TemporalAdjusters.next(dow));
                    }
                    mask(residual, residualNorm, wm.start(), wm.end());
                }
            }
        }

        // ---------- 4) دمج النتيجة ----------
        r.dateFound = date != null;
        r.timeFound = time != null || relative != null;

        if (relative != null && time == null) {
            r.when = relative;
            if (date != null && !date.equals(today)) {
                r.when = LocalDateTime.of(date, relative.toLocalTime());
            }
        } else if (time != null) {
            r.when = resolveTime(time, date, now, nightHint);
            if (weekdayToday != null && !r.when.isAfter(now)) {
                r.when = r.when.plusDays(7);
            }
        } else if (date != null) {
            r.when = LocalDateTime.of(date, LocalTime.of(9, 0));
        } else {
            // لا وقت ولا تاريخ: اقتراح بعد ساعة (مقرّباً لربع الساعة)
            LocalDateTime base = now.plusHours(1).truncatedTo(ChronoUnit.MINUTES);
            int add = (15 - base.getMinute() % 15) % 15;
            r.when = base.plusMinutes(add);
        }

        // ---------- 5) العنوان ----------
        r.title = buildTitle(new String(residual), new String(residualNorm));
        return r;
    }

    // ---------------------------------------------------------------------------------------
    // الوقت
    // ---------------------------------------------------------------------------------------

    private static final class TimeHit {
        int hour;
        int minute;
        int period; // 0 = غير محدد ، 1 = صباحاً ، 2 = مساءً
        boolean noon; // كلمة "الظهر"
    }

    private static boolean has(Matcher m, String group) {
        try {
            return m.group(group) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String grp(Matcher m, String group) {
        try {
            return m.group(group);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TimeHit readTime(Matcher m) {
        String hs = m.group("h");
        int h;
        if (hs.matches("\\d{1,2}")) {
            h = Integer.parseInt(hs);
        } else {
            h = hourFromWord(hs);
        }
        if (h < 0 || h > 23) return null;

        int min = 0;
        String mm = grp(m, "mm");
        if (mm != null) {
            min = Integer.parseInt(mm);
            if (min > 59) return null;
        } else if (has(m, "half")) {
            min = 30;
        } else if (has(m, "quarter")) {
            min = 15;
        } else if (has(m, "third")) {
            min = 20;
        } else if (has(m, "lquarter")) {
            h -= 1;
            min = 45;
        } else if (has(m, "lthird")) {
            h -= 1;
            min = 40;
        } else if (grp(m, "plusm") != null) {
            min = Integer.parseInt(grp(m, "plusm"));
            if (min > 59) return null;
        } else if (grp(m, "minusm") != null) {
            int x = Integer.parseInt(grp(m, "minusm"));
            if (x <= 0 || x > 59) return null;
            h -= 1;
            min = 60 - x;
        }
        if (h < 0) h += 12;

        TimeHit t = new TimeHit();
        t.hour = h;
        t.minute = min;
        String am = grp(m, "am");
        String pm = grp(m, "pm");
        if (am != null) {
            t.period = 1;
        } else if (pm != null) {
            t.period = 2;
            String p = pm.replaceAll("\\s+", "");
            t.noon = p.contains("ظهر") || p.contains("ضهر");
        }
        // "2 م" تُقبل فقط إذا كانت الساعة 1..12
        if (t.period != 0 && h > 12) t.period = 0;
        return t;
    }

    private static int hourFromWord(String w) {
        String s = w.trim();
        for (int i = 0; i < HOUR_WORD_PATTERNS.length; i++) {
            if (HOUR_WORD_PATTERNS[i].matcher(s).matches()) return HOUR_VALUES[i];
        }
        return -1;
    }

    private static LocalDateTime resolveTime(TimeHit t, LocalDate date, LocalDateTime now, boolean nightHint) {
        boolean explicitDate = date != null;
        LocalDate d = explicitDate ? date : now.toLocalDate();
        int h = t.hour;

        if (t.period == 1) { // صباحاً
            if (h == 12) h = 0;
            return rollIfPast(LocalDateTime.of(d, LocalTime.of(h, t.minute)), explicitDate, now);
        }
        if (t.period == 2) { // مساءً / ظهراً
            if (h < 12 && !(t.noon && h >= 11)) h += 12;
            return rollIfPast(LocalDateTime.of(d, LocalTime.of(h, t.minute)), explicitDate, now);
        }
        // بدون تحديد صباح/مساء
        if (h >= 13 || h == 0) {
            return rollIfPast(LocalDateTime.of(d, LocalTime.of(h, t.minute)), explicitDate, now);
        }
        if (h == 12) {
            return rollIfPast(LocalDateTime.of(d, LocalTime.of(12, t.minute)), explicitDate, now);
        }
        if (h <= 6 || nightHint) {
            // 1..6 تعني غالباً بعد الظهر (الساعة 2 = 14:00)
            return rollIfPast(LocalDateTime.of(d, LocalTime.of(h + 12, t.minute)), explicitDate, now);
        }
        // 7..11: صباحاً، وإن كان قد مضى اليوم فنجرب المساء
        LocalDateTime morning = LocalDateTime.of(d, LocalTime.of(h, t.minute));
        if (explicitDate || morning.isAfter(now)) {
            return morning;
        }
        LocalDateTime evening = LocalDateTime.of(d, LocalTime.of(h + 12, t.minute));
        if (evening.isAfter(now)) return evening;
        return morning.plusDays(1);
    }

    private static LocalDateTime rollIfPast(LocalDateTime dt, boolean explicitDate, LocalDateTime now) {
        if (!explicitDate && !dt.isAfter(now)) return dt.plusDays(1);
        return dt;
    }

    // ---------------------------------------------------------------------------------------
    // التاريخ
    // ---------------------------------------------------------------------------------------

    private static LocalDate readNumericDate(Matcher m, LocalDate today) {
        String a = m.group("a");
        int b = Integer.parseInt(m.group("b"));
        String c = m.group("c");
        if (a.length() == 4) {
            // yyyy-mm-dd
            if (c == null) return null;
            return safeDate(Integer.parseInt(a), b, Integer.parseInt(c));
        }
        if (a.length() > 2) return null;
        int d = Integer.parseInt(a);
        Integer y = null;
        if (c != null) {
            int yy = Integer.parseInt(c);
            if (c.length() == 2) yy += 2000;
            else if (c.length() != 4) return null;
            y = yy;
        }
        return dayMonth(d, b, y, today);
    }

    private static LocalDate namedDate(String d, String mn, String y, LocalDate today) {
        int month = monthFromName(mn);
        if (month < 1) return null;
        return dayMonth(Integer.parseInt(d), month, y == null ? null : Integer.parseInt(y), today);
    }

    private static LocalDate dayMonth(int d, int month, Integer year, LocalDate today) {
        if (month < 1 || month > 12 || d < 1 || d > 31) return null;
        if (year != null) return safeDate(year, month, d);
        LocalDate dt = safeDate(today.getYear(), month, d);
        if (dt == null) return null;
        if (dt.isBefore(today)) dt = safeDate(today.getYear() + 1, month, d);
        return dt;
    }

    private static LocalDate safeDate(int y, int m, int d) {
        try {
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private static int monthFromName(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        switch (s) {
            case "كانونالثاني": case "يناير": case "january": case "jan": return 1;
            case "شباط": case "فبراير": case "february": case "feb": return 2;
            case "اذار": case "مارس": case "march": case "mar": return 3;
            case "نيسان": case "ابريل": case "april": case "apr": return 4;
            case "ايار": case "مايو": case "may": return 5;
            case "حزيران": case "يونيو": case "يونيه": case "june": case "jun": return 6;
            case "تموز": case "يوليو": case "يوليه": case "july": case "jul": return 7;
            case "اب": case "اغسطس": case "august": case "aug": return 8;
            case "ايلول": case "سبتمبر": case "september": case "sep": case "sept": return 9;
            case "تشرينالاول": case "اكتوبر": case "october": case "oct": return 10;
            case "تشرينالثاني": case "نوفمبر": case "november": case "nov": return 11;
            case "كانونالاول": case "ديسمبر": case "december": case "dec": return 12;
            default: return -1;
        }
    }

    private static DayOfWeek arabicDay(String d) {
        switch (d) {
            case "احد": return DayOfWeek.SUNDAY;
            case "اثنين": case "اتنين": return DayOfWeek.MONDAY;
            case "ثلاثاء": case "ثلاثا": case "تلاتا": case "تلات": return DayOfWeek.TUESDAY;
            case "اربعاء": case "اربعا": return DayOfWeek.WEDNESDAY;
            case "خميس": return DayOfWeek.THURSDAY;
            case "جمعه": return DayOfWeek.FRIDAY;
            case "سبت": return DayOfWeek.SATURDAY;
            default: return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // العنوان
    // ---------------------------------------------------------------------------------------

    private static String buildTitle(String residual, String residualNorm) {
        String title = null;

        Matcher km = KEYWORD.matcher(residualNorm);
        if (km.find()) {
            String k = km.group("k").toLowerCase(Locale.ROOT);
            String head = null;
            for (String[] kv : KEYWORD_CANON) {
                if (kv[0].equals(k)) { head = kv[1]; break; }
            }
            if (head == null) {
                // كلمة إنجليزية: نأخذها كما كُتبت مع حرف أول كبير
                String orig = residual.substring(km.start("k"), km.end("k"));
                head = orig.substring(0, 1).toUpperCase(Locale.ROOT) + orig.substring(1);
            }
            String tail = collectTokens(residual, residualNorm, km.end(), 6, true);
            if (tail.isEmpty()) {
                // ربما "مع ..." في مكان آخر من الرسالة
                String with = withPhrase(residual, residualNorm);
                if (with != null) tail = with;
            }
            title = tail.isEmpty() ? head : head + " " + tail;
        } else {
            String with = withPhrase(residual, residualNorm);
            if (with != null) {
                title = (with.toLowerCase(Locale.ROOT).startsWith("with") ? "Meeting " : "موعد ") + with;
            }
        }

        if (title == null) title = fallbackTitle(residual, residualNorm);
        title = title.replace(SENTINEL, ' ').replaceAll("\\s+", " ").trim();
        title = title.replaceAll("[\\s،,.:;!؟?\\-–—]+$", "").trim();
        if (title.length() > 60) title = title.substring(0, 60).trim() + "…";
        if (title.isEmpty()) title = "موعد";
        return title;
    }

    private static String withPhrase(String residual, String residualNorm) {
        Matcher wm = WITH.matcher(residualNorm);
        if (!wm.find()) return null;
        String rest = collectTokens(residual, residualNorm, wm.end(), 4, false);
        if (rest.isEmpty()) return null;
        return residual.substring(wm.start(), wm.end()) + " " + rest;
    }

    /**
     * يجمع حتى maxTokens كلمة تبدأ من موضع from، ويتوقف عند علامات الترقيم أو كلمات التوقف
     * أو عند جزء تمت قراءته (تاريخ/وقت) — إلا إذا كان allowSkipFirstGap صحيحاً ولم تُجمع أي كلمة بعد.
     */
    private static String collectTokens(String residual, String residualNorm, int from, int maxTokens,
                                        boolean allowSkipFirstGap) {
        StringBuilder out = new StringBuilder();
        int i = from;
        int n = residual.length();
        int count = 0;
        boolean skippedGap = false;
        while (i < n && count < maxTokens) {
            char c = residual.charAt(i);
            if (c == SENTINEL) {
                if (count == 0 && allowSkipFirstGap && !skippedGap) {
                    while (i < n && (residual.charAt(i) == SENTINEL || Character.isWhitespace(residual.charAt(i)))) i++;
                    skippedGap = true;
                    continue;
                }
                break;
            }
            if (c == '\n' || c == '\r') break;
            if (Character.isWhitespace(c)) { i++; continue; }
            if ("،,؛;!؟?:()[]\"«»".indexOf(c) >= 0) break;
            int j = i;
            while (j < n && !Character.isWhitespace(residual.charAt(j)) && residual.charAt(j) != SENTINEL
                    && "،,؛;!؟?:()[]\"«»".indexOf(residual.charAt(j)) < 0) j++;
            String tok = residual.substring(i, j);
            String tokNorm = residualNorm.substring(i, j).toLowerCase(Locale.ROOT);
            String tokNormBare = tokNorm.replaceAll("[.]+$", "");
            if (TITLE_STOP.contains(tokNormBare)) break;
            boolean endsSentence = tok.endsWith(".") && tokNormBare.length() > 2;
            out.append(out.length() == 0 ? "" : " ").append(endsSentence ? tok.substring(0, tok.length() - 1) : tok);
            count++;
            i = j;
            if (endsSentence) break;
        }
        return out.toString().trim();
    }

    private static String fallbackTitle(String residual, String residualNorm) {
        // أول جملة غير فارغة بعد حذف كلمات الافتتاح
        String[] parts = residual.split("[\\n\\r!؟?،,؛;" + SENTINEL + "]");
        String[] partsNorm = residualNorm.split("[\\n\\r!؟?،,؛;" + SENTINEL + "]");
        for (int p = 0; p < parts.length && p < partsNorm.length; p++) {
            String[] toks = parts[p].trim().split("\\s+");
            String[] toksN = partsNorm[p].trim().split("\\s+");
            int k = 0;
            while (k < toks.length && k < toksN.length
                    && LEADING_FILLER.contains(toksN[k].toLowerCase(Locale.ROOT).replaceAll("[:.]+$", ""))) k++;
            StringBuilder sb = new StringBuilder();
            for (int t = k; t < toks.length && t < k + 7; t++) {
                if (toks[t].isEmpty()) continue;
                sb.append(sb.length() == 0 ? "" : " ").append(toks[t]);
            }
            String s = sb.toString().trim();
            if (s.replaceAll("[^\\p{L}]", "").length() >= 2) return s;
        }
        return "موعد";
    }

    // ---------------------------------------------------------------------------------------
    // التطبيع
    // ---------------------------------------------------------------------------------------

    /** حذف التشكيل والرموز غير المرئية (يغيّر الطول). */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'ً' && c <= 'ْ') || c == 'ٰ' || c == 'ـ'
                    || c == '​' || c == '‌' || c == '‍' || c == '‎' || c == '‏'
                    || c == '؜' || c == '‪' || c == '‫' || c == '‬' || c == '‭'
                    || c == '‮' || c == '﻿') {
                continue;
            }
            if (c == ' ' || c == ' ' || c == ' ' || c == '\t') c = ' ';
            if (c == SENTINEL) c = '|';
            sb.append(c);
        }
        return sb.toString();
    }

    /** تطبيع حرفاً بحرف (لا يغيّر الطول) لتسهيل المطابقة. */
    static String normalize(String s) {
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            char c = a[i];
            if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') c = 'ا'; // ا
            else if (c == 'ة') c = 'ه'; // ة → ه
            else if (c == 'ى') c = 'ي'; // ى → ي
            else if (c == 'ی') c = 'ي'; // ی فارسية → ي
            else if (c == 'ک') c = 'ك'; // ک → ك
            else if (c == '٫') c = '.';      // ٫
            else if (c == '：') c = ':';
            else c = Character.toLowerCase(c);
            a[i] = c;
        }
        return new String(a);
    }

    private static void mask(char[] residual, char[] residualNorm, int start, int end) {
        for (int i = start; i < end && i < residual.length; i++) {
            residual[i] = SENTINEL;
            residualNorm[i] = SENTINEL;
        }
    }
}
