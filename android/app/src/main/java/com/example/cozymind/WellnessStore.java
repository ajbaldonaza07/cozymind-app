package com.example.cozymind;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Small on-device wellness data shared by Calm Start and the main app:
 * daily affirmations, the water tracker, the check-in streak and the daily reminder.
 */
public final class WellnessStore {

    public static final int WATER_GOAL = 8;

    private static final String PREFS = "cozymind_calm_start";
    private static final String KEY_CHECKIN_DAYS = "checkin_days";
    private static final String KEY_REMINDER_ON = "reminder_on";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final int REMINDER_REQUEST = 4201;

    static final String[] AFFIRMATIONS = {
            "You are allowed to rest. Growth still happens on quiet days.",
            "This feeling is a visitor. It will not stay forever.",
            "You are doing better than you think you are.",
            "Small steps are still steps forward.",
            "You deserve the same kindness you give to others.",
            "It is okay to not have everything figured out.",
            "Breathe. You have survived every hard day so far.",
            "Your worth is not measured by your productivity.",
            "You can start again at any moment of the day.",
            "Asking for help is a sign of strength.",
            "Be gentle with yourself. You are learning.",
            "One thing at a time is enough.",
            "You belong here, exactly as you are.",
            "Progress, not perfection.",
            "Today, you only need to do your best, whatever that looks like.",
            "Your feelings are valid, and they matter.",
            "Peace begins with one slow breath.",
            "You are more than your worst day.",
            "It is okay to say no to protect your peace.",
            "You are worthy of care, especially your own.",
    };

    static final String[] JOURNAL_PROMPTS = {
            "What is one small thing that brought you peace today?",
            "What made you smile today, even a little?",
            "What is something you are proud of this week?",
            "What is weighing on your mind right now?",
            "Who made your day easier, and how?",
            "What do you need more of this week?",
            "Describe a place where you feel safe.",
            "What would you tell a friend who feels the way you do today?",
            "What are three things you are grateful for right now?",
            "What is one kind thing you can do for yourself tomorrow?",
            "What drained your energy today, and what restored it?",
            "Write a short letter to your future self.",
    };

    private WellnessStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String dayKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static String todayKey() {
        return dayKey(new Date());
    }

    // ---------- Affirmations ----------

    static int todaysAffirmationIndex() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % AFFIRMATIONS.length;
    }

    static String affirmationForToday() {
        return AFFIRMATIONS[todaysAffirmationIndex()];
    }

    static String journalPrompt(int index) {
        return JOURNAL_PROMPTS[Math.floorMod(index, JOURNAL_PROMPTS.length)];
    }

    static int todaysPromptIndex() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % JOURNAL_PROMPTS.length;
    }

    // ---------- Water ----------

    static int waterToday(Context context) {
        return prefs(context).getInt("water_" + todayKey(), 0);
    }

    static int changeWater(Context context, int delta) {
        int glasses = Math.max(0, Math.min(WATER_GOAL, waterToday(context) + delta));
        prefs(context).edit().putInt("water_" + todayKey(), glasses).apply();
        return glasses;
    }

    // ---------- Check-in streak ----------

    /** Remembers that the person checked in today. */
    static void recordCheckInToday(Context context) {
        addCheckInDay(context, todayKey());
    }

    private static void addCheckInDay(Context context, String day) {
        Set<String> days = new HashSet<>(prefs(context).getStringSet(KEY_CHECKIN_DAYS, new HashSet<>()));
        if (days.add(day)) prefs(context).edit().putStringSet(KEY_CHECKIN_DAYS, days).apply();
    }

    /** Adds check-ins from the server ("yyyy-MM-dd HH:mm:ss", UTC) so the streak survives reinstalls. */
    static void mergeServerCheckIn(Context context, String utcDateTime) {
        Date date = parseUtc(utcDateTime);
        if (date != null) addCheckInDay(context, dayKey(date));
    }

    /** Parses the server's UTC timestamps; returns null when the text is not a date. */
    static Date parseUtc(String utcDateTime) {
        if (utcDateTime == null || utcDateTime.length() < 10) return null;
        String pattern = utcDateTime.length() >= 19 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return format.parse(utcDateTime.length() >= 19 ? utcDateTime.substring(0, 19) : utcDateTime.substring(0, 10));
        } catch (ParseException exception) {
            return null;
        }
    }

    /** Days in a row with at least one check-in, counting back from today (or yesterday). */
    static int checkInStreak(Context context) {
        Set<String> days = prefs(context).getStringSet(KEY_CHECKIN_DAYS, new HashSet<>());
        Calendar day = Calendar.getInstance();
        if (!days.contains(dayKey(day.getTime()))) day.add(Calendar.DAY_OF_YEAR, -1);
        int streak = 0;
        while (days.contains(dayKey(day.getTime()))) {
            streak++;
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    static String streakLabel(Context context) {
        int streak = checkInStreak(context);
        if (streak == 0) return "🌱 Check in today to start a streak";
        return "🔥 " + streak + "-day check-in streak";
    }

    // ---------- Daily reminder ----------

    static boolean reminderOn(Context context) {
        return prefs(context).getBoolean(KEY_REMINDER_ON, false);
    }

    static int reminderHour(Context context) {
        return prefs(context).getInt(KEY_REMINDER_HOUR, 20);
    }

    static int reminderMinute(Context context) {
        return prefs(context).getInt(KEY_REMINDER_MINUTE, 0);
    }

    static String reminderTimeLabel(Context context) {
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, reminderHour(context));
        time.set(Calendar.MINUTE, reminderMinute(context));
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(time.getTime());
    }

    static void setReminder(Context context, boolean on, int hour, int minute) {
        prefs(context).edit()
                .putBoolean(KEY_REMINDER_ON, on)
                .putInt(KEY_REMINDER_HOUR, hour)
                .putInt(KEY_REMINDER_MINUTE, minute)
                .apply();
        scheduleReminder(context);
    }

    /** Schedules (or cancels) the once-a-day gentle reminder. Inexact, so it needs no special permission. */
    static void scheduleReminder(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_REMIND);
        PendingIntent pending = PendingIntent.getBroadcast(context, REMINDER_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!reminderOn(context)) return;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, reminderHour(context));
        next.set(Calendar.MINUTE, reminderMinute(context));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pending);
    }
}
