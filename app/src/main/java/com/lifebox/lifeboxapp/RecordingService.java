package com.lifebox.lifeboxapp;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import static com.lifebox.lifeboxapp.LifeBoxAppMain.RECORDING_START;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.RECORDING_STOP;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.REDRAW_MAIN;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.SCREENOFF_OFF;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.SCREENOFF_ON;

public class RecordingService extends Service {

    private DataUpdateReceiver dataUpdateReceiver;
    private GlobalReceiver globalReceiver;
    private String folderPath = "";
    private static MediaRecorder recorder = null;
    private String filename;
    private static String InstallationId = "";
    private boolean notificationIconFlag = false;

    private static final Object lock = new Object();

    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(RECORDING_START)) {
                recordingStatus(true);
                loop();
            }
            if (intent.getAction().equals(RECORDING_STOP)) {
                recordingStatus(false);
                stop();
            }
            if (intent.getAction().equals(SCREENOFF_ON))
                notificationIconBoolean(true);
            if (intent.getAction().equals(SCREENOFF_OFF))
                notificationIconBoolean(false);
        }
    }

    private class GlobalReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (onlyWhenScreenOff()) {
                    recordingStatus(false);
                    stop();
                }
            }

            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (onlyWhenScreenOff()) {
                    recordingStatus(true);
                    loop();
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        recordingStatus(false);

        if (dataUpdateReceiver == null)
            dataUpdateReceiver = new DataUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter(RECORDING_START);
        intentFilter.addAction(RECORDING_STOP);
        intentFilter.addAction(SCREENOFF_ON);
        intentFilter.addAction(SCREENOFF_OFF);
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, intentFilter);

        if (globalReceiver == null)
            globalReceiver = new GlobalReceiver();
        IntentFilter intentFilter2 = new IntentFilter(Intent.ACTION_SCREEN_ON);
        intentFilter2.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(globalReceiver, intentFilter2);

        folderPath = getExternalFilesDir(null).toString();

        if (onlyWhenScreenOff())
            notificationIconBoolean(true);
    }

    @Override
    public void onDestroy() {
        if (dataUpdateReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        recordingStatus(false);
        stop();
        stopForeground(true);
    }

    private void loop() {
        if (recordingStatus())
        {
            start();
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(REDRAW_MAIN)
            );
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    stop();
                    loop();
                }
            }, 1800000); // Split files every 30 minutes
            //}, 60000); // Split files every 1 minute for debugging
        }
    }

    private void stop() {
        synchronized(lock) {
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.reset();
                    recorder.release();
                    recorder = null;
                }
                File f = new File(folderPath + "/" + "incomplete_" + filename);
                if (f.exists())
                    f.renameTo(new File(folderPath + "/" + filename));
            }
            catch (Exception e) {
                Log.e("RecordingService",e.getMessage());
                recorder = null;
            }
        }
    }

    private void start() {
        if (recordingStatus()) {
            synchronized(lock) {
                try {
                    recorder = new MediaRecorder();
                    //filename = getFileName() + ".3gp";
                    //recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                    //recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                    //recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                    filename = getFileName() + ".mp4";
                    recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                    recorder.setAudioChannels(1);
                    recorder.setOutputFile(folderPath + "/" + "incomplete_" + filename);

                    recorder.prepare();
                    recorder.start();
                }
                catch (Exception e) {
                    Log.e("RecordingService",e.getMessage());
                }
            }
        }
    }

    private String getFileName() {
        return InstallationId + "_" + getUtcTime();
    }

    private static String getUtcTime()
    {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private void recordingStatus(boolean value) {
        SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("recordingStatus", value);
        editor.commit();
        notificationIconBoolean(value);
    }

    private void notificationIconBoolean(boolean toggle)
    {
        if (toggle && !notificationIconFlag)
        {
            Context context = getApplicationContext();
            InstallationId = Installation.id(context);

            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setComponent(ComponentName.unflattenFromString(
                    "com.lifebox.lifeboxapp/com.lifebox.lifeboxapp.LifeBoxAppMain"));
            intent.addCategory("android.intent.category.LAUNCHER");

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle("LifeBoxApp")
                    .setContentText("LifeBoxApp");

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(LifeBoxAppMain.class);
            stackBuilder.addNextIntent(intent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            if (onlyWhenScreenOff())
                mBuilder.setContentTitle("LifeBoxApp")
                        .setSmallIcon(R.drawable.icon)
                        .setContentText("Waiting to record when screen is off");
            else if (recordingStatus()) {
                mBuilder.setContentTitle("LifeBoxApp")
                        .setSmallIcon(R.drawable.icon)
                        .setContentText("Recording");
            }
            else {
                mBuilder.setContentTitle("LifeBoxApp")
                        .setSmallIcon(R.drawable.icon)
                        .setContentText("Unknown status");
            }
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(1337, mBuilder.build());
            notificationIconFlag = true;
        }
        else if (!onlyWhenScreenOff())
        {
            // This removes the icon from the notification area and ends the 'foreground' status.
            stopForeground(true);
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(1337);
            notificationIconFlag = false;
        }
    }

    private boolean recordingStatus() {
        SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
        return settings.getBoolean("recordingStatus", false);
    }

    private boolean onlyWhenScreenOff() {
        SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
        return settings.getBoolean("onlyWhenScreenOff", false);
    }
}
