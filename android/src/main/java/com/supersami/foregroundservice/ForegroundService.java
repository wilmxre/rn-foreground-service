package com.supersami.foregroundservice;

import static com.supersami.foregroundservice.Constants.NOTIFICATION_CONFIG;
import static com.supersami.foregroundservice.Constants.TASK_CONFIG;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

public class ForegroundService extends Service {

    private static ForegroundService mInstance = null;
    private static Bundle lastNotificationConfig = null;
    private int running = 0;

    public static boolean isServiceCreated() {
        try {
            return mInstance != null && mInstance.ping();
        } catch (NullPointerException e) {
            return false;
        }
    }

    public static ForegroundService getInstance() {
        if (isServiceCreated()) {
            return mInstance;
        }
        return null;
    }

    public int isRunning() {
        return running;
    }

    private boolean ping() {
        return true;
    }

    @Override
    public void onCreate() {
        Log.d("SuperLog", "onCreate new process now: " + running);

        running = 0;
        mInstance = this;
    }

    @Override
    public void onDestroy() {
        Log.d("SuperLog", "onDestroy");
        this.handler.removeCallbacks(this.runnableCode);
        running = 0;
        mInstance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("SuperLog", "onTaskRemoved yoooo");
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);

        super.onTaskRemoved(rootIntent);
    }

    private boolean startService(Bundle notificationConfig) {
        try {
            int id = (int) notificationConfig.getDouble("id");

            Notification notification = NotificationHelper
                    .getInstance(getApplicationContext())
                    .buildNotification(getApplicationContext(), notificationConfig);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(id, notification, 8);
            } else {
                startForeground(id, notification);
            }

            running += 1;
            lastNotificationConfig = notificationConfig;

            return true;
        } catch (Exception e) {
            Log.e("ForegroundService", "Failed to start service: " + e.getMessage());
            return false;
        }
    }

    private void scheduleServiceRestart() {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000, // restart after 1 second
                restartServicePendingIntent);

        Log.d("SuperLog", "Service is scheduled to restart");
    }

    public Bundle taskConfig;
    private Handler handler = new Handler();
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            final Intent service = new Intent(getApplicationContext(), ForegroundServiceTask.class);
            service.putExtras(taskConfig);
            getApplicationContext().startService(service);

            int loopDelay = (int) taskConfig.getDouble("loopDelay");
            Log.d("SuperLog", "" + loopDelay);
            handler.postDelayed(this, loopDelay);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        if (action != null) {
            if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_START)) {
                if (intent.getExtras() != null && intent.getExtras().containsKey(NOTIFICATION_CONFIG)) {
                    Bundle notificationConfig = intent.getExtras().getBundle(NOTIFICATION_CONFIG);
                    startService(notificationConfig);
                }
            } else if (action.equals(Constants.ACTION_UPDATE_NOTIFICATION)) {
                if (intent.getExtras() != null && intent.getExtras().containsKey(NOTIFICATION_CONFIG)) {
                    Bundle notificationConfig = intent.getExtras().getBundle(NOTIFICATION_CONFIG);

                    if (running <= 0) {
                        Log.d("ForegroundService", "Update Notification called without a running service, trying to restart service.");
                        startService(notificationConfig);
                    } else {
                        try {
                            int id = (int) notificationConfig.getDouble("id");

                            Notification notification = NotificationHelper
                                    .getInstance(getApplicationContext())
                                    .buildNotification(getApplicationContext(), notificationConfig);

                            NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
                            mNotificationManager.notify(id, notification);

                            lastNotificationConfig = notificationConfig;

                        } catch (Exception e) {
                            Log.e("ForegroundService", "Failed to update notification: " + e.getMessage());
                        }
                    }

                }
            } else if (action.equals(Constants.ACTION_FOREGROUND_RUN_TASK)) {
                if (running <= 0 && lastNotificationConfig == null) {
                    Log.e("ForegroundService", "Service is not running to run tasks.");
                    stopSelf();
                    return START_NOT_STICKY;
                } else {
                    if (running <= 0) {
                        Log.d("ForegroundService", "Run Task called without a running service, trying to restart service.");
                        if (!startService(lastNotificationConfig)) {
                            Log.e("ForegroundService", "Service is not running to run tasks.");
                            return START_STICKY;
                        }
                    }

                    if (intent.getExtras() != null && intent.getExtras().containsKey(TASK_CONFIG)) {
                        taskConfig = intent.getExtras().getBundle(TASK_CONFIG);

                        try {
                            if (taskConfig.getBoolean("onLoop")) {
                                this.handler.post(this.runnableCode);
                            } else {
                                this.runHeadlessTask(taskConfig);
                            }

                        } catch (Exception e) {
                            Log.e("ForegroundService", "Failed to start task: " + e.getMessage());
                        }
                    }
                }
            } else if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_STOP)) {
                if (running > 0) {
                    running -= 1;

                    if (running == 0) {
                        stopSelf();
                        lastNotificationConfig = null;
                    }
                } else {
                    Log.d("ForegroundService", "Service is not running to stop.");
                    stopSelf();
                    lastNotificationConfig = null;
                }
                return START_NOT_STICKY;

            } else if (action.equals(Constants.ACTION_FOREGROUND_SERVICE_STOP_ALL)) {
                running = 0;
                mInstance = null;
                lastNotificationConfig = null;
                stopSelf();
                return START_NOT_STICKY;
            }
        } else {
            // Handle null action case here
            Log.e("ForegroundService", "Received null Intent or action, performing default initialization.");
            restartAppWithDelay();
        }

        // service to restart automatically if it's killed
        return START_STICKY;
    }

    private void restartAppWithDelay() {
        Intent intent = new Intent(getApplicationContext(), RestartReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            Log.d("SuperLog", "Restarting app in 5 seconds");
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 15000, // 5 seconds delay
                    pendingIntent);
        }
    }


    public void runHeadlessTask(Bundle bundle) {
        final Intent service = new Intent(getApplicationContext(), ForegroundServiceTask.class);
        service.putExtras(bundle);

        int delay = (int) bundle.getDouble("delay");

        if (delay <= 0) {
            getApplicationContext().startService(service);
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (running <= 0) {
                        return;
                    }
                    try {
                        getApplicationContext().startService(service);
                    } catch (Exception e) {
                        Log.e("ForegroundService", "Failed to start delayed headless task: " + e.getMessage());
                    }
                }
            }, delay);
        }
    }
}
