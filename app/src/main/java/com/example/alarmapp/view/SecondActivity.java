package com.example.alarmapp.view;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.alarmapp.R;
import com.example.alarmapp.broadcast.AlarmReceiver;
import com.example.alarmapp.dao.AlarmDAO;
import com.example.alarmapp.database.AlarmDatabase;
import com.example.alarmapp.model.Alarm;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SecondActivity extends AppCompatActivity {

    private AlarmDAO alarmDAO;
    private TimePicker timePicker;
    private Button selectDateButton, saveAlarmButton, cancelButton;
    private Switch repeatDailySwitch, repeatWeeklySwitch;

    private Date selectedDate;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private int alarmId = -1; // Default value indicating no alarm to edit

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        alarmDAO = AlarmDatabase.getInstance(this).alarmDAO();

        timePicker = findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        selectDateButton = findViewById(R.id.selectDateButton);
        saveAlarmButton = findViewById(R.id.saveAlarmButton);
        cancelButton = findViewById(R.id.cancelButton);

        repeatDailySwitch = findViewById(R.id.repeatDailySwitch);
        repeatWeeklySwitch = findViewById(R.id.repeatWeeklySwitch);

        selectedDate = new Date();

        selectDateButton.setOnClickListener(v -> openDatePickerDialog());
        saveAlarmButton.setOnClickListener(v -> saveAlarm());
        cancelButton.setOnClickListener(v -> cancelSelection());

        // Retrieve the alarm ID from the intent
        alarmId = getIntent().getIntExtra("alarmId", -1);

        if (alarmId != -1) {
            Alarm alarm = alarmDAO.getAlarm(alarmId);
            if (alarm != null) {
                timePicker.setHour(alarm.getTime().getHours());
                timePicker.setMinute(alarm.getTime().getMinutes());
                selectedDate = alarm.getDate();
                selectDateButton.setText(dateFormat.format(selectedDate));
                repeatDailySwitch.setChecked(alarm.isRepeatDaily());
                repeatWeeklySwitch.setChecked(alarm.isRepeatWeekly());
            }
        } else {
            selectDateButton.setText(dateFormat.format(selectedDate));
        }

        String packageName = getPackageName();
        Intent intent = new Intent();
        if (!isIgnoringBatteryOptimizations(this, packageName)) {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }

    private boolean isIgnoringBatteryOptimizations(Context context, String packageName) {
        return context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(packageName);
    }

    private void openDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                R.style.CustomDatePickerDialog,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    selectedDate = calendar.getTime();
                    selectDateButton.setText(dateFormat.format(selectedDate));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void saveAlarm() {
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        boolean repeatDaily = repeatDailySwitch.isChecked();
        boolean repeatWeekly = repeatWeeklySwitch.isChecked();

        Date alarmTime = calendar.getTime();

        Alarm alarm = new Alarm(alarmTime, selectedDate, repeatDaily, repeatWeekly, true);

        if (alarmId == -1) {
            alarmId = (int) alarmDAO.insertAlarm(alarm);
            scheduleAlarm(alarmId, alarmTime);
            Toast.makeText(this, "Alarm set for " + dateFormat.format(selectedDate) + " at " + String.format(Locale.getDefault(), "%02d:%02d", hour, minute), Toast.LENGTH_SHORT).show();
        } else {
            alarm.setId(alarmId);
            alarmDAO.updateAlarm(alarm);
            cancelScheduledAlarm(alarmId);
            scheduleAlarm(alarmId, alarmTime);
            Toast.makeText(this, "Alarm updated for " + dateFormat.format(selectedDate) + " at " + String.format(Locale.getDefault(), "%02d:%02d", hour, minute), Toast.LENGTH_SHORT).show();
        }

        Intent intent = new Intent(SecondActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleAlarm(int alarmId, Date alarmTime) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("alarmId", alarmId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime.getTime(), pendingIntent);
        }
    }

    private void cancelScheduledAlarm(int alarmId) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarmId, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void cancelSelection() {
        Intent intent = new Intent(SecondActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
