package com.lifebox.lifeboxapp;

import java.io.File;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

public class LifeBoxAppMain extends Activity {
	public static final String REDRAW_MAIN = "REDRAW_MAIN";
	public static final String RECORDING_STOP = "RECORDING_STOP";
	public static final String RECORDING_START = "RECORDING_START";
	public static final String SCREENOFF_ON = "SCREENOFF_ON";
	public static final String SCREENOFF_OFF = "SCREENOFF_OFF";
	public static final String UPLOAD_STOP = "UPLOAD_STOP";

	private boolean uploading = false;

	private class DataUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(UPLOAD_STOP)) {
				uploading = false;
			}
			redraw_display();
		}
	}

	private DataUpdateReceiver dataUpdateReceiver;

	final static private String ACCOUNT_PREFS_NAME = "prefs";

	private final Context context = this;

	private String _pathFolder = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		final SharedPreferences settings = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);

		if (!settings.getBoolean("UsageAgreementAccepted", false)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Before using LifeBoxApp you must agree to comply with your local laws regarding the recording of other people. In some places it is illegal to record other people without their consent.")
					.setCancelable(false)
					.setPositiveButton("Agree", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							settings.edit().putBoolean("UsageAgreementAccepted",true).commit();
						}
					})
					.setNegativeButton("Disagree", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							System.exit(0);
						}
					});
			AlertDialog alert = builder.create();
			alert.show();
		}

		_pathFolder = getExternalFilesDir(null).toString();

		File dir = new File(_pathFolder);
		for (File child : dir.listFiles()) {
			if (".".equals(child.getName()) || "..".equals(child.getName())) {
				continue;
			}
			if (child.getName().startsWith("incomplete")) {
				child.delete();
			}
		}

		Button mUpload = (Button) findViewById(R.id.upload);
		Button mSettings = (Button) findViewById(R.id.settings);
		Button mAbout = (Button) findViewById(R.id.about);

		mUpload.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				uploaderStart();
			}
		});

		mSettings.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent myIntent = new Intent(LifeBoxAppMain.this,
						Settings.class);
				LifeBoxAppMain.this.startActivity(myIntent);
			}
		});

		mAbout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String url = "http://lifeboxapp.com";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
			}
		});

		redraw_display();
	}

	private boolean isMyServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (RecordingService.class.getName().equals(
					service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!isMyServiceRunning()) {
			try {
				startService(new Intent(LifeBoxAppMain.this,
						RecordingService.class));
			}
			catch (Exception e) {
				Log.e("Main",e.getMessage());
			}
		}

		if (dataUpdateReceiver == null)
			dataUpdateReceiver = new DataUpdateReceiver();
		LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(REDRAW_MAIN));
		LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(UPLOAD_STOP));
		redraw_display();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (dataUpdateReceiver != null)
			LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private void redraw_display() {
		try {
			Button mUpload = (Button) findViewById(R.id.upload);
			final Button mRecord = (Button) findViewById(R.id.record_button);

			if (onlyWhenScreenOff()) {
				mRecord.setBackground(getResources().getDrawable(R.drawable.recording_on));
				mRecord.setText("Auto");
			}
			else {
				if (recordingStatus()) {

					mRecord.setBackground(getResources().getDrawable(R.drawable.recording_on));
					mRecord.setText("Stop");
					mRecord.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							mRecord.setEnabled(false);

							LocalBroadcastManager.getInstance(context).sendBroadcast(
									new Intent(RECORDING_STOP));

							Handler handler = new Handler();
							handler.postDelayed(new Runnable() {
								public void run() {
									mRecord.setEnabled(true);
									redraw_display();
								}
							}, 1000);
						}
					});
				} else {
					mRecord.setBackground(getResources().getDrawable(R.drawable.recording_off));
					mRecord.setText("Start");
					mRecord.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							mRecord.setEnabled(false);

							LocalBroadcastManager.getInstance(context).sendBroadcast(
									new Intent(RECORDING_START));

							Handler handler = new Handler();
							handler.postDelayed(new Runnable() {
								public void run() {
									mRecord.setEnabled(true);
									redraw_display();
								}
							}, 1000);
						}
					});
				}
			}

			long fileBytes = 0;
			File dir = new File(_pathFolder);
			for (File child : dir.listFiles()) {
				if (".".equals(child.getName()) || "..".equals(child.getName()))
					continue;
				try {
					if (!child.getName().startsWith("incomplete"))
						fileBytes = fileBytes + child.length();
				}
				catch (Exception e) {
					Log.e("Main",e.getMessage());
				}
			}

			if (fileBytes == 0) {
				mUpload.setText("No Files");
				mUpload.setEnabled(false);
				mUpload.setBackground(getResources().getDrawable(R.drawable.upload));
			}
			else {
				if (uploading) {
					if (fileBytes < 1024 * 1024)
						mUpload.setText("Uploading\n" + (fileBytes / 1024) + "KB");
					else
						mUpload.setText("Uploading\n" + (fileBytes / 1024 / 1024) + "MB");
					mUpload.setBackground(getResources().getDrawable(R.drawable.uploading));
					mUpload.setEnabled(false);
				}
				else {
					if (fileBytes < 1024 * 1024)
						mUpload.setText("Upload\n" + (fileBytes / 1024) + "KB");
					else
						mUpload.setText("Upload\n" + (fileBytes / 1024 / 1024) + "MB");
					mUpload.setBackground(getResources().getDrawable(R.drawable.upload));
					mUpload.setEnabled(true);
				}
			}
		}
		catch (Exception e) {
			Log.e("Main",e.getMessage());
		}
	}

	private void uploaderStart() {
		String key = null;
		try {
			uploading = true;
			SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
			key = settings.getString("key",null);
			Upload upload = new Upload(this, _pathFolder, key, settings.getInt("cloud",1));
			upload.execute();
		}
		catch (Exception e) {
			Log.e("Main",e.getMessage());
		}

		redraw_display();
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
