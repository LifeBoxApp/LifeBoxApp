package com.lifebox.lifeboxapp;

import java.io.File;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session.AccessType;
import com.dropbox.client2.session.TokenPair;

public class LifeBoxAppMain extends Activity {

	private class DataUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(RecordingService.REDRAW_MAIN)) {
				redraw_display();
			}
		}
	}

	private DataUpdateReceiver dataUpdateReceiver;

	final static private String APP_KEY = "INSERT_APP_KEY";
	final static private String APP_SECRET = "INSERT_APP_KEY";
	final static private AccessType ACCESS_TYPE = AccessType.APP_FOLDER;
	final static private String ACCOUNT_PREFS_NAME = "prefs";
	final static private String ACCESS_KEY_NAME = "ACCESS_KEY";
	final static private String ACCESS_SECRET_NAME = "ACCESS_SECRET";

	private DropboxAPI<AndroidAuthSession> mDBApi;

	private boolean mLoggedIn = false;

	private Context context = this;

	private String _pathFolder = "";
	protected ConnectivityManager connManager;
	protected NetworkInfo mWifi;

	//private RecordingServiceBroadcastReceiver intentReceiver = new RecordingServiceBroadcastReceiver();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		mDBApi = new DropboxAPI<AndroidAuthSession>(buildSession());
		_pathFolder = getExternalFilesDir(null).toString();
		connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		checkAppKeySetup();

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

		connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		checkAppKeySetup();

		Button mSubmit = (Button) findViewById(R.id.auth_button);
		Button mUpload = (Button) findViewById(R.id.upload);
		Button mSettings = (Button) findViewById(R.id.settings);
		Button mAbout = (Button) findViewById(R.id.about);

		mSubmit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mLoggedIn) {
					logOut();
				} else {
					mDBApi.getSession()
							.startAuthentication(LifeBoxAppMain.this);
				}
			}
		});

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
				String url = "https://clintidau.github.io/LifeBoxApp/";
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse(url));
				startActivity(i);
			}
		});

		mLoggedIn = mDBApi.getSession().isLinked();

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

		AndroidAuthSession session = mDBApi.getSession();

		if (session.authenticationSuccessful()) {
			try {
				session.finishAuthentication();
				TokenPair tokens = session.getAccessTokenPair();
				storeKeys(tokens.key, tokens.secret);
				mLoggedIn = true;
				redraw_display();
			}
            catch (Exception e) {e.printStackTrace();}
		}

		if (!isMyServiceRunning()) {
			try {
				startService(new Intent(LifeBoxAppMain.this,
						RecordingService.class));
			}
            catch (Exception e) {e.printStackTrace();}
		}

		if (dataUpdateReceiver == null)
			dataUpdateReceiver = new DataUpdateReceiver();
		IntentFilter intentFilter = new IntentFilter(RecordingService.REDRAW_MAIN);
		LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, intentFilter);
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

	@Override
	protected void onStop() {
		/*try {
			unregisterReceiver(intentReceiver);
		}
        catch (Exception e) {e.printStackTrace();}
		*/
		super.onStop();
	}

	private void logOut() {
		mDBApi.getSession().unlink();
		clearKeys();
		mLoggedIn = false;
		redraw_display();
	}

	private void redraw_display() {
		try {
			Button mSubmit = (Button) findViewById(R.id.auth_button);
			Button mUpload = (Button) findViewById(R.id.upload);
			final Button mRecord = (Button) findViewById(R.id.record_button);
			final Button mStop = (Button) findViewById(R.id.stop_button);
			final Button mAutomatic = (Button) findViewById(R.id.automatic_button);

			if (mLoggedIn)
				mSubmit.setVisibility(android.view.View.GONE);
			else
				mSubmit.setVisibility(android.view.View.VISIBLE);

			if (onlyWhenScreenOff()) {
				mRecord.setVisibility(android.view.View.GONE);
				mStop.setVisibility(android.view.View.GONE);
				mAutomatic.setVisibility(android.view.View.VISIBLE);
			}
            else {
				mAutomatic.setVisibility(android.view.View.GONE);
				if (recordingStatus()) {
					mRecord.setVisibility(android.view.View.GONE);
					mStop.setVisibility(android.view.View.VISIBLE);
				} else {
					mRecord.setVisibility(android.view.View.VISIBLE);
					mStop.setVisibility(android.view.View.GONE);
				}
				mRecord.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						mRecord.setEnabled(false);
						mStop.setEnabled(false);

						LocalBroadcastManager.getInstance(context).sendBroadcast(
                            new Intent(RecordingService.RECORDING_START));

						Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							public void run() {
								mRecord.setEnabled(true);
								mStop.setEnabled(true);
								redraw_display();
							}
						}, 1000);
					}
				});
				mStop.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						mRecord.setEnabled(false);
						mStop.setEnabled(false);

						LocalBroadcastManager.getInstance(context).sendBroadcast(
                            new Intent(RecordingService.RECORDING_STOP));

						Handler handler = new Handler();
						handler.postDelayed(new Runnable() {
							public void run() {
								mRecord.setEnabled(true);
								mStop.setEnabled(true);
								redraw_display();
							}
						}, 1000);
					}
				});
			}

			mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			Button mWifiStatus = (Button)findViewById(R.id.wifi_button);
			if (mWifi.isConnected()) {
				mWifiStatus.setText("WiFi on");
				mWifiStatus.setBackgroundDrawable(getResources().getDrawable(R.drawable.wifi_on));
				}
			else
            {
			    mWifiStatus.setText("WiFi off");
			    mWifiStatus.setBackgroundDrawable(getResources().getDrawable(R.drawable.wifi_off));
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
                catch (Exception e) {e.printStackTrace();}
			}

			if (fileBytes == 0) {
				mUpload.setText("No Files");
				mUpload.setEnabled(false);
			}
            else {
				if (fileBytes < 1024 * 1024)
					mUpload.setText("Upload " + (fileBytes / 1024) + "KB");
				else
					mUpload.setText("Upload " + (fileBytes / 1024 / 1024) + "MB");
				mUpload.setEnabled(true);
			}

			Button mStatus = (Button) findViewById(R.id.textView3);
			if (onlyWhenScreenOff())
				mStatus.setText("Recording when screen is off");
			else if (recordingStatus())
				mStatus.setText("Recording Now");
			else
				mStatus.setText("Not Recording");
		}
        catch (Exception e) {e.printStackTrace();}
	}

	private void checkAppKeySetup() {
		if (APP_KEY.startsWith("CHANGE") || APP_SECRET.startsWith("CHANGE")) {
			finish();
			return;
		}

		// Check if the app has set up its manifest properly.
		Intent testIntent = new Intent(Intent.ACTION_VIEW);
		String scheme = "db-" + APP_KEY;
		String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
		testIntent.setData(Uri.parse(uri));
		PackageManager pm = getPackageManager();
		if (0 == pm.queryIntentActivities(testIntent, 0).size())
			finish();
	}

	private String[] getKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		String key = prefs.getString(ACCESS_KEY_NAME, null);
		String secret = prefs.getString(ACCESS_SECRET_NAME, null);
		if (key != null && secret != null) {
			String[] ret = new String[2];
			ret[0] = key;
			ret[1] = secret;
			return ret;
		} else {
			return null;
		}
	}

	private void storeKeys(String key, String secret) {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.putString(ACCESS_KEY_NAME, key);
		edit.putString(ACCESS_SECRET_NAME, secret);
		edit.commit();
	}

	private void clearKeys() {
		SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
		Editor edit = prefs.edit();
		edit.clear();
		edit.commit();
	}

	private AndroidAuthSession buildSession() {
		AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
		AndroidAuthSession session;

		String[] stored = getKeys();
		if (stored != null) {
			AccessTokenPair accessToken = new AccessTokenPair(stored[0],stored[1]);
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE,accessToken);
		}
        else
			session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);

		return session;
	}

	protected void uploaderStart() {
		String key = null;
		try {
			SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
			key = settings.getString("key",null);
			Upload upload = new Upload(this, mDBApi, _pathFolder, key);
			upload.execute();
		}
        catch (Exception e) {e.printStackTrace();}

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
