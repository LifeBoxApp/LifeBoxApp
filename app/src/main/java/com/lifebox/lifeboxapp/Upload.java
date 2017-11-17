package com.lifebox.lifeboxapp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.cloudrail.si.CloudRail;
import com.cloudrail.si.exceptions.ParseException;
import com.cloudrail.si.interfaces.CloudStorage;
import com.cloudrail.si.services.Box;
import com.cloudrail.si.services.Dropbox;
import com.cloudrail.si.services.OneDrive;

import static com.lifebox.lifeboxapp.LifeBoxAppMain.REDRAW_MAIN;
import static com.lifebox.lifeboxapp.LifeBoxAppMain.UPLOAD_STOP;

class Upload extends AsyncTask<Void, Long, Boolean> {
    private final File fileDirectory;
    private final Context context;
    private int fileCount;
    private String key = null;
    private String folder;
    private String tokenName;
    private CloudStorage service;


    public Upload(Context context, String directory, String encryptionKey, Integer cloud) {
        this.context = context.getApplicationContext();

        CloudRail.setAppKey("XXXXXX");
        Dropbox dropbox = new Dropbox(this.context, "XXXXXX", "XXXXXX");
        OneDrive onedrive = new OneDrive(this.context, "XXXXXX", "XXXXXX");
        Box box = new Box(this.context, "XXXXXX", "XXXXXX");

        switch(cloud) {
            case 1:
                service = dropbox;
                folder = "";
                tokenName = "cloudrailDropbox";
                break;
            case 2:
                service = onedrive;
                folder = "/LifeBoxApp";
                tokenName = "cloudrailOnedrive";
                break;
            case 3:
                service = box;
                folder = "/LifeBoxApp";
                tokenName = "cloudrailBox";
                break;
        }

        key = encryptionKey;
        fileDirectory = new File(directory);
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        SharedPreferences settings = context.getSharedPreferences("LifeBoxAppPreferences", 0);
        SharedPreferences.Editor editor = settings.edit();

        try {
            String token = settings.getString(tokenName,"");
            if (token != "") {
                service.loadAsString(token);
            }

            String username = service.getUserName(); // Test the Cloud
            editor.putString(tokenName,service.saveAsString());
            editor.commit();
        } catch (ParseException e) {
            Log.e(tokenName, e.getMessage());
        }

        if (folder != "" && !service.exists(folder)) {
            service.createFolder(folder);
        }
        for (File file : fileDirectory.listFiles()) {
            if (".".equals(file.getName()) || "..".equals(file.getName()))
                continue;
            try {
                if (!file.getName().startsWith("incomplete"))  {
                    String fileName = file.getName();
                    FileInputStream inputStream = new FileInputStream(file);
                    if (key == null) {
                        service.upload(
                                folder+"/"+fileName,
                                inputStream,
                                inputStream.available(),
                                true
                        );
                    }
                    else {
                        fileName = fileName+".enc";
                        int fileLength = inputStream.available();
                        byte[] fileBytes = new byte[fileLength];
                        inputStream.read(fileBytes);
                        Crypto crypto = new Crypto(key);
                        byte[] encrypted = crypto.encrypt(fileBytes);
                        InputStream encryptedStream = new ByteArrayInputStream(encrypted);
                        service.upload(
                                folder+"/"+fileName,
                                encryptedStream,
                                encryptedStream.available(),
                                true
                        );
                    }
                    file.delete();
                    fileCount = fileCount + 1;
                    LocalBroadcastManager.getInstance(context).sendBroadcast(
                            new Intent(REDRAW_MAIN));
                }
            }
            catch (Exception e) {
                Log.e("Upload",e.getMessage());
                LocalBroadcastManager.getInstance(context).sendBroadcast(
                        new Intent(UPLOAD_STOP));
                break;
            }
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(
                new Intent(UPLOAD_STOP));
        return true;
    }
}


