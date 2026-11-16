package com.example.cozymind;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Calm Start: the first screen every time Cozymind opens.
 * A gentle breathing circle, a daily affirmation, a water tracker,
 * a 5-4-3-2-1 grounding exercise and crisis hotline numbers.
 */
public class CalmStartActivity extends Activity {

    private static final int WATER_GOAL = WellnessStore.WATER_GOAL;

    /** Guided breathing: 4 seconds in, 2 hold, 6 out. */
    private static final long INHALE_MS = 4000;
    private static final long HOLD_MS = 2000;
    private static final long EXHALE_MS = 6000;
    private static final long GUIDED_SESSION_MS = 60_000;

    private static final String[] GROUNDING_STEPS = {
            "👀  Look around and name 5 things you can SEE.",
            "✋  Notice 4 things you can TOUCH. Feel their texture.",
            "👂  Listen for 3 things you can HEAR right now.",
            "👃  Find 2 things you can SMELL, or two scents you like.",
            "👅  Notice 1 thing you can TASTE.",
            "💚  Well done. Take one slow breath. You are here, and you are safe.",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView breathOrb;
    private View breathGlow;
    private TextView breathHint;
    private Button breathToggle;
    private AnimatorSet idlePulse;
    private AnimatorSet guidedCycle;
    private boolean guidedRunning;
    private long guidedEndsAt;

    private TextView affirmationView;
    private int affirmationIndex;

    private TextView waterCount;
    private TextView waterDots;

    private TextView groundStep;
    private Button groundNext;
    private int groundIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_calm_start);
        applySystemBarInsets(findViewById(R.id.calm_root));

        setUpGreeting();
        setUpBreathing();
        setUpAffirmation();
        setUpWater();
        setUpGrounding();
        setUpHelpLines();

