package com.lifebox.lifeboxapp;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

public class RecordingService extends Service {
	public static final String REDRAW_MAIN = "com.lifebox.lifeboxapp.RecordingService.REDRAW_MAIN";
	public static final String RECORDING_STOP = "com.lifebox.lifeboxapp.RecordingService.RECORDING_STOP";
	public static final String RECORDING_START = "com.lifebox.lifeboxapp.RecordingService.RECORDING_START";
	public static final String SCREENOFF_ON = "com.lifebox.lifeboxapp.RecordingService.SCREENOFF_ON";
	public static final String SCREENOFF_OFF = "com.lifebox.lifeboxapp.RecordingService.SCREENOFF_OFF";

    private DataUpdateReceiver dataUpdateReceiver;
    private GlobalReceiver globalReceiver;
    private String folderPath = "";
    private static MediaRecorder recorder = null;
    protected ConnectivityManager connManager;
    protected NetworkInfo mWifi;
    protected String filename;
	private static String InstallationId = "";
    private boolean notificationIconFlag = false;

	private static final Object lock = new Object();

	private class DataUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(RecordingService.RECORDING_START)) {
				recordingStatus(true);
				loop();
			}
			if (intent.getAction().equals(RecordingService.RECORDING_STOP)) {
				recordingStatus(false);
				stop();
			}
			if (intent.getAction().equals(RecordingService.SCREENOFF_ON))
				notificationIconBoolean(true);
			if (intent.getAction().equals(RecordingService.SCREENOFF_OFF))
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
		IntentFilter intentFilter = new IntentFilter(RecordingService.RECORDING_START);
		intentFilter.addAction(RecordingService.RECORDING_STOP);
		intentFilter.addAction(RecordingService.SCREENOFF_ON);
		intentFilter.addAction(RecordingService.SCREENOFF_OFF);
		LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, intentFilter);

		if (globalReceiver == null)
			globalReceiver = new GlobalReceiver();
		IntentFilter intentFilter2 = new IntentFilter(Intent.ACTION_SCREEN_ON);
		intentFilter2.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(globalReceiver, intentFilter2);

		folderPath = getExternalFilesDir(null).toString();

		connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		
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

	protected void loop() {
		if (recordingStatus())
		{
			start();
			LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(RecordingService.REDRAW_MAIN)
            );
			Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				public void run() {
					stop();
					loop();
				}
			}, 1800000); // Split files every 30 minutes
			//}, 180000); // Split files every 3 minutes for debugging
		}
	}

	private void stop() {
		System.out.println("Stop");
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
                e.printStackTrace();
                recorder = null;
            }
		}
	}
	
	private void start() {
		System.out.println("Start");
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
                catch (Exception e) {e.printStackTrace();}
			}
		}
	}

	protected String getFileName() {
		return InstallationId + "_" + getUtcTime();
	}

    public static String getUtcTime()
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
			// This puts the icon in the notification area and gives lifebox the
            // 'foreground' status so it cannot be killed by android to save memory.
			int icon = R.drawable.icon;
			CharSequence tickerText = "";
			long when = System.currentTimeMillis();
            Notification notification = new Notification(icon, tickerText, when);
			Context context = getApplicationContext();
			InstallationId = Installation.id(context);

			Intent intent = new Intent("android.intent.action.MAIN");
			intent.setComponent(ComponentName.unflattenFromString(
                    "com.lifebox.lifeboxapp/com.lifebox.lifeboxapp.LifeBoxAppMain"));
			intent.addCategory("android.intent.category.LAUNCHER");
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

			if (onlyWhenScreenOff())
				notification.setLatestEventInfo(context, "Lifebox",
                        "Waiting to record when screen is off", contentIntent);
			else if (recordingStatus())
				notification.setLatestEventInfo(context, "Lifebox",
                        "Recording", contentIntent);
			else
				notification.setLatestEventInfo(context, "Lifebox",
                        "Unknown status", contentIntent);

			startForeground(1337, notification);
			notificationIconFlag = true;
		}
		else if (!onlyWhenScreenOff())
		{
			// This removes the icon from the notification area and ends the 'foreground' status.
			stopForeground(true);
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
