package com.example.cozymind;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Build;
import android.text.TextUtils;
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
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cozymind.data.AppDatabase;
import com.example.cozymind.data.User;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends Activity {
    private enum Screen {
        LOGIN, SIGN_UP, WELCOME, ONBOARDING_FOCUS, ONBOARDING_MOOD, ONBOARDING_ROUTINE,
        DASHBOARD, TOOLKIT, MOOD, JOURNAL, BREATHE, PROFILE, COMFORT_WORDS
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
        subtitle.setText("Good morning, " + name + ".");
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
        wireBottomNavigation(Screen.TOOLKIT);
    }

    private void showProfile() {
        cancelBreathingTimer();
        currentScreen = Screen.PROFILE;
        showScreen(R.layout.screen_profile);
        ((TextView) findViewById(R.id.profile_initial)).setText("♡");
        ((TextView) findViewById(R.id.profile_name)).setText(getSavedName());
        ((TextView) findViewById(R.id.profile_subtitle)).setText("Member since today");
        findViewById(R.id.profile_sign_out).setOnClickListener(view -> signOut());
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

        View.OnClickListener openEditor = view -> {
            editor.setVisibility(View.VISIBLE);
            title.requestFocus();
        };
        findViewById(R.id.new_journal_entry).setOnClickListener(openEditor);
        findViewById(R.id.write_response).setOnClickListener(openEditor);
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
                    Toast.makeText(this, moodSupportMessage(moodLabel(severity)), Toast.LENGTH_LONG).show();
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
        final String entryTitle = title.isEmpty() ? "A quiet moment" : title;
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
                    history.removeAllViews();
                    if (response.moods.isEmpty()) {
                        addEmptyMessage(history, "Your saved check-ins will appear here.");
                        return;
                    }
                    for (CozymindApi.Mood checkIn : response.moods) {
                        addHistoryCard(history, moodLabel(checkIn.severity) + " · " + checkIn.severity
                                + " / 5\n" + checkIn.datetimeMood + " UTC");
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
                    entries.removeAllViews();
                    if (journalEntries.isEmpty()) {
                        addEmptyMessage(entries, "Your private journal entries will appear here.");
                        return;
                    }
                    for (CozymindApi.Journal entry : journalEntries) {
                        addHistoryCard(entries, entry.title + "\n" + entry.body + "\n"
                                + entry.createdAt + " UTC");
                    }
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

    private void addHistoryCard(LinearLayout container, String text) {
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
        return "\"Choose me, love me, or let me handle everything else for you.\"";
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
        } else if (currentScreen == Screen.COMFORT_WORDS) {
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
