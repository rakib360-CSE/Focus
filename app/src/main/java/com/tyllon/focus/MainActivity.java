package com.tyllon.focus;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LinearLayout splashLayout, normalModeLayout, focusModeLayout;
    private NotificationManager notificationManager;
    private TextView tvSelectedTime, tvTimerDisplay;
    private CountDownTimer countDownTimer;

    private long selectedTimeInMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashLayout = findViewById(R.id.splashLayout);
        normalModeLayout = findViewById(R.id.normalModeLayout);
        focusModeLayout = findViewById(R.id.focusModeLayout);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Button btn30Min = findViewById(R.id.btn30Min);
        Button btn1Hour = findViewById(R.id.btn1Hour);
        Button btn3Hour = findViewById(R.id.btn3Hour);
        Button btnPickTime = findViewById(R.id.btnPickTime);
        Button btnStartFocus = findViewById(R.id.btnStartFocus);
        ImageView splashIcon = findViewById(R.id.splashIcon);
        TextView splashTitle = findViewById(R.id.splashTitle);

        checkDNDPermission();
        playSplashAnimation(splashIcon, splashTitle);

        btn30Min.setOnClickListener(v -> updateSelectedTime(30 * 60000L, "30 Minutes"));
        btn1Hour.setOnClickListener(v -> updateSelectedTime(60 * 60000L, "1 Hour"));
        btn3Hour.setOnClickListener(v -> updateSelectedTime(3 * 60 * 60000L, "3 Hours"));

        btnPickTime.setOnClickListener(v -> showTimePicker());

        btnStartFocus.setOnClickListener(v -> {
            if (selectedTimeInMillis <= 0) {
                Toast.makeText(this, "Please select a time first!", Toast.LENGTH_SHORT).show();
                return;
            }
            startFocusMode(selectedTimeInMillis);
        });

        // ব্যাক বাটন ডিজেবল করা
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (focusModeLayout.getVisibility() == View.VISIBLE) {
                    Toast.makeText(MainActivity.this, "Focus mode is active! Wait for the timer.", Toast.LENGTH_SHORT).show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && focusModeLayout != null && focusModeLayout.getVisibility() == View.VISIBLE) {
            hideSystemUI();
            focusModeLayout.postDelayed(() -> {
                try {
                    startLockTask();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 500);
        }
    }

    private void playSplashAnimation(ImageView icon, TextView title) {
        icon.animate().alpha(1f).setDuration(1000).start();
        title.animate().alpha(1f).setDuration(1000).setStartDelay(500).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                splashLayout.postDelayed(() -> {
                    splashLayout.setVisibility(View.GONE);
                    normalModeLayout.setVisibility(View.VISIBLE);
                }, 1000);
            }
        }).start();
    }

    private void updateSelectedTime(long millis, String textToShow) {
        selectedTimeInMillis = millis;
        tvSelectedTime.setText("Selected: " + textToShow);
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            long millis = (hourOfDay * 60L * 60000L) + (minute * 60000L);
            if (millis > 0) {
                updateSelectedTime(millis, hourOfDay + "h " + minute + "m");
            } else {
                Toast.makeText(this, "Please select a valid time", Toast.LENGTH_SHORT).show();
            }
        }, 0, 0, true);
        timePickerDialog.setTitle("Select Focus Time");
        timePickerDialog.show();
    }

    private void startFocusMode(long durationInMillis) {
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            checkDNDPermission();
            return;
        }

        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        normalModeLayout.setVisibility(View.GONE);
        focusModeLayout.setVisibility(View.VISIBLE);
        hideSystemUI();
        startLockTask();

        countDownTimer = new CountDownTimer(durationInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int hours = (int) (millisUntilFinished / (1000 * 60 * 60));
                int minutes = (int) (millisUntilFinished / (1000 * 60)) % 60;
                int seconds = (int) (millisUntilFinished / 1000) % 60;
                String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
                tvTimerDisplay.setText(timeFormatted);
            }

            @Override
            public void onFinish() {
                stopFocusMode();
            }
        }.start();
    }

    private void stopFocusMode() {
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        normalModeLayout.setVisibility(View.VISIBLE);
        focusModeLayout.setVisibility(View.GONE);

        try {
            stopLockTask();
        } catch (Exception e) {
            e.printStackTrace();
        }

        selectedTimeInMillis = 0;
        tvSelectedTime.setText("Selected: 0 Min");

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    private void checkDNDPermission() {
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}