        findViewById(R.id.enter_app).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.calm_window_enter, R.anim.calm_window_exit);
            finish();
        });
    }

    /** Keeps the content below the camera/status area and above gesture navigation. */
    private void applySystemBarInsets(View root) {
        int baseTop = root.getPaddingTop();
        int baseBottom = root.getPaddingBottom();
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
            view.setPadding(view.getPaddingLeft(), baseTop + top, view.getPaddingRight(), baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    // ---------- Greeting ----------

    private void setUpGreeting() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 5) greeting = "Still awake? Be gentle tonight 🌙";
        else if (hour < 12) greeting = "Good morning ☀";
        else if (hour < 18) greeting = "Good afternoon 🌤";
        else greeting = "Good evening 🌙";
        ((TextView) findViewById(R.id.calm_greeting)).setText(greeting);

        String date = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now.getTime());
        ((TextView) findViewById(R.id.calm_date)).setText(date + "  •  Take a moment for you");
    }

    // ---------- Breathing ----------

    private void setUpBreathing() {
        breathOrb = findViewById(R.id.breath_orb);
        breathGlow = findViewById(R.id.breath_glow);
        breathHint = findViewById(R.id.breath_hint);
        breathToggle = findViewById(R.id.breath_toggle);

        startIdlePulse();
        breathToggle.setOnClickListener(v -> {
            if (guidedRunning) stopGuided(false);
            else startGuided();
        });
    }

    /** A slow, wordless pulse so the screen always feels alive and calm. */
    private void startIdlePulse() {
        breathOrb.setText("Breathe");
        idlePulse = new AnimatorSet();
        idlePulse.playTogether(
                pulse(breathOrb, 0.88f, 1.0f, 3500),
                pulse(breathGlow, 0.85f, 1.05f, 3500));
        idlePulse.start();
    }

    private ObjectAnimator[] scale(View view, float from, float to, long duration) {
        ObjectAnimator x = ObjectAnimator.ofFloat(view, "scaleX", from, to);
        ObjectAnimator y = ObjectAnimator.ofFloat(view, "scaleY", from, to);
        x.setDuration(duration);
        y.setDuration(duration);
        x.setInterpolator(new AccelerateDecelerateInterpolator());
        y.setInterpolator(new AccelerateDecelerateInterpolator());
        return new ObjectAnimator[]{x, y};
    }

    private AnimatorSet pulse(View view, float from, float to, long duration) {
        ObjectAnimator[] parts = scale(view, from, to, duration);
        for (ObjectAnimator part : parts) {
            part.setRepeatCount(ValueAnimator.INFINITE);
            part.setRepeatMode(ValueAnimator.REVERSE);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(parts[0], parts[1]);
        return set;
    }

    private void startGuided() {
        if (idlePulse != null) idlePulse.cancel();
        guidedRunning = true;
        guidedEndsAt = System.currentTimeMillis() + GUIDED_SESSION_MS;
        breathToggle.setText("Stop");
        breathHint.setText("Follow the circle. In through your nose, out through your mouth.");
        runGuidedCycle();
    }

    private void runGuidedCycle() {
        if (!guidedRunning) return;
        if (System.currentTimeMillis() >= guidedEndsAt) {
            stopGuided(true);
            return;
        }

        ObjectAnimator[] in = scale(breathOrb, 0.75f, 1.15f, INHALE_MS);
        ObjectAnimator[] out = scale(breathOrb, 1.15f, 0.75f, EXHALE_MS);
        out[0].setStartDelay(HOLD_MS);
        out[1].setStartDelay(HOLD_MS);

        AnimatorSet inhale = new AnimatorSet();
        inhale.playTogether(in[0], in[1]);
        AnimatorSet exhale = new AnimatorSet();
        exhale.playTogether(out[0], out[1]);

        breathOrb.setText("Breathe in");
        handler.postDelayed(() -> { if (guidedRunning) breathOrb.setText("Hold"); }, INHALE_MS);
        handler.postDelayed(() -> { if (guidedRunning) breathOrb.setText("Breathe out"); },
                INHALE_MS + HOLD_MS);

        guidedCycle = new AnimatorSet();
        guidedCycle.playSequentially(inhale, exhale);
        guidedCycle.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) runGuidedCycle();
            }
        });
        guidedCycle.start();
    }

    private void stopGuided(boolean finished) {
        guidedRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (guidedCycle != null) guidedCycle.cancel();
        breathOrb.setScaleX(1f);
        breathOrb.setScaleY(1f);
        breathToggle.setText("Start 1-minute breathing");
        breathHint.setText(finished
                ? "Beautiful. Notice how your body feels now. 💚"
                : "Let the circle set your pace. In as it grows, out as it shrinks.");
        startIdlePulse();
    }

    // ---------- Affirmation ----------

    private void setUpAffirmation() {
        affirmationView = findViewById(R.id.calm_affirmation);
        // A different affirmation each day of the year.
        affirmationIndex = WellnessStore.todaysAffirmationIndex();
        affirmationView.setText(WellnessStore.AFFIRMATIONS[affirmationIndex]);

        findViewById(R.id.affirmation_next).setOnClickListener(v -> {
            affirmationIndex = (affirmationIndex + 1) % WellnessStore.AFFIRMATIONS.length;
            affirmationView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                affirmationView.setText(WellnessStore.AFFIRMATIONS[affirmationIndex]);
                affirmationView.animate().alpha(1f).setDuration(300).start();
            }).start();
        });
    }

    // ---------- Water ----------

    private void setUpWater() {
        waterCount = findViewById(R.id.water_count);
        waterDots = findViewById(R.id.water_dots);
        findViewById(R.id.water_plus).setOnClickListener(v -> changeWater(1));
        findViewById(R.id.water_minus).setOnClickListener(v -> changeWater(-1));
        showWater(WellnessStore.waterToday(this));
    }

    private void changeWater(int delta) {
        int glasses = WellnessStore.changeWater(this, delta);
        showWater(glasses);
        if (delta > 0 && glasses == WATER_GOAL) {
            Toast.makeText(this, "You reached your water goal today! 🎉", Toast.LENGTH_SHORT).show();
        }
    }

    private void showWater(int glasses) {
        waterCount.setText(glasses >= WATER_GOAL
                ? "Goal reached: " + glasses + " glasses 🎉"
                : glasses + " of " + WATER_GOAL + " glasses today");
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < WATER_GOAL; i++) {
            if (i > 0) dots.append(' ');
            dots.append(i < glasses ? '●' : '○');
        }
        waterDots.setText(dots.toString());
    }

    // ---------- Grounding ----------

    private void setUpGrounding() {
        groundStep = findViewById(R.id.ground_step);
        groundNext = findViewById(R.id.ground_next);
        groundNext.setOnClickListener(v -> {
            groundIndex++;
            if (groundIndex >= GROUNDING_STEPS.length) {
                groundIndex = -1;
                groundStep.setText("A grounding exercise that brings you back to the present, one sense at a time.");
                groundNext.setText("Begin");
                return;
            }
            groundStep.setAlpha(0f);
            groundStep.setText(GROUNDING_STEPS[groundIndex]);
            groundStep.animate().alpha(1f).setDuration(350).start();
            groundNext.setText(groundIndex == GROUNDING_STEPS.length - 1 ? "Start over" : "Next");
        });
    }

    // ---------- Help lines ----------

    private void setUpHelpLines() {
        findViewById(R.id.call_1553).setOnClickListener(v -> dial("1553"));
        findViewById(R.id.call_globe).setOnClickListener(v -> dial("09178998727"));
        findViewById(R.id.call_smart).setOnClickListener(v -> dial("09190571553"));
    }

    /** Opens the phone dialer with the number filled in; the person presses call themselves. */
    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Please call " + number + " from your phone.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (idlePulse != null) idlePulse.cancel();
        if (guidedCycle != null) guidedCycle.cancel();
        super.onDestroy();
    }
}
