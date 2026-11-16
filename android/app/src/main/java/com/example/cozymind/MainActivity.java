package com.example.cozymind;

import android.app.Activity;
import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cozymind.data.AppDatabase;
import com.example.cozymind.data.User;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity {
    private enum Screen {
        LOGIN, SIGN_UP, WELCOME, ONBOARDING_FOCUS, ONBOARDING_MOOD, ONBOARDING_ROUTINE,
        DASHBOARD, TOOLKIT, MOOD, JOURNAL, BREATHE, PROFILE, COMFORT_WORDS, GOALS, SLEEP
    }

    private FrameLayout screenContainer;
    private AppDatabase database;
    private ExecutorService databaseExecutor;
    private CozymindApi api;
    private CountDownTimer breathingTimer;
    private int breathingRound;
    private long currentUserId;
    private String currentUserName = "";
    private boolean serverSessionActive;
    private int onboardingMoodSeverity;
    private Screen currentScreen = Screen.LOGIN;
    private static final int REQUEST_NOTIFICATIONS = 31;
    private int journalPromptIndex;
    private String selectedJournalTag = "";
    private List<CozymindApi.Journal> loadedJournals = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        screenContainer = findViewById(R.id.screen_container);
        applySystemBarInsets();
        database = AppDatabase.getInstance(getApplicationContext());
        databaseExecutor = Executors.newSingleThreadExecutor();
        api = new CozymindApi();
        restoreSession();
        migrateLegacyAccount();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBackNavigation);
        }
        showLogin();
    }

    /** Keeps every screen below the camera/status area and above gesture navigation. */
    private void applySystemBarInsets() {
        screenContainer.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        screenContainer.requestApplyInsets();
    }

    private void showLogin() {
        cancelBreathingTimer();
        currentScreen = Screen.LOGIN;
        showScreen(R.layout.screen_login);

        EditText email = findViewById(R.id.login_email);
        EditText password = findViewById(R.id.login_password);
        email.setText(getPreferences(MODE_PRIVATE).getString("last_email",
                getPreferences(MODE_PRIVATE).getString("email", "")));

        findViewById(R.id.open_signup).setOnClickListener(view -> showSignUp());
        findViewById(R.id.forgot_password).setOnClickListener(view ->
                Toast.makeText(this, "Password recovery can be connected to an email service later.", Toast.LENGTH_LONG).show());
        findViewById(R.id.login_button).setOnClickListener(view -> attemptLogin(email, password));
    }

    private void showSignUp() {
        currentScreen = Screen.SIGN_UP;
        showScreen(R.layout.screen_signup);

        EditText name = findViewById(R.id.signup_name);
        EditText username = findViewById(R.id.signup_username);
        EditText email = findViewById(R.id.signup_email);
        EditText phoneNumber = findViewById(R.id.signup_phone_number);
        EditText password = findViewById(R.id.signup_password);
        EditText confirmPassword = findViewById(R.id.signup_confirm_password);
        CheckBox terms = findViewById(R.id.terms_checkbox);

        findViewById(R.id.back_to_login).setOnClickListener(view -> showLogin());
        findViewById(R.id.login_from_signup).setOnClickListener(view -> showLogin());
        findViewById(R.id.register_button).setOnClickListener(view ->
                register(name, username, email, phoneNumber, password, confirmPassword, terms));
    }

    private void showDashboard(String name) {
        cancelBreathingTimer();
        currentScreen = Screen.DASHBOARD;
        showScreen(R.layout.screen_dashboard);

        TextView greeting = findViewById(R.id.dashboard_greeting);
        greeting.setText("Welcome Back\n" + name + "!");
        TextView subtitle = findViewById(R.id.dashboard_subtitle);
        subtitle.setText(timeOfDayGreeting() + ", " + name + ".");
        ((TextView) findViewById(R.id.dashboard_streak)).setText(WellnessStore.streakLabel(this));
        TextView waterStatus = findViewById(R.id.dashboard_water_status);
        showDashboardWater(waterStatus, WellnessStore.waterToday(this));
        findViewById(R.id.dashboard_water_tile).setOnClickListener(view -> {
            int glasses = WellnessStore.changeWater(this, 1);
            showDashboardWater(waterStatus, glasses);
            Toast.makeText(this, glasses >= WellnessStore.WATER_GOAL
                    ? "Water goal reached today! 🎉" : "💧 Logged. " + glasses + " of "
                    + WellnessStore.WATER_GOAL + " glasses.", Toast.LENGTH_SHORT).show();
        });
        TextView profile = findViewById(R.id.dashboard_profile);
        profile.setText("♡");
        TextView dailyAffirmation = findViewById(R.id.daily_affirmation);
        dailyAffirmation.setText(getDailyAffirmation());

        profile.setOnClickListener(view -> showProfile());
        findViewById(R.id.check_in_button).setOnClickListener(view -> showMoodCheckIn());
        wireBottomNavigation(Screen.DASHBOARD);
    }

    private void showWelcome() {
        cancelBreathingTimer();
        currentScreen = Screen.WELCOME;
        showScreen(R.layout.screen_welcome);
        findViewById(R.id.get_started).setOnClickListener(view -> showOnboardingFocus());
    }

    private void showOnboardingFocus() {
        currentScreen = Screen.ONBOARDING_FOCUS;
        showScreen(R.layout.screen_onboarding_focus);
        TextView selection = findViewById(R.id.focus_selection);
        findViewById(R.id.focus_relax).setOnClickListener(view -> selection.setText("Relax selected. A gentle start."));
        findViewById(R.id.focus_calm).setOnClickListener(view -> selection.setText("Calm Down selected. Take it slowly."));
        findViewById(R.id.focus_focus).setOnClickListener(view -> selection.setText("Focus selected. One thing at a time."));
        findViewById(R.id.focus_selfcare).setOnClickListener(view -> selection.setText("Self-Care selected. You deserve the pause."));
        findViewById(R.id.focus_continue).setOnClickListener(view -> showOnboardingMood());
    }

    private void showOnboardingMood() {
        currentScreen = Screen.ONBOARDING_MOOD;
        showScreen(R.layout.screen_onboarding_mood);
        TextView selection = findViewById(R.id.onboarding_mood_selection);
        findViewById(R.id.onboarding_mood_happy).setOnClickListener(view -> selectOnboardingMood(5, "Happy", selection));
        findViewById(R.id.onboarding_mood_stressed).setOnClickListener(view -> selectOnboardingMood(1, "Stressed", selection));
        findViewById(R.id.onboarding_mood_calm).setOnClickListener(view -> selectOnboardingMood(4, "Calm", selection));
        findViewById(R.id.onboarding_mood_anxious).setOnClickListener(view -> selectOnboardingMood(2, "Anxious", selection));
        findViewById(R.id.onboarding_mood_motivated).setOnClickListener(view -> selectOnboardingMood(5, "Motivated", selection));
        findViewById(R.id.onboarding_mood_neutral).setOnClickListener(view -> selectOnboardingMood(3, "Neutral", selection));
        findViewById(R.id.onboarding_mood_continue).setOnClickListener(view -> showOnboardingRoutine());
    }

    private void selectOnboardingMood(int severity, String label, TextView selection) {
        onboardingMoodSeverity = severity;
        selection.setText(label + " selected. Your answer is private and can change anytime.");
    }

    private void showOnboardingRoutine() {
        currentScreen = Screen.ONBOARDING_ROUTINE;
        showScreen(R.layout.screen_onboarding_routine);
        TextView selection = findViewById(R.id.routine_selection);
        findViewById(R.id.routine_breathe).setOnClickListener(view -> selection.setText("Pause & Breathe selected."));
        findViewById(R.id.routine_journal).setOnClickListener(view -> selection.setText("Journal a thought selected."));
        findViewById(R.id.routine_comfort).setOnClickListener(view -> selection.setText("Comfort Words selected."));
        findViewById(R.id.routine_night).setOnClickListener(view -> selection.setText("Before Bed Relax selected."));
        findViewById(R.id.routine_continue).setOnClickListener(view -> finishOnboarding());
    }

    private void finishOnboarding() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("onboarding_complete", true).apply();
        if (onboardingMoodSeverity > 0 && serverSessionActive) {
            int selectedSeverity = onboardingMoodSeverity;
            databaseExecutor.execute(() -> {
                try {
                    api.saveMood(selectedSeverity);
                    WellnessStore.recordCheckInToday(this);
                } catch (CozymindApi.ApiException ignored) {
                    // Onboarding remains usable if a check-in cannot be synced immediately.
                }
            });
        }
        showDashboard(getSavedName());
    }

    private void showToolkit() {
        cancelBreathingTimer();
        currentScreen = Screen.TOOLKIT;
        showScreen(R.layout.screen_toolkit);
        findViewById(R.id.tool_calm_down).setOnClickListener(view -> showBreathing());
        findViewById(R.id.tool_lift_mood).setOnClickListener(view -> showMoodCheckIn());
        findViewById(R.id.tool_comfort_words).setOnClickListener(view -> showComfortWords());
        findViewById(R.id.toolkit_journal).setOnClickListener(view -> showJournal());
        findViewById(R.id.tool_goals).setOnClickListener(view -> showGoals());
        findViewById(R.id.tool_sleep).setOnClickListener(view -> showSleepLog());
        findViewById(R.id.tool_sounds).setOnClickListener(view -> openTool(ToolActivity.TOOL_SOUNDS));
        findViewById(R.id.tool_focus).setOnClickListener(view -> openTool(ToolActivity.TOOL_FOCUS));
        findViewById(R.id.tool_gratitude).setOnClickListener(view -> openTool(ToolActivity.TOOL_GRATITUDE));
        findViewById(R.id.tool_reframe).setOnClickListener(view -> openTool(ToolActivity.TOOL_REFRAME));
        findViewById(R.id.tool_selfcare).setOnClickListener(view -> openTool(ToolActivity.TOOL_SELF_CARE));
        findViewById(R.id.tool_bodyscan).setOnClickListener(view -> openTool(ToolActivity.TOOL_BODY_SCAN));
        wireBottomNavigation(Screen.TOOLKIT);
    }

    private void showProfile() {
        cancelBreathingTimer();
        currentScreen = Screen.PROFILE;
        showScreen(R.layout.screen_profile);
        ((TextView) findViewById(R.id.profile_initial)).setText("♡");
        ((TextView) findViewById(R.id.profile_name)).setText(getSavedName());
        ((TextView) findViewById(R.id.profile_subtitle)).setText("Taking care of yourself, one day at a time");
        ((TextView) findViewById(R.id.profile_streak)).setText(WellnessStore.streakLabel(this));
        findViewById(R.id.profile_mood_history).setOnClickListener(view -> showMoodCheckIn());
        findViewById(R.id.profile_journal).setOnClickListener(view -> showJournal());
        findViewById(R.id.profile_sign_out).setOnClickListener(view -> signOut());

        Switch reminderSwitch = findViewById(R.id.profile_reminder_switch);
        TextView reminderTime = findViewById(R.id.profile_reminder_time);
        reminderSwitch.setChecked(WellnessStore.reminderOn(this));
        reminderTime.setText("🕒   Reminder time: " + WellnessStore.reminderTimeLabel(this));
        reminderSwitch.setOnCheckedChangeListener((button, on) -> {
            if (on) askForNotificationPermission();
            WellnessStore.setReminder(this, on, WellnessStore.reminderHour(this), WellnessStore.reminderMinute(this));
            Toast.makeText(this, on ? "Daily reminder set for " + WellnessStore.reminderTimeLabel(this) + " 💚"
                    : "Daily reminder turned off.", Toast.LENGTH_SHORT).show();
        });
        reminderTime.setOnClickListener(view -> new TimePickerDialog(this, (picker, hour, minute) -> {
            WellnessStore.setReminder(this, true, hour, minute);
            reminderSwitch.setChecked(true);
            reminderTime.setText("🕒   Reminder time: " + WellnessStore.reminderTimeLabel(this));
            askForNotificationPermission();
        }, WellnessStore.reminderHour(this), WellnessStore.reminderMinute(this), false).show());
        wireBottomNavigation(Screen.PROFILE);
    }

    private void showComfortWords() {
        currentScreen = Screen.COMFORT_WORDS;
        showScreen(R.layout.screen_comfort_words);
        TextView quote = findViewById(R.id.comfort_quote);
        quote.setText(getComfortWord(0));
        findViewById(R.id.back_from_comfort).setOnClickListener(view -> showToolkit());
        findViewById(R.id.comfort_next).setOnClickListener(view ->
                quote.setText(getComfortWord((int) (System.currentTimeMillis() / 1000L))));
        wireBottomNavigation(Screen.TOOLKIT);
    }

    private void showMoodCheckIn() {
        currentScreen = Screen.MOOD;
        showScreen(R.layout.screen_mood_checkin);

        RadioGroup moodOptions = findViewById(R.id.mood_options);
        LinearLayout history = findViewById(R.id.mood_history_container);
        TextView statistics = findViewById(R.id.mood_statistics_summary);

        findViewById(R.id.back_from_mood).setOnClickListener(view -> showDashboard(getSavedName()));
        findViewById(R.id.save_mood).setOnClickListener(view -> saveMoodCheckIn(moodOptions, history, statistics));
        loadMoodHistory(history, statistics);
    }

    private void showJournal() {
        currentScreen = Screen.JOURNAL;
        showScreen(R.layout.screen_journal);

        EditText title = findViewById(R.id.journal_title);
        EditText body = findViewById(R.id.journal_body);
        LinearLayout entries = findViewById(R.id.journal_entries_container);
        LinearLayout editor = findViewById(R.id.journal_editor);

        TextView prompt = findViewById(R.id.journal_prompt);
        EditText search = findViewById(R.id.journal_search);
        journalPromptIndex = WellnessStore.todaysPromptIndex();
        prompt.setText(WellnessStore.journalPrompt(journalPromptIndex));
        findViewById(R.id.journal_prompt_next).setOnClickListener(view -> {
            journalPromptIndex++;
            prompt.setText(WellnessStore.journalPrompt(journalPromptIndex));
        });

        findViewById(R.id.new_journal_entry).setOnClickListener(view -> {
            editor.setVisibility(View.VISIBLE);
            title.requestFocus();
        });
        findViewById(R.id.write_response).setOnClickListener(view -> {
            editor.setVisibility(View.VISIBLE);
            if (title.getText().toString().trim().isEmpty()) title.setText(prompt.getText());
            body.requestFocus();
        });
        findViewById(R.id.back_from_journal).setOnClickListener(view -> search.requestFocus());
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                renderJournalEntries(entries, text.toString());
            }
        });

        selectedJournalTag = "";
        int[] tagIds = {R.id.journal_tag_1, R.id.journal_tag_2, R.id.journal_tag_3,
                R.id.journal_tag_4, R.id.journal_tag_5};
        for (int tagId : tagIds) {
            TextView tag = findViewById(tagId);
            tag.setOnClickListener(view -> {
                String emoji = tag.getText().toString();
                selectedJournalTag = emoji.equals(selectedJournalTag) ? "" : emoji;
                for (int otherId : tagIds) {
                    TextView other = findViewById(otherId);
                    other.setBackgroundResource(other.getText().toString().equals(selectedJournalTag)
                            ? R.drawable.bg_soft_teal : R.drawable.bg_outline_button);
                }
            });
        }
        findViewById(R.id.save_journal).setOnClickListener(view -> saveJournalEntry(title, body, entries));
        wireBottomNavigation(Screen.JOURNAL);
        loadJournalEntries(entries);
    }

    private void showBreathing() {
        currentScreen = Screen.BREATHE;
        showScreen(R.layout.screen_breathe);

        TextView instruction = findViewById(R.id.breath_instruction);
        TextView countdown = findViewById(R.id.breath_countdown);
        TextView round = findViewById(R.id.breath_round);
        Button startButton = findViewById(R.id.start_breathing);

        findViewById(R.id.back_from_breathe).setOnClickListener(view -> handleBackNavigation());
        startButton.setOnClickListener(view -> startBreathingSession(instruction, countdown, round, startButton));
    }

    // ---------- Goals (uses the goals/*.php endpoints) ----------

    private void showGoals() {
        cancelBreathingTimer();
        currentScreen = Screen.GOALS;
        showScreen(R.layout.screen_goals);

        EditText title = findViewById(R.id.goal_title);
        EditText description = findViewById(R.id.goal_description);
        RadioGroup frequency = findViewById(R.id.goal_frequency);
        LinearLayout goals = findViewById(R.id.goals_container);

        findViewById(R.id.back_from_goals).setOnClickListener(view -> showToolkit());
        findViewById(R.id.save_goal).setOnClickListener(view -> {
            String goalTitle = title.getText().toString().trim();
            if (goalTitle.isEmpty()) {
                title.setError("Give your goal a short title.");
                title.requestFocus();
                return;
            }
            String goalDescription = description.getText().toString().trim();
            String goalFrequency = frequency.getCheckedRadioButtonId() == R.id.goal_weekly ? "weekly" : "daily";
            databaseExecutor.execute(() -> {
                try {
                    api.createGoal(goalTitle, goalDescription, goalFrequency);
                    runIfAlive(() -> {
                        if (currentScreen != Screen.GOALS) return;
                        title.setText("");
                        description.setText("");
                        Toast.makeText(this, "Goal added. One gentle step at a time.", Toast.LENGTH_SHORT).show();
                        loadGoals(goals);
                    });
                } catch (CozymindApi.ApiException exception) {
                    runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
        wireBottomNavigation(Screen.TOOLKIT);
        loadGoals(goals);
    }

    private void loadGoals(LinearLayout container) {
        databaseExecutor.execute(() -> {
            try {
                List<CozymindApi.Goal> goals = api.getGoals();
                runIfAlive(() -> {
                    if (currentScreen != Screen.GOALS) return;
                    container.removeAllViews();
                    showGoalProgress(goals);
                    if (goals.isEmpty()) {
                        addEmptyMessage(container, "No goals yet. Add one small thing you'd like to do.");
                        return;
                    }
                    for (CozymindApi.Goal goal : goals) {
                        boolean done = "done".equals(goal.status);
                        String text = (done ? "✓ " : "○ ") + goal.title
                                + (goal.description.isEmpty() ? "" : "\n" + goal.description)
                                + "\n" + capitalize(goal.frequency) + " · " + capitalize(goal.status)
                                + " · since " + goal.startDate;
                        TextView card = addHistoryCard(container, text);
                        if (done) card.setAlpha(0.6f);
                        card.setOnClickListener(view -> showGoalActions(goal, container));
                    }
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> {
                    if (currentScreen != Screen.GOALS) return;
                    container.removeAllViews();
                    addEmptyMessage(container, exception.getMessage());
                });
            }
        });
    }

    private void showGoalActions(CozymindApi.Goal goal, LinearLayout container) {
        boolean done = "done".equals(goal.status);
        String[] options = {done ? "Mark as active again" : "Mark as done", "Delete goal"};
        new AlertDialog.Builder(this)
                .setTitle(goal.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        String newStatus = done ? "active" : "done";
                        databaseExecutor.execute(() -> {
                            try {
                                api.setGoalStatus(goal.id, newStatus);
                                runIfAlive(() -> {
                                    if (!done) celebrateGoal(goal.title);
                                    loadGoals(container);
                                });
                            } catch (CozymindApi.ApiException exception) {
                                runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    } else {
                        confirmDelete("Delete this goal?", goal.title,
                                () -> api.deleteGoal(goal.id), () -> loadGoals(container));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Sleep log (uses the sleep-logs/*.php endpoints) ----------

    private void showSleepLog() {
        cancelBreathingTimer();
        currentScreen = Screen.SLEEP;
        showScreen(R.layout.screen_sleep);

        EditText date = findViewById(R.id.sleep_date);
        EditText bedTime = findViewById(R.id.sleep_bed_time);
        EditText wakeTime = findViewById(R.id.sleep_wake_time);
        RadioGroup quality = findViewById(R.id.sleep_quality);
        EditText notes = findViewById(R.id.sleep_notes);
        LinearLayout logs = findViewById(R.id.sleep_container);

        // Default to last night: yesterday's date.
        date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)));

        findViewById(R.id.back_from_sleep).setOnClickListener(view -> showToolkit());
        findViewById(R.id.save_sleep).setOnClickListener(view -> {
            String sleepDate = date.getText().toString().trim();
            String bed = bedTime.getText().toString().trim();
            String wake = wakeTime.getText().toString().trim();
            if (!sleepDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                date.setError("Use the format 2026-10-25.");
                date.requestFocus();
                return;
            }
            if (!bed.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
                bedTime.setError("Use 24-hour time, e.g. 22:30.");
                bedTime.requestFocus();
                return;
            }
            if (!wake.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
                wakeTime.setError("Use 24-hour time, e.g. 06:30.");
                wakeTime.requestFocus();
                return;
            }
            int sleepQuality = sleepQualityForId(quality.getCheckedRadioButtonId());
            String sleepNotes = notes.getText().toString().trim();
            String bedPadded = bed.length() == 4 ? "0" + bed : bed;
            String wakePadded = wake.length() == 4 ? "0" + wake : wake;
            databaseExecutor.execute(() -> {
                try {
                    api.createSleepLog(sleepDate, bedPadded, wakePadded, sleepQuality, sleepNotes);
                    runIfAlive(() -> {
                        if (currentScreen != Screen.SLEEP) return;
                        bedTime.setText("");
                        wakeTime.setText("");
                        notes.setText("");
                        Toast.makeText(this, "Sleep log saved. Rest well tonight.", Toast.LENGTH_SHORT).show();
                        loadSleepLogs(logs);
                    });
                } catch (CozymindApi.ApiException exception) {
                    runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
        wireBottomNavigation(Screen.TOOLKIT);
        loadSleepLogs(logs);
    }

    private void loadSleepLogs(LinearLayout container) {
        databaseExecutor.execute(() -> {
            try {
                List<CozymindApi.SleepLog> logs = api.getSleepLogs();
                runIfAlive(() -> {
                    if (currentScreen != Screen.SLEEP) return;
                    container.removeAllViews();
                    showSleepSummary(logs);
                    if (logs.isEmpty()) {
                        addEmptyMessage(container, "No nights logged yet.");
                        return;
                    }
                    for (CozymindApi.SleepLog log : logs) {
                        String text = log.sleepDate + " · " + hoursSlept(log.bedTime, log.wakeTime)
                                + "\n" + shortTime(log.bedTime) + " → " + shortTime(log.wakeTime)
                                + " · Quality " + log.quality + " / 5"
                                + (log.notes.isEmpty() ? "" : "\n" + log.notes);
                        TextView card = addHistoryCard(container, text);
                        card.setOnClickListener(view -> confirmDelete("Delete this sleep log?", log.sleepDate,
                                () -> api.deleteSleepLog(log.id), () -> loadSleepLogs(container)));
                    }
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> {
                    if (currentScreen != Screen.SLEEP) return;
                    container.removeAllViews();
                    addEmptyMessage(container, exception.getMessage());
                });
            }
        });
    }

    private int sleepQualityForId(int selectedId) {
        if (selectedId == R.id.sleep_q1) return 1;
        if (selectedId == R.id.sleep_q2) return 2;
        if (selectedId == R.id.sleep_q4) return 4;
        if (selectedId == R.id.sleep_q5) return 5;
        return 3;
    }

    /** "22:30:00" → "22:30" */
    private String shortTime(String time) {
        return time != null && time.length() >= 5 ? time.substring(0, 5) : String.valueOf(time);
    }

    /** Hours between bedtime and wake time, crossing midnight if needed, e.g. "8h 0m". */
    private String hoursSlept(String bed, String wake) {
        try {
            int bedMinutes = Integer.parseInt(bed.substring(0, 2)) * 60 + Integer.parseInt(bed.substring(3, 5));
            int wakeMinutes = Integer.parseInt(wake.substring(0, 2)) * 60 + Integer.parseInt(wake.substring(3, 5));
            int total = wakeMinutes - bedMinutes;
            if (total <= 0) total += 24 * 60;
            return (total / 60) + "h " + (total % 60) + "m";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    // ---------- shared helpers for the new screens ----------

    private interface ApiCall {
        void run() throws CozymindApi.ApiException;
    }

    /** Asks before deleting, then runs the API call in the background and refreshes the list. */
    private void confirmDelete(String question, String detail, ApiCall deleteCall, Runnable reload) {
        new AlertDialog.Builder(this)
                .setTitle(question)
                .setMessage(detail)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> databaseExecutor.execute(() -> {
                    try {
                        deleteCall.run();
                        runIfAlive(() -> {
                            Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show();
                            reload.run();
                        });
                    } catch (CozymindApi.ApiException exception) {
                        runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }))
                .show();
    }

    private String capitalize(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    private void attemptLogin(EditText emailField, EditText passwordField) {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        // The server accepts either an email or a username.
        if (TextUtils.isEmpty(email) || (email.contains("@") && !isEmailValid(email))) {
            emailField.setError("Enter your email or username.");
            emailField.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordField.setError("Enter your password.");
            passwordField.requestFocus();
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                CozymindApi.UserProfile user = api.login(email, password);
                runIfAlive(() -> {
                    signInServer(user);
                    showDashboard(user.name);
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> showServerError(passwordField, exception));
            }
        });
    }

    private void register(EditText nameField, EditText usernameField, EditText emailField,
                          EditText phoneNumberField, EditText passwordField,
                          EditText confirmPasswordField, CheckBox terms) {
        String name = nameField.getText().toString().trim();
        String username = usernameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String phoneNumber = phoneNumberField.getText().toString().trim();
        String password = passwordField.getText().toString();
        String confirmPassword = confirmPasswordField.getText().toString();

        if (TextUtils.isEmpty(name)) {
            nameField.setError("Enter your name.");
            nameField.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(username)) {
            usernameField.setError("Enter a username.");
            usernameField.requestFocus();
            return;
        }
        if (!isEmailValid(email)) {
            emailField.setError("Enter a valid email address.");
            emailField.requestFocus();
            return;
        }
        if (phoneNumber.replaceAll("[^0-9]", "").length() < 7) {
            phoneNumberField.setError("Enter a valid phone number.");
            phoneNumberField.requestFocus();
            return;
        }
        if (password.length() < 6) {
            passwordField.setError("Use at least 6 characters.");
            passwordField.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordField.setError("Passwords must match.");
            confirmPasswordField.requestFocus();
            return;
        }
        if (!terms.isChecked()) {
            Toast.makeText(this, "Please agree to the terms to continue.", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                CozymindApi.UserProfile user = api.register(name, username, email, phoneNumber, password);
                runIfAlive(() -> {
                    getPreferences(MODE_PRIVATE).edit().putString("last_email", email).apply();
                    signInServer(user);
                    Toast.makeText(this, "Account created and saved securely.", Toast.LENGTH_SHORT).show();
                    showWelcome();
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> showServerError(usernameField, exception));
            }
        });
    }

    private void saveMoodCheckIn(RadioGroup moodOptions, LinearLayout history, TextView statistics) {
        int severity = severityForId(moodOptions.getCheckedRadioButtonId());
        if (severity == 0) {
            Toast.makeText(this, "Choose a feeling first.", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                api.saveMood(severity);
                runIfAlive(() -> {
                    if (currentScreen != Screen.MOOD) return;
                    moodOptions.clearCheck();
                    WellnessStore.recordCheckInToday(this);
                    if (severity <= 2) showSupportOptions();
                    else Toast.makeText(this, moodSupportMessage(moodLabel(severity)), Toast.LENGTH_LONG).show();
                    loadMoodHistory(history, statistics);
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void saveJournalEntry(EditText titleField, EditText bodyField, LinearLayout entries) {
        String body = bodyField.getText().toString().trim();
        if (body.isEmpty()) {
            bodyField.setError("Write a few words before saving.");
            bodyField.requestFocus();
            return;
        }

        String title = titleField.getText().toString().trim();
        String baseTitle = title.isEmpty() ? "A quiet moment" : title;
        final String entryTitle = selectedJournalTag.isEmpty() ? baseTitle : selectedJournalTag + " " + baseTitle;
        databaseExecutor.execute(() -> {
            try {
                api.saveJournal(entryTitle, body);
                runIfAlive(() -> {
                    if (currentScreen != Screen.JOURNAL) return;
                    titleField.setText("");
                    bodyField.setText("");
                    Toast.makeText(this, "Journal entry saved securely.", Toast.LENGTH_SHORT).show();
                    loadJournalEntries(entries);
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void loadMoodHistory(LinearLayout history, TextView statistics) {
        databaseExecutor.execute(() -> {
            try {
                CozymindApi.MoodResponse response = api.getMoods();
                runIfAlive(() -> {
                    if (currentScreen != Screen.MOOD) return;
                    statistics.setText(response.statistics.isEmpty()
                            ? "Add at least 3 check-ins within a 6-hour window to see your average."
                            : String.format(Locale.getDefault(),
                            "Latest 6-hour average: %.1f / 5 from %d check-ins.",
                            response.statistics.get(0).averageSeverity,
                            response.statistics.get(0).entryCount));
                    for (CozymindApi.Mood checkIn : response.moods) {
                        WellnessStore.mergeServerCheckIn(this, checkIn.datetimeMood);
                    }
                    renderMoodWeek(response.moods);
                    history.removeAllViews();
                    if (response.moods.isEmpty()) {
                        addEmptyMessage(history, "Your saved check-ins will appear here.");
                        return;
                    }
                    for (CozymindApi.Mood checkIn : response.moods) {
                        addHistoryCard(history, moodEmoji(checkIn.severity) + "  " + moodLabel(checkIn.severity)
                                + " · " + checkIn.severity + " / 5\n" + friendlyTime(checkIn.datetimeMood));
                    }
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> showLoadError(history, statistics, exception.getMessage()));
            }
        });
    }

    private void loadJournalEntries(LinearLayout entries) {
        databaseExecutor.execute(() -> {
            try {
                List<CozymindApi.Journal> journalEntries = api.getJournals();
                runIfAlive(() -> {
                    if (currentScreen != Screen.JOURNAL) return;
                    loadedJournals = journalEntries;
                    EditText search = findViewById(R.id.journal_search);
                    renderJournalEntries(entries, search == null ? "" : search.getText().toString());
                });
            } catch (CozymindApi.ApiException exception) {
                runIfAlive(() -> {
                    if (currentScreen != Screen.JOURNAL) return;
                    entries.removeAllViews();
                    addEmptyMessage(entries, exception.getMessage());
                });
            }
        });
    }

    private void startBreathingSession(TextView instruction, TextView countdown, TextView round,
                                       Button startButton) {
        cancelBreathingTimer();
        breathingRound = 1;
        runBreathingPhase(0, instruction, countdown, round, startButton);
    }

    private void runBreathingPhase(int phase, TextView instruction, TextView countdown,
                                   TextView round, Button startButton) {
        String[] instructions = {"Breathe in", "Hold gently", "Breathe out"};
        int[] durations = {4, 4, 6};
        int durationSeconds = durations[phase];

        instruction.setText(instructions[phase]);
        countdown.setText(String.valueOf(durationSeconds));
        round.setText("Round " + breathingRound + " of 4");
        startButton.setText("Restart session");

        breathingTimer = new CountDownTimer(durationSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdown.setText(String.valueOf((millisUntilFinished + 999) / 1000));
            }

            @Override
            public void onFinish() {
                if (phase < instructions.length - 1) {
                    runBreathingPhase(phase + 1, instruction, countdown, round, startButton);
                } else if (breathingRound < 4) {
                    breathingRound++;
                    runBreathingPhase(0, instruction, countdown, round, startButton);
                } else {
                    instruction.setText("You did it");
                    countdown.setText("✓");
                    round.setText("Four mindful breaths complete");
                    startButton.setText("Start again");
                    breathingTimer = null;
                }
            }
        }.start();
    }

    private int severityForId(int selectedId) {
        if (selectedId == R.id.mood_great) return 5;
        if (selectedId == R.id.mood_good) return 4;
        if (selectedId == R.id.mood_neutral) return 3;
        if (selectedId == R.id.mood_low) return 2;
        if (selectedId == R.id.mood_overwhelmed) return 1;
        return 0;
    }

    private String moodLabel(int severity) {
        if (severity == 5) return "Great";
        if (severity == 4) return "Good";
        if (severity == 3) return "In between";
        if (severity == 2) return "Low";
        return "Overwhelmed";
    }

    private TextView addHistoryCard(LinearLayout container, String text) {
        TextView card = new TextView(this);
        card.setBackgroundResource(R.drawable.bg_dashboard_card);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setText(text);
        card.setTextColor(getColor(R.color.ink));
        card.setTextSize(15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        container.addView(card, params);
        return card;
    }

    private void addEmptyMessage(LinearLayout container, String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(getColor(R.color.muted_ink));
        message.setTextSize(14);
        message.setPadding(0, dp(8), 0, dp(8));
        container.addView(message);
    }

    private String getSavedName() {
        return currentUserName.isEmpty() ? "there" : currentUserName;
    }

    private void restoreSession() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        currentUserId = preferences.getLong("active_user_id", 0L);
        currentUserName = preferences.getString("active_user_name", "");
        serverSessionActive = false;
    }

    private void signIn(User user) {
        currentUserId = user.id;
        currentUserName = user.name;
        serverSessionActive = false;
        getPreferences(MODE_PRIVATE).edit()
                .putLong("active_user_id", user.id)
                .putString("active_user_name", user.name)
                .putString("last_email", user.email)
                .apply();
    }

    private void signInServer(CozymindApi.UserProfile user) {
        currentUserId = user.id;
        currentUserName = user.name;
        serverSessionActive = true;
        getPreferences(MODE_PRIVATE).edit()
                .putLong("active_user_id", user.id)
                .putString("active_user_name", user.name)
                .putString("last_email", user.email)
                .apply();
    }

    private void signOut() {
        boolean hadServerSession = serverSessionActive;
        currentUserId = 0L;
        currentUserName = "";
        serverSessionActive = false;
        getPreferences(MODE_PRIVATE).edit()
                .remove("active_user_id")
                .remove("active_user_name")
                .apply();
        if (hadServerSession) {
            databaseExecutor.execute(() -> {
                try {
                    api.logout();
                } catch (CozymindApi.ApiException ignored) {
                    // The local cookie is still cleared, even when the server cannot be reached.
                } finally {
                    api.clearSession();
                }
            });
        } else {
            api.clearSession();
        }
        showLogin();
    }

    private void migrateLegacyAccount() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        if (preferences.getBoolean("legacy_account_migrated", false)) return;

        String legacyEmail = preferences.getString("email", "");
        String legacyPassword = preferences.getString("password", "");
        String legacyName = preferences.getString("name", "");
        if (TextUtils.isEmpty(legacyEmail) || TextUtils.isEmpty(legacyPassword)) {
            preferences.edit().putBoolean("legacy_account_migrated", true).apply();
            return;
        }

        databaseExecutor.execute(() -> {
            if (database.userDao().findByEmail(legacyEmail) == null) {
                int atSign = legacyEmail.indexOf('@');
                String baseUsername = legacyEmail.substring(0, atSign > 0 ? atSign : legacyEmail.length())
                        .replaceAll("[^A-Za-z0-9_]", "");
                String username = (baseUsername.isEmpty() ? "legacy_user" : baseUsername)
                        + "_" + Math.abs(legacyEmail.hashCode());
                long now = System.currentTimeMillis();
                try {
                    database.userDao().insert(new User(
                            TextUtils.isEmpty(legacyName) ? "Cozymind user" : legacyName,
                            username, legacyPassword, legacyEmail, "", false, now, now));
                } catch (Exception ignored) {
                    // A unique user record already exists; retain the user's local account data.
                }
            }
            preferences.edit().putBoolean("legacy_account_migrated", true).apply();
        });
    }

    private String getDailyAffirmation() {
        return "\"" + WellnessStore.affirmationForToday() + "\"";
    }

    // ---------- New wellness helpers ----------

    private void openTool(String tool) {
        startActivity(ToolActivity.intent(this, tool));
        overridePendingTransition(R.anim.calm_window_enter, R.anim.calm_window_exit);
    }

    private String timeOfDayGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return "Rest well tonight";
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    private void showDashboardWater(TextView status, int glasses) {
        status.setText(glasses >= WellnessStore.WATER_GOAL ? "Goal reached 🎉"
                : glasses + " of " + WellnessStore.WATER_GOAL + " · tap to log");
    }

    /** A gentle follow-up after a low or overwhelmed check-in. */
    private void showSupportOptions() {
        String[] options = {
                "🌬  Try a 1-minute breathing reset",
                "📝  Write it out in my journal",
                "📞  Talk to someone (NCMH 1553, 24/7)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Thank you for checking in 💗")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showBreathing();
                    else if (which == 1) showJournal();
                    else dial("1553");
                })
                .setNegativeButton("Not right now", null)
                .show();
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Please call " + number + " from your phone.", Toast.LENGTH_LONG).show();
        }
    }

    private String moodEmoji(int severity) {
        if (severity >= 5) return "😄";
        if (severity == 4) return "🙂";
        if (severity == 3) return "😐";
        if (severity == 2) return "😔";
        return "😣";
    }

    private int moodColor(int severity) {
        if (severity >= 5) return 0xFF7FB7A6;
        if (severity == 4) return 0xFFFFC46B;
        if (severity == 3) return 0xFFCFCCC4;
        if (severity == 2) return 0xFFA79DE8;
        return 0xFFE8787A;
    }

    /** "2026-11-15 03:10:00" (UTC) → "Sun, Nov 15 · 11:10 AM" in the phone's time zone. */
    private String friendlyTime(String utcDateTime) {
        Date date = WellnessStore.parseUtc(utcDateTime);
        if (date == null) return String.valueOf(utcDateTime);
        return new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(date);
    }

    private String localDayKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    /** Draws a 7-day bar chart of average mood, plus the streak and a short summary. */
    private void renderMoodWeek(List<CozymindApi.Mood> moods) {
        LinearLayout chart = findViewById(R.id.mood_week_chart);
        TextView summary = findViewById(R.id.mood_week_summary);
        TextView streak = findViewById(R.id.mood_streak);
        if (chart == null || summary == null) return;
        if (streak != null) streak.setText(WellnessStore.streakLabel(this));

        String[] keys = new String[7];
        String[] labels = new String[7];
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat dayName = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            keys[i] = localDayKey(day.getTime());
            labels[i] = i == 6 ? "Today" : dayName.format(day.getTime());
            day.add(Calendar.DAY_OF_YEAR, 1);
        }

        int[] totals = new int[7];
        int[] counts = new int[7];
        for (CozymindApi.Mood mood : moods) {
            Date date = WellnessStore.parseUtc(mood.datetimeMood);
            if (date == null) continue;
            String key = localDayKey(date);
            for (int i = 0; i < 7; i++) {
                if (keys[i].equals(key)) {
                    totals[i] += mood.severity;
                    counts[i]++;
                }
            }
        }

        chart.removeAllViews();
        int weekTotal = 0;
        int weekCount = 0;
        int daysLogged = 0;
        for (int i = 0; i < 7; i++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            int average = counts[i] == 0 ? 0 : Math.round((float) totals[i] / counts[i]);
            TextView emoji = new TextView(this);
            emoji.setText(counts[i] == 0 ? "" : moodEmoji(average));
            emoji.setTextSize(14);
            emoji.setGravity(Gravity.CENTER);
            column.addView(emoji, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            View bar = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(8));
            shape.setColor(counts[i] == 0 ? 0x33A4A6B1 : moodColor(average));
            bar.setBackground(shape);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    dp(20), counts[i] == 0 ? dp(6) : dp(10 + average * 14));
            barParams.topMargin = dp(2);
            column.addView(bar, barParams);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(10);
            label.setTextColor(getColor(i == 6 ? R.color.register_teal : R.color.muted_ink));
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(4);
            column.addView(label, labelParams);

            chart.addView(column, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            weekTotal += totals[i];
            weekCount += counts[i];
            if (counts[i] > 0) daysLogged++;
        }

        if (weekCount == 0) {
            summary.setText("No check-ins this week yet. Your first one starts the chart. 🌱");
        } else {
            float weekAverage = (float) weekTotal / weekCount;
            summary.setText(String.format(Locale.getDefault(),
                    "Average %.1f / 5 · mostly %s %s\nYou checked in on %d of the last 7 days.",
                    weekAverage, moodLabel(Math.round(weekAverage)), moodEmoji(Math.round(weekAverage)),
                    daysLogged));
        }
    }

    /** Shows journal cards, filtered by the search box. */
    private void renderJournalEntries(LinearLayout entries, String query) {
        if (currentScreen != Screen.JOURNAL) return;
        entries.removeAllViews();
        if (loadedJournals.isEmpty()) {
            addEmptyMessage(entries, "Your private journal entries will appear here.");
            return;
        }
        String needle = query.trim().toLowerCase(Locale.getDefault());
        int shown = 0;
        for (CozymindApi.Journal entry : loadedJournals) {
            String haystack = (entry.title + " " + entry.body).toLowerCase(Locale.getDefault());
            if (!needle.isEmpty() && !haystack.contains(needle)) continue;
            TextView card = addHistoryCard(entries, entry.title + "\n" + entry.body + "\n"
                    + friendlyTime(entry.createdAt));
            card.setOnClickListener(view -> confirmDelete("Delete this journal entry?",
                    entry.title, () -> api.deleteJournal(entry.id), () -> loadJournalEntries(entries)));
            shown++;
        }
        if (shown == 0) addEmptyMessage(entries, "No entries match \"" + query.trim() + "\".");
    }

    private void showGoalProgress(List<CozymindApi.Goal> goals) {
        TextView summary = findViewById(R.id.goals_summary);
        ProgressBar progress = findViewById(R.id.goals_progress);
        if (summary == null || progress == null) return;
        int done = 0;
        for (CozymindApi.Goal goal : goals) if ("done".equals(goal.status)) done++;
        int total = goals.size();
        progress.setProgress(total == 0 ? 0 : Math.round(done * 100f / total));
        if (total == 0) summary.setText("🎯  Add your first goal to see your progress");
        else if (done == total) summary.setText("🌟  All " + total + " goals done. Amazing work!");
        else summary.setText("🎯  " + done + " of " + total + " goals done · keep going");
    }

    private void celebrateGoal(String title) {
        new AlertDialog.Builder(this)
                .setTitle("🎉  Goal complete!")
                .setMessage("\"" + title + "\" is done.\n\nEvery small step counts. Take a moment to feel proud of yourself. 💚")
                .setPositiveButton("Thank you", null)
                .show();
    }

    private int minutesSlept(String bed, String wake) {
        try {
            int bedMinutes = Integer.parseInt(bed.substring(0, 2)) * 60 + Integer.parseInt(bed.substring(3, 5));
            int wakeMinutes = Integer.parseInt(wake.substring(0, 2)) * 60 + Integer.parseInt(wake.substring(3, 5));
            int total = wakeMinutes - bedMinutes;
            if (total <= 0) total += 24 * 60;
            return total;
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    /** Weekly sleep summary with a tip based on the most recent night. */
    private void showSleepSummary(List<CozymindApi.SleepLog> logs) {
        TextView summary = findViewById(R.id.sleep_summary);
        if (summary == null) return;
        if (logs.isEmpty()) {
            summary.setText("🌙  Log a few nights to see your weekly sleep summary.");
            return;
        }
        List<CozymindApi.SleepLog> sorted = new ArrayList<>(logs);
        sorted.sort((first, second) -> second.sleepDate.compareTo(first.sleepDate));
        List<CozymindApi.SleepLog> recent = sorted.subList(0, Math.min(7, sorted.size()));

        int minutesTotal = 0;
        int minutesCount = 0;
        int qualityTotal = 0;
        for (CozymindApi.SleepLog log : recent) {
            int minutes = minutesSlept(log.bedTime, log.wakeTime);
            if (minutes > 0) {
                minutesTotal += minutes;
                minutesCount++;
            }
            qualityTotal += log.quality;
        }
        int averageMinutes = minutesCount == 0 ? 0 : minutesTotal / minutesCount;
        float averageQuality = (float) qualityTotal / recent.size();
        CozymindApi.SleepLog latest = recent.get(0);

        String tip;
        if (latest.quality <= 2) tip = "Last night felt restless. Try dimming screens 30 minutes before bed.";
        else if (averageMinutes > 0 && averageMinutes < 6 * 60) tip = "You're running low on sleep. Try heading to bed 30 minutes earlier tonight.";
        else if (averageMinutes > 10 * 60) tip = "You're sleeping long. If you still wake up tired, a short morning walk can help.";
        else tip = "Nice rhythm! A steady bedtime helps your mood too.";

        summary.setText(String.format(Locale.getDefault(),
                "🌙  Last %d night%s\nAverage sleep: %dh %dm · Quality %.1f / 5\n\n💡 %s",
                recent.size(), recent.size() == 1 ? "" : "s",
                averageMinutes / 60, averageMinutes % 60, averageQuality, tip));
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Notifications are off, so the reminder can't appear. You can allow them in Settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String getComfortWord(int seed) {
        String[] words = {
                "\"You don't have to carry it all today. Just this moment is enough.\"",
                "Rest is not something you have to earn.",
                "A soft breath can be a fresh beginning.",
                "You are allowed to move at your own gentle pace.",
                "There is room for both your feelings and your hope."
        };
        return words[Math.floorMod(seed, words.length)];
    }

    private String initialFor(String name) {
        if (TextUtils.isEmpty(name)) return "C";
        return name.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void wireBottomNavigation(Screen active) {
        TextView home = findViewById(R.id.nav_home);
        TextView toolkit = findViewById(R.id.nav_toolkit);
        TextView journal = findViewById(R.id.nav_journal);
        TextView profile = findViewById(R.id.nav_profile);
        home.setOnClickListener(view -> showDashboard(getSavedName()));
        toolkit.setOnClickListener(view -> showToolkit());
        journal.setOnClickListener(view -> showJournal());
        profile.setOnClickListener(view -> showProfile());
        int activeColor = getColor(R.color.register_teal);
        int inactiveColor = getColor(R.color.nav_inactive);
        home.setTextColor(active == Screen.DASHBOARD ? activeColor : inactiveColor);
        toolkit.setTextColor(active == Screen.TOOLKIT ? activeColor : inactiveColor);
        journal.setTextColor(active == Screen.JOURNAL ? activeColor : inactiveColor);
        profile.setTextColor(active == Screen.PROFILE ? activeColor : inactiveColor);
    }

    private String moodSupportMessage(String mood) {
        if ("Great".equals(mood)) return "Lovely. Save that spark for later.";
        if ("Good".equals(mood)) return "Steady is a good place to be.";
        if ("In between".equals(mood)) return "Thanks for noticing where you are today.";
        if ("Low".equals(mood)) return "Your check-in is saved. Be extra gentle with yourself.";
        return "Your check-in is saved. Try a slow breathing reset when you are ready.";
    }

    private String formatDate(long time) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(time));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isEmailValid(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showServerError(EditText field, CozymindApi.ApiException exception) {
        String message = exception.statusCode == 401
                ? "Email/username or password does not match."
                : exception.getMessage();
        field.setError(message);
        field.requestFocus();
    }

    private void showLoadError(LinearLayout history, TextView statistics, String message) {
        if (currentScreen != Screen.MOOD) return;
        statistics.setText("Unable to load your online mood history.");
        history.removeAllViews();
        addEmptyMessage(history, message);
    }

    /** Posts to the UI thread, but skips the update if the activity has already closed. */
    private void runIfAlive(Runnable action) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            action.run();
        });
    }

    private void showScreen(int layoutId) {
        View screen = LayoutInflater.from(this).inflate(layoutId, screenContainer, false);
        screenContainer.removeAllViews();
        screenContainer.addView(screen);
    }

    private void cancelBreathingTimer() {
        if (breathingTimer != null) {
            breathingTimer.cancel();
            breathingTimer = null;
        }
    }

    @SuppressWarnings("deprecation") // Legacy back-navigation fallback for Android 12L and earlier.
    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (currentScreen == Screen.LOGIN) {
            finish();
        } else if (currentScreen == Screen.SIGN_UP || currentScreen == Screen.DASHBOARD) {
            showLogin();
        } else if (currentScreen == Screen.WELCOME) {
            showDashboard(getSavedName());
        } else if (currentScreen == Screen.ONBOARDING_FOCUS) {
            showWelcome();
        } else if (currentScreen == Screen.ONBOARDING_MOOD) {
            showOnboardingFocus();
        } else if (currentScreen == Screen.ONBOARDING_ROUTINE) {
            showOnboardingMood();
        } else if (currentScreen == Screen.COMFORT_WORDS || currentScreen == Screen.GOALS
                || currentScreen == Screen.SLEEP) {
            showToolkit();
        } else {
            showDashboard(getSavedName());
        }
    }

    @Override
    protected void onDestroy() {
        cancelBreathingTimer();
        if (databaseExecutor != null) databaseExecutor.shutdown();
        super.onDestroy();
    }
}
