package com.example.cozymind;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Extra wellness tools, each opened from the Toolkit:
 * calm sounds, focus timer, gratitude jar, thought reframe, self-care checklist and body scan.
 */
public class ToolActivity extends Activity {

    static final String EXTRA_TOOL = "tool";
    static final String TOOL_SOUNDS = "sounds";
    static final String TOOL_FOCUS = "focus";
    static final String TOOL_GRATITUDE = "gratitude";
    static final String TOOL_REFRAME = "reframe";
    static final String TOOL_SELF_CARE = "selfcare";
    static final String TOOL_BODY_SCAN = "bodyscan";

    private static final String PREFS = "cozymind_tools";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout content;

    static Intent intent(Context context, String tool) {
        return new Intent(context, ToolActivity.class).putExtra(EXTRA_TOOL, tool);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_tool);
        applySystemBarInsets(findViewById(R.id.tool_root));
        content = findViewById(R.id.tool_content);
        findViewById(R.id.tool_back).setOnClickListener(view -> finish());

        String tool = getIntent().getStringExtra(EXTRA_TOOL);
        if (TOOL_SOUNDS.equals(tool)) setUpSounds();
        else if (TOOL_FOCUS.equals(tool)) setUpFocus();
        else if (TOOL_GRATITUDE.equals(tool)) setUpGratitude();
        else if (TOOL_REFRAME.equals(tool)) setUpReframe();
        else if (TOOL_SELF_CARE.equals(tool)) setUpSelfCare();
        else setUpBodyScan();
    }

    private void header(String emoji, String title, String subtitle) {
        ((TextView) findViewById(R.id.tool_emoji)).setText(emoji);
        ((TextView) findViewById(R.id.tool_title)).setText(title);
        ((TextView) findViewById(R.id.tool_subtitle)).setText(subtitle);
    }

    // =====================================================================
    // Calm Sounds: soothing noise generated on the phone, with a sleep timer.
    // =====================================================================

    private static final String[] SOUND_NAMES = {"🌧  Gentle rain", "🌊  Ocean waves", "🟤  Deep brown noise", "⚪  Soft white noise"};
    private static final int SAMPLE_RATE = 22050;

    private volatile boolean soundPlaying;
    private volatile int soundType;
    private Thread soundThread;
    private AudioTrack audioTrack;
    private TextView soundStatus;
    private final Runnable sleepTimerEnd = this::stopSound;

    private void setUpSounds() {
        header("🎧", "Calm Sounds", "Soft background sounds to relax, focus or fall asleep. Headphones make it extra cozy.");

        soundStatus = text("Choose a sound to begin", 15, R.color.ink, true);
        content.addView(soundStatus);

        for (int i = 0; i < SOUND_NAMES.length; i++) {
            int type = i;
            Button button = button(SOUND_NAMES[i], i % 2 == 0 ? R.drawable.bg_soft_blue : R.drawable.bg_lilac_card,
                    R.color.ink);
            button.setOnClickListener(view -> startSound(type));
            content.addView(button, fullWidth(54, 12));
        }

        content.addView(text("Sleep timer", 14, R.color.muted_ink, false), fullWidth(-2, 20));
        LinearLayout timers = row();
        int[] minutes = {15, 30, 60};
        for (int i = 0; i < minutes.length; i++) {
            int value = minutes[i];
            Button chip = button(value + " min", R.drawable.bg_outline_button, R.color.ink);
            chip.setOnClickListener(view -> {
                handler.removeCallbacks(sleepTimerEnd);
                handler.postDelayed(sleepTimerEnd, value * 60_000L);
                Toast.makeText(this, "The sound will fade out in " + value + " minutes. Sleep well 🌙",
                        Toast.LENGTH_SHORT).show();
            });
            timers.addView(chip, weighted(46, i == 0 ? 0 : 8));
        }
        content.addView(timers, fullWidth(-2, 8));

        Button stop = button("Stop sound", R.drawable.bg_register_button, R.color.surface);
        stop.setOnClickListener(view -> stopSound());
        content.addView(stop, fullWidth(52, 20));
    }

    private void startSound(int type) {
        soundType = type;
        soundStatus.setText("Now playing: " + SOUND_NAMES[type].substring(3).trim() + "  ♪");
        if (soundPlaying) return; // the running thread picks up the new type smoothly

        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(minBuffer, SAMPLE_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        soundPlaying = true;
        audioTrack.play();
        AudioTrack track = audioTrack;
        soundThread = new Thread(() -> generateNoise(track), "calm-sounds");
        soundThread.start();
    }

    /** Fills the speaker with gently shaped noise until stopped. Fades in at the start. */
    private void generateNoise(AudioTrack track) {
        Random random = new Random();
        short[] buffer = new short[1024];
        double brown = 0;
        double pink0 = 0, pink1 = 0, pink2 = 0;
        double fade = 0;
        long sample = 0;
        while (soundPlaying) {
            int type = soundType;
            for (int i = 0; i < buffer.length; i++) {
                double white = random.nextDouble() * 2 - 1;
                brown = (brown + 0.02 * white) / 1.02;
                pink0 = 0.99765 * pink0 + white * 0.0990460;
                pink1 = 0.96300 * pink1 + white * 0.2965164;
                pink2 = 0.57000 * pink2 + white * 1.0526913;
                double pink = (pink0 + pink1 + pink2 + white * 0.1848) * 0.11;

                double value;
                if (type == 0) {
                    value = pink * 0.9; // rain
                } else if (type == 1) {
                    double swell = 0.55 + 0.45 * Math.sin(2 * Math.PI * sample / (SAMPLE_RATE * 9.0));
                    value = brown * 3.2 * swell; // ocean waves rolling in and out
                } else if (type == 2) {
                    value = brown * 3.5; // deep brown noise
                } else {
                    value = white * 0.18; // soft white noise
                }
                if (fade < 1) fade += 1.0 / (SAMPLE_RATE * 2.0);
                value = Math.max(-1, Math.min(1, value * fade));
                buffer[i] = (short) (value * 12000);
                sample++;
            }
            track.write(buffer, 0, buffer.length);
        }
        try {
            track.stop();
        } catch (IllegalStateException ignored) {
            // Already stopped.
        }
        track.release();
    }

    private void stopSound() {
        handler.removeCallbacks(sleepTimerEnd);
        soundPlaying = false;
        audioTrack = null;
        if (soundStatus != null) soundStatus.setText("Choose a sound to begin");
    }

    // =====================================================================
    // Focus Timer: 25-minute focus, 5 or 15-minute breaks.
    // =====================================================================

    private CountDownTimer focusTimer;
    private long focusRemaining = 25 * 60_000L;
    private long focusLength = 25 * 60_000L;
    private boolean focusIsWork = true;
    private TextView focusClock;
    private TextView focusMode;
    private TextView focusCount;
    private ProgressBar focusProgress;
    private Button focusStart;

    private void setUpFocus() {
        header("⏱", "Focus Timer", "Work in calm, short bursts. Rest is part of the plan, not a reward.");

        focusMode = text("Focus session", 15, R.color.register_teal, true);
        content.addView(focusMode);

        focusClock = text("25:00", 56, R.color.ink, true);
        content.addView(focusClock, fullWidth(-2, 6));

        focusProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        focusProgress.setMax(1000);
        focusProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.register_teal)));
        content.addView(focusProgress, fullWidth(10, 10));

        LinearLayout modes = row();
        Button work = button("Focus 25", R.drawable.bg_outline_button, R.color.ink);
        Button shortBreak = button("Break 5", R.drawable.bg_outline_button, R.color.ink);
        Button longBreak = button("Break 15", R.drawable.bg_outline_button, R.color.ink);
        work.setOnClickListener(view -> setFocusMode(true, 25, "Focus session"));
        shortBreak.setOnClickListener(view -> setFocusMode(false, 5, "Short break · stretch or sip water"));
        longBreak.setOnClickListener(view -> setFocusMode(false, 15, "Long break · step away from the screen"));
        modes.addView(work, weighted(44, 0));
        modes.addView(shortBreak, weighted(44, 8));
        modes.addView(longBreak, weighted(44, 8));
        content.addView(modes, fullWidth(-2, 18));

        focusStart = button("Start", R.drawable.bg_register_button, R.color.surface);
        focusStart.setOnClickListener(view -> {
            if (focusTimer == null) startFocus();
            else pauseFocus();
        });
        content.addView(focusStart, fullWidth(54, 16));

        Button reset = button("Reset", R.drawable.bg_chip, R.color.muted_ink);
        reset.setOnClickListener(view -> setFocusMode(focusIsWork, (int) (focusLength / 60_000L),
                focusMode.getText().toString()));
        content.addView(reset, fullWidth(44, 8));

        focusCount = text("", 14, R.color.muted_ink, false);
        content.addView(focusCount, fullWidth(-2, 18));
        showFocusCount();
    }

    private String focusKey() {
        return "focus_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void showFocusCount() {
        int sessions = prefs().getInt(focusKey(), 0);
        focusCount.setText(sessions == 0 ? "No focus sessions yet today. You've got this. 🌱"
                : "🍅 " + sessions + " focus session" + (sessions == 1 ? "" : "s") + " completed today");
    }

    private void setFocusMode(boolean work, int minutes, String label) {
        pauseFocus();
        focusIsWork = work;
        focusLength = minutes * 60_000L;
        focusRemaining = focusLength;
        focusMode.setText(label);
        showFocusTime();
        focusStart.setText("Start");
    }

    private void startFocus() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        focusStart.setText("Pause");
        focusTimer = new CountDownTimer(focusRemaining, 500) {
            @Override
            public void onTick(long millisUntilFinished) {
                focusRemaining = millisUntilFinished;
                showFocusTime();
            }

            @Override
            public void onFinish() {
                focusTimer = null;
                focusRemaining = 0;
                showFocusTime();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                chime();
                if (focusIsWork) {
                    prefs().edit().putInt(focusKey(), prefs().getInt(focusKey(), 0) + 1).apply();
                    showFocusCount();
                    new AlertDialog.Builder(ToolActivity.this)
                            .setTitle("🎉  Session complete!")
                            .setMessage("Great focus. Take a 5-minute break: stretch, breathe, or drink some water.")
                            .setPositiveButton("Start break", (d, w) -> {
                                setFocusMode(false, 5, "Short break · stretch or sip water");
                                startFocus();
                            })
                            .setNegativeButton("Later", (d, w) -> setFocusMode(true, 25, "Focus session"))
                            .show();
                } else {
                    Toast.makeText(ToolActivity.this, "Break's over. Ready when you are 💚", Toast.LENGTH_LONG).show();
                    setFocusMode(true, 25, "Focus session");
                }
            }
        }.start();
    }

    private void pauseFocus() {
        if (focusTimer != null) {
            focusTimer.cancel();
            focusTimer = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (focusStart != null) focusStart.setText("Resume");
    }

    private void showFocusTime() {
        long seconds = (focusRemaining + 999) / 1000;
        focusClock.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
        focusProgress.setProgress((int) (1000 - focusRemaining * 1000 / Math.max(1, focusLength)));
    }

    private void chime() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 400);
            handler.postDelayed(tone::release, 600);
        } catch (RuntimeException ignored) {
            // Some devices have no tone generator; the dialog still appears.
        }
    }

    // =====================================================================
    // Gratitude Jar
    // =====================================================================

    private LinearLayout gratitudeList;
    private TextView gratitudeCount;

    private void setUpGratitude() {
        header("🫙", "Gratitude Jar", "Drop in one good thing, big or tiny. On hard days, pull out a memory.");

        gratitudeCount = text("", 15, R.color.ink, true);
        content.addView(gratitudeCount);

        EditText input = input("Today I'm grateful for…");
        content.addView(input, fullWidth(54, 14));

        Button add = button("Add to my jar  ✨", R.drawable.bg_register_button, R.color.surface);
        add.setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                input.setError("Write one good thing first.");
                return;
            }
            JSONArray items = loadArray("gratitude");
            JSONObject item = new JSONObject();
            try {
                item.put("text", value);
                item.put("date", new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date()));
            } catch (JSONException ignored) {
                return;
            }
            items.put(item);
            saveArray("gratitude", items);
            input.setText("");
            Toast.makeText(this, "Added to your jar 🫙", Toast.LENGTH_SHORT).show();
            showGratitude();
        });
        content.addView(add, fullWidth(52, 10));

        Button memory = button("🌟  Pull out a memory", R.drawable.bg_yellow_card, R.color.ink);
        memory.setOnClickListener(view -> {
            JSONArray items = loadArray("gratitude");
            if (items.length() == 0) {
                Toast.makeText(this, "Your jar is empty for now. Add your first good thing!", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject item = items.optJSONObject(new Random().nextInt(items.length()));
            new AlertDialog.Builder(this)
                    .setTitle("🌟  From your jar")
                    .setMessage("\"" + item.optString("text") + "\"\n\n" + item.optString("date"))
                    .setPositiveButton("That's lovely", null)
                    .show();
        });
        content.addView(memory, fullWidth(52, 10));

        content.addView(text("In your jar", 14, R.color.muted_ink, true), fullWidth(-2, 22));
        gratitudeList = new LinearLayout(this);
        gratitudeList.setOrientation(LinearLayout.VERTICAL);
        content.addView(gratitudeList, fullWidth(-2, 8));
        showGratitude();
    }

    private void showGratitude() {
        JSONArray items = loadArray("gratitude");
        gratitudeCount.setText(items.length() == 0 ? "Your jar is waiting for its first good thing"
                : "🫙 " + items.length() + " good thing" + (items.length() == 1 ? "" : "s") + " in your jar");
        gratitudeList.removeAllViews();
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int index = i;
            TextView card = card("💛  " + item.optString("text") + "\n" + item.optString("date"), R.drawable.bg_outline_card);
            card.setOnClickListener(view -> confirmRemove(() -> {
                saveArray("gratitude", without(loadArray("gratitude"), index));
                showGratitude();
            }));
            gratitudeList.addView(card, fullWidth(-2, 8));
        }
    }

    // =====================================================================
    // Thought Reframe (a simple CBT exercise)
    // =====================================================================

    private LinearLayout reframeList;

    private void setUpReframe() {
        header("💭", "Thought Reframe", "Thoughts are not always facts. Answer gently, there are no wrong answers.");

        EditText thought = question("1.  What thought is bothering you?", "e.g. I'm going to fail my exam");
        EditText evidence = question("2.  What facts support it, and what facts don't?", "e.g. I passed the last two quizzes…");
        EditText friend = question("3.  What would you tell a friend who thought this?", "e.g. You've prepared, one exam doesn't define you");
        EditText balanced = question("4.  What is a kinder, more balanced thought?", "e.g. I'm nervous, but I've studied and I can do my best");

        Button save = button("Save my reframe  💚", R.drawable.bg_register_button, R.color.surface);
        save.setOnClickListener(view -> {
            String first = thought.getText().toString().trim();
            String last = balanced.getText().toString().trim();
            if (first.isEmpty() || last.isEmpty()) {
                Toast.makeText(this, "Fill in at least the first and last questions.", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONArray items = loadArray("reframes");
            JSONObject item = new JSONObject();
            try {
                item.put("thought", first);
                item.put("balanced", last);
                item.put("date", new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date()));
            } catch (JSONException ignored) {
                return;
            }
            items.put(item);
            saveArray("reframes", items);
            thought.setText("");
            evidence.setText("");
            friend.setText("");
            balanced.setText("");
            new AlertDialog.Builder(this)
                    .setTitle("Well done 💚")
                    .setMessage("You just practised looking at a thought from a new angle. That gets easier every time.")
                    .setPositiveButton("Thanks", null)
                    .show();
            showReframes();
        });
        content.addView(save, fullWidth(52, 16));

        content.addView(text("Your past reframes", 14, R.color.muted_ink, true), fullWidth(-2, 22));
        reframeList = new LinearLayout(this);
        reframeList.setOrientation(LinearLayout.VERTICAL);
        content.addView(reframeList, fullWidth(-2, 8));
        showReframes();
    }

    private EditText question(String label, String hint) {
        TextView title = text(label, 14, R.color.ink, true);
        title.setGravity(Gravity.START);
        content.addView(title, fullWidth(-2, 14));
        EditText field = input(hint);
        field.setMinHeight(dp(54));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(field, fullWidth(-2, 6));
        return field;
    }

    private void showReframes() {
        JSONArray items = loadArray("reframes");
        reframeList.removeAllViews();
        if (items.length() == 0) {
            reframeList.addView(text("Your saved reframes will appear here.", 13, R.color.muted_ink, false));
            return;
        }
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int index = i;
            TextView card = card("☁  " + item.optString("thought") + "\n🌤  " + item.optString("balanced")
                    + "\n" + item.optString("date"), R.drawable.bg_lilac_card);
            card.setOnClickListener(view -> confirmRemove(() -> {
                saveArray("reframes", without(loadArray("reframes"), index));
                showReframes();
            }));
            reframeList.addView(card, fullWidth(-2, 8));
        }
    }

    // =====================================================================
    // Self-Care Checklist (resets every day)
    // =====================================================================

    private static final String[] SELF_CARE = {
            "💧  Drink a glass of water",
            "🚶  Move your body for 10 minutes",
            "🌞  Get some sunlight or fresh air",
            "🍎  Eat something nourishing",
            "📵  Take a 15-minute screen break",
            "💬  Talk to someone you like",
            "🎨  Do one thing just for fun",
            "😴  Get ready for bed on time",
    };

    private TextView selfCareProgress;

    private void setUpSelfCare() {
        header("✅", "Self-Care Checklist", "Small acts of care add up. Tick what you can today, it resets every morning.");

        selfCareProgress = text("", 15, R.color.register_teal, true);
        content.addView(selfCareProgress);

        Set<String> done = new HashSet<>(prefs().getStringSet(selfCareKey(), new HashSet<>()));
        for (int i = 0; i < SELF_CARE.length; i++) {
            String key = String.valueOf(i);
            CheckBox box = new CheckBox(this);
            box.setText(SELF_CARE[i]);
            box.setTextSize(16);
            box.setTextColor(getColor(R.color.ink));
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.register_teal)));
            box.setBackgroundResource(i % 2 == 0 ? R.drawable.bg_green_card : R.drawable.bg_soft_blue);
            box.setPadding(dp(8), dp(12), dp(12), dp(12));
            box.setChecked(done.contains(key));
            box.setOnCheckedChangeListener((button, checked) -> {
                Set<String> current = new HashSet<>(prefs().getStringSet(selfCareKey(), new HashSet<>()));
                if (checked) current.add(key);
                else current.remove(key);
                prefs().edit().putStringSet(selfCareKey(), current).apply();
                showSelfCareProgress(current.size(), checked);
            });
            content.addView(box, fullWidth(-2, 10));
        }
        showSelfCareProgress(done.size(), false);
    }

    private String selfCareKey() {
        return "selfcare_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void showSelfCareProgress(int count, boolean justChecked) {
        if (count == SELF_CARE.length) {
            selfCareProgress.setText("🌟 All " + count + " done! You really took care of yourself today.");
            if (justChecked) Toast.makeText(this, "🎉 Self-care complete for today!", Toast.LENGTH_LONG).show();
        } else {
            selfCareProgress.setText(count + " of " + SELF_CARE.length + " done today · every little bit counts");
        }
    }

    // =====================================================================
    // Body Scan: a guided relaxation that moves through the body.
    // =====================================================================

    private static final String[] BODY_STEPS = {
            "Get comfortable. Let your eyes close or soften your gaze. Take three slow breaths.",
            "Notice your feet. Curl your toes tightly… then let them relax completely.",
            "Move up to your legs. Feel their weight. Let them sink down and soften.",
            "Notice your belly rise and fall with each breath. No need to change it.",
            "Squeeze your hands into fists… hold… and slowly let them open.",
            "Lift your shoulders up to your ears… hold… and let them drop. Feel the release.",
            "Relax your face. Unclench your jaw, soften your forehead and the space between your eyes.",
            "Feel your whole body at once, heavy and calm. Stay here as long as you like. 💚",
    };
    private static final long BODY_STEP_MS = 22_000;

    private CountDownTimer bodyTimer;
    private int bodyStep;
    private TextView bodyText;
    private TextView bodyCounter;
    private ProgressBar bodyProgress;
    private Button bodyStart;

    private void setUpBodyScan() {
        header("🧘", "Body Scan", "A 3-minute guided relaxation. Find a quiet spot, sit or lie down, and follow along.");

        bodyCounter = text("8 gentle steps · about 3 minutes", 14, R.color.register_teal, true);
        content.addView(bodyCounter);

        bodyText = card("Press start when you are ready. Each step moves on by itself.", R.drawable.bg_quote_card);
        bodyText.setGravity(Gravity.CENTER);
        bodyText.setTextSize(19);
        bodyText.setMinHeight(dp(170));
        content.addView(bodyText, fullWidth(-2, 14));

        bodyProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bodyProgress.setMax(BODY_STEPS.length);
        bodyProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.register_teal)));
        content.addView(bodyProgress, fullWidth(10, 14));

        bodyStart = button("Start body scan", R.drawable.bg_register_button, R.color.surface);
        bodyStart.setOnClickListener(view -> {
            if (bodyTimer != null) stopBodyScan("Paused. Press start to begin again.");
            else runBodyStep(0);
        });
        content.addView(bodyStart, fullWidth(54, 18));
    }

    private void runBodyStep(int step) {
        bodyStep = step;
        if (step >= BODY_STEPS.length) {
            stopBodyScan("You finished your body scan. Notice how calm you feel now. 💚");
            bodyProgress.setProgress(BODY_STEPS.length);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bodyStart.setText("Stop");
        bodyCounter.setText("Step " + (step + 1) + " of " + BODY_STEPS.length);
        bodyProgress.setProgress(step + 1);
        bodyText.setAlpha(0f);
        bodyText.setText(BODY_STEPS[step]);
        bodyText.animate().alpha(1f).setDuration(800).start();
        bodyTimer = new CountDownTimer(BODY_STEP_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                bodyTimer = null;
                runBodyStep(bodyStep + 1);
            }
        }.start();
    }

    private void stopBodyScan(String message) {
        if (bodyTimer != null) {
            bodyTimer.cancel();
            bodyTimer = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bodyText.setText(message);
        bodyStart.setText("Start body scan");
        bodyCounter.setText("8 gentle steps · about 3 minutes");
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private JSONArray loadArray(String key) {
        try {
            return new JSONArray(prefs().getString(key, "[]"));
        } catch (JSONException exception) {
            return new JSONArray();
        }
    }

    private void saveArray(String key, JSONArray array) {
        prefs().edit().putString(key, array.toString()).apply();
    }

    private JSONArray without(JSONArray array, int removeIndex) {
        JSONArray copy = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            if (i != removeIndex) copy.put(array.opt(i));
        }
        return copy;
    }

    private void confirmRemove(Runnable remove) {
        new AlertDialog.Builder(this)
                .setTitle("Remove this?")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Remove", (dialog, which) -> remove.run())
                .show();
    }

    private TextView text(String value, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(colorRes));
        view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView card(String value, int backgroundRes) {
        TextView view = text(value, 15, R.color.ink, false);
        view.setGravity(Gravity.START);
        view.setBackgroundResource(backgroundRes);
        view.setPadding(dp(18), dp(14), dp(18), dp(14));
        return view;
    }

    private Button button(String label, int backgroundRes, int textColorRes) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(getColor(textColorRes));
        button.setBackgroundResource(backgroundRes);
        button.setStateListAnimator(null);
        return button;
    }

    private EditText input(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextColor(getColor(R.color.ink));
        field.setHintTextColor(getColor(R.color.muted_ink));
        field.setTextSize(15);
        field.setBackgroundResource(R.drawable.bg_input);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return field;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    /** Full-width params; heightDp of -2 means wrap content. */
    private LinearLayout.LayoutParams fullWidth(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightDp == -2 ? LinearLayout.LayoutParams.WRAP_CONTENT : dp(heightDp));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams weighted(int heightDp, int startMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(heightDp), 1f);
        params.setMarginStart(dp(startMarginDp));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Keeps the content below the camera/status area and above gesture navigation. */
    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        soundPlaying = false;
        if (focusTimer != null) focusTimer.cancel();
        if (bodyTimer != null) bodyTimer.cancel();
        super.onDestroy();
    }
}
