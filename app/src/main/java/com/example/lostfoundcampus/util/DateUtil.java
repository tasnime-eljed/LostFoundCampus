package com.example.lostfoundcampus.util;

import android.content.Context;

import com.example.lostfoundcampus.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Formats de date en français. Les motifs ne sont pas des textes visibles. */
public final class DateUtil {

    private static final Locale FR = Locale.FRANCE;

    private DateUtil() {
    }

    /** Nombre de jours calendaires entre la date et aujourd'hui (0 = aujourd'hui). */
    public static int daysAgo(long time) {
        Calendar then = startOfDay(time);
        Calendar today = startOfDay(System.currentTimeMillis());
        long diff = today.getTimeInMillis() - then.getTimeInMillis();
        return (int) Math.round(diff / (24.0 * 60 * 60 * 1000));
    }

    /** « Aujourd'hui », « Hier », « Il y a 3 j », puis « 9 nov. ». */
    public static String relativeShort(Context c, long time) {
        int d = daysAgo(time);
        if (d <= 0) return c.getString(R.string.date_today);
        if (d == 1) return c.getString(R.string.date_yesterday);
        if (d < 7) return c.getString(R.string.date_days_ago, d);
        return new SimpleDateFormat("d MMM", FR).format(new Date(time));
    }

    /** « Publiée il y a 2 jours ». */
    public static String published(Context c, long time) {
        int d = daysAgo(time);
        if (d <= 0) return c.getString(R.string.published_today);
        if (d == 1) return c.getString(R.string.published_yesterday);
        return c.getString(R.string.published_days_ago, d);
    }

    /** « 9 novembre 2026 ». */
    public static String longDate(long time) {
        return new SimpleDateFormat("d MMMM yyyy", FR).format(new Date(time));
    }

    /** « 9 nov. 2026 ». */
    public static String shortDate(long time) {
        return new SimpleDateFormat("d MMM yyyy", FR).format(new Date(time));
    }

    /** « 10:42 ». */
    public static String time(long time) {
        return new SimpleDateFormat("HH:mm", FR).format(new Date(time));
    }

    /** Heure du jour, « Hier », ou jour abrégé (« Lun. »). */
    public static String conversationTime(Context c, long time) {
        int d = daysAgo(time);
        if (d <= 0) return time(time);
        if (d == 1) return c.getString(R.string.time_yesterday);
        if (d < 7) return capitalize(new SimpleDateFormat("EEE", FR).format(new Date(time)));
        return new SimpleDateFormat("d MMM", FR).format(new Date(time));
    }

    /**
     * « 2026-11-16T15:04:05.123+00:00 » (timestamptz PostgREST) → millisecondes.
     * Le décalage horaire (+01:00, Z…) est pris en compte : sans lui, on suppose UTC.
     */
    public static long parseIso(String iso) {
        if (iso == null || iso.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.substring(0, 19).replace(' ', 'T'));
            if (d == null) return 0;
            return d.getTime() - offsetMillis(iso.substring(19));
        } catch (ParseException e) {
            return 0;
        }
    }

    /** Décalage « +01:00 », « -0530 », « +02 » ou « Z » à la fin d'une date ISO (après les fractions). */
    private static long offsetMillis(String tail) {
        int i = 0;
        if (i < tail.length() && tail.charAt(i) == '.') {
            i++;
            while (i < tail.length() && Character.isDigit(tail.charAt(i))) i++;
        }
        if (i >= tail.length()) return 0;
        char sign = tail.charAt(i);
        if (sign != '+' && sign != '-') return 0;
        String digits = tail.substring(i + 1).replace(":", "");
        if (digits.length() < 2) return 0;
        try {
            int hours = Integer.parseInt(digits.substring(0, 2));
            int minutes = digits.length() >= 4 ? Integer.parseInt(digits.substring(2, 4)) : 0;
            long ms = (hours * 60L + minutes) * 60_000L;
            return sign == '-' ? -ms : ms;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** « 2026-11-09 » (colonne date) → midi heure locale de ce jour. */
    public static long parseDay(String day) {
        if (day == null || day.length() < 10) return 0;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(day.substring(0, 10));
            if (d == null) return 0;
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            c.set(Calendar.HOUR_OF_DAY, 12);
            return c.getTimeInMillis();
        } catch (ParseException e) {
            return 0;
        }
    }

    /** Jour local au format SQL « 2026-11-09 ». */
    public static String formatDay(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(time));
    }

    private static Calendar startOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(FR) + s.substring(1);
    }
}
