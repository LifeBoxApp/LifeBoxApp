package com.lifebox.lifeboxapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;

import static com.lifebox.lifeboxapp.LifeBoxAppMain.SCREENOFF_OFF;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.SCREENOFF_ON;

public class Settings extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.settings);

        final Context context = this;
        final CheckBox checkBoxOnlyWhenScreenOff = (CheckBox) findViewById(R.id.checkBoxOnlyWhenScreenOff);
        final CheckBox checkBoxEncryption = (CheckBox) findViewById(R.id.checkBoxEncryption);
        final RadioButton radioButtonDropbox = (RadioButton) findViewById(R.id.radioButtonDropbox);
        final RadioButton radioButtonOnedrive = (RadioButton) findViewById(R.id.radioButtonOnedrive);
        final RadioButton radioButtonBox = (RadioButton) findViewById(R.id.radioButtonBox);
        final RadioButton radioButtonGoogleDrive = (RadioButton) findViewById(R.id.radioButtonGoogleDrive);

        final SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
        final SharedPreferences.Editor editor = settings.edit();

        Integer cloud = settings.getInt("cloud",1);
        switch(cloud) {
            case 1: radioButtonDropbox.setChecked(true); break;
            case 2: radioButtonOnedrive.setChecked(true); break;
            case 3: radioButtonBox.setChecked(true); break;
            case 4: radioButtonGoogleDrive.setChecked(true); break;
        }

        radioButtonDropbox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                editor.putInt("cloud", 1);
                editor.commit();
            }
        });

        radioButtonOnedrive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                editor.putInt("cloud", 2);
                editor.commit();
            }
        });

        radioButtonBox.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                editor.putInt("cloud", 3);
                editor.commit();
            }
        });

        radioButtonGoogleDrive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                editor.putInt("cloud", 4);
                editor.commit();
            }
        });

        if (settings.getBoolean("onlyWhenScreenOff",false))
            checkBoxOnlyWhenScreenOff.setChecked(true);
        else
            checkBoxOnlyWhenScreenOff.setChecked(false);

        String key = settings.getString("key",null);
        if (key == null)
            checkBoxEncryption.setChecked(false);
        else
            checkBoxEncryption.setChecked(true);

        checkBoxOnlyWhenScreenOff.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor editor = settings.edit();

                if (settings.getBoolean("onlyWhenScreenOff",false)) {
                    editor.putBoolean("onlyWhenScreenOff", false);
                    editor.commit();
                    LocalBroadcastManager.getInstance(context).sendBroadcast(
                            new Intent(SCREENOFF_OFF));
                }
                else {
                    editor.putBoolean("onlyWhenScreenOff", true);
                    editor.commit();
                    LocalBroadcastManager.getInstance(context).sendBroadcast(
                            new Intent(SCREENOFF_ON));
                }
            }
        });

        checkBoxEncryption.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (settings.getString("key",null) == null) {
                    LayoutInflater li = LayoutInflater.from(context);
                    View promptsView = li.inflate(R.layout.password, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    alertDialogBuilder.setView(promptsView);

                    final EditText userInput = (EditText) promptsView.findViewById(
                            R.id.editTextDialogUserInput);

                    alertDialogBuilder.setCancelable(false)
                            .setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,int id) {
                                            String password = userInput.getText().toString();
                                            if (password.length()!=0) {
                                                setPassword(password, Installation.id(context));
                                                checkBoxEncryption.setChecked(true);
                                            }
                                            else
                                                checkBoxEncryption.setChecked(false);
                                        }
                                    })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,int id) {
                                            dialog.cancel();
                                            checkBoxEncryption.setChecked(false);
                                        }
                                    });

                    AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
                else {
                    deletePassword();
                    checkBoxEncryption.setChecked(false);
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
        catch (Exception e) {
            Log.e("Settings",e.getMessage());
        }
    }

    private void deletePassword() {
        try {
            SharedPreferences settings = getSharedPreferences("LifeBoxAppPreferences", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.remove("key");
            editor.commit();
        }
        catch (Exception e) {
            Log.e("Settings",e.getMessage());
        }
    }
}
