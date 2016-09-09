package com.lifebox.lifeboxapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;

public class Settings extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.settings);

		final Context context = this;
		final CheckBox ScreenOffCheckBox = (CheckBox) findViewById(R.id.checkBoxOnlyWhenScreenOff);
        final CheckBox EncryptionCheckBox = (CheckBox) findViewById(R.id.checkBoxEncryption);
		final SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);

		if (settings.getBoolean("onlyWhenScreenOff",false))
			ScreenOffCheckBox.setChecked(true);
		else
			ScreenOffCheckBox.setChecked(false);

        String key = settings.getString("key",null);
        if (key == null)
            EncryptionCheckBox.setChecked(false);
        else
            EncryptionCheckBox.setChecked(true);

		ScreenOffCheckBox.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				SharedPreferences.Editor editor = settings.edit();
				
				if (settings.getBoolean("onlyWhenScreenOff",false)) {
					editor.putBoolean("onlyWhenScreenOff", false);
					editor.commit();
					LocalBroadcastManager.getInstance(context).sendBroadcast(
							new Intent(RecordingService.SCREENOFF_OFF));
				}
				else {
					editor.putBoolean("onlyWhenScreenOff", true);
					editor.commit();
					LocalBroadcastManager.getInstance(context).sendBroadcast(
							new Intent(RecordingService.SCREENOFF_ON));
				}
			}
		});

        EncryptionCheckBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor editor = settings.edit();

                if (settings.getString("key",null) == null) {
                    LayoutInflater li = LayoutInflater.from(context);
                    View promptsView = li.inflate(R.layout.password, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    alertDialogBuilder.setView(promptsView);

                    final EditText userInput = (EditText) promptsView.findViewById(
                            R.id.editTextDialogUserInput);

                    // set dialog message
                    alertDialogBuilder.setCancelable(false)
                        .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    String password = userInput.getText().toString();
                                    if (!password.isEmpty()) {
                                        setPassword(password, Installation.id(context));
                                        EncryptionCheckBox.setChecked(true);
                                    }
                                    else
                                        EncryptionCheckBox.setChecked(false);
                                }
                            })
                        .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                    EncryptionCheckBox.setChecked(false);
                                }
                            });

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
                else {
                    deletePassword();
                    EncryptionCheckBox.setChecked(false);
                }
            }
        });
	}

    private void setPassword(String password, String installationId) {
        try {
            String key = Crypto.makeKey(password,installationId);
            SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("key",key);
            editor.commit();
        }
        catch (Exception e) {e.printStackTrace();}
    }

    private void deletePassword() {
        try {
            SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.remove("key");
            editor.commit();
        }
        catch (Exception e) {e.printStackTrace();}
    }
}
