/*
This all needs to be replaced because the Dropbox API v1 is
deprecated and will stop working in June 2017!
https://blogs.dropbox.com/developers/2016/06/api-v1-deprecated/
*/

package com.lifebox.lifeboxapp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.UploadRequest;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxLocalStorageFullException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

public class Upload extends AsyncTask<Void, Long, Boolean> {
	private DropboxAPI<?> dropboxApi;
	private File fileDirectory;
	private UploadRequest uploadRequest;
	private Context context;
	private final ProgressDialog progressDialog;
	private int fileCount;
	private String fileName;
	private String errorMessage;
    private String key = null;

	public Upload(Context context, DropboxAPI<?> api, String directory, String encryptionKey) {
		this.context = context.getApplicationContext();
        key = encryptionKey;
		dropboxApi = api;
		fileDirectory = new File(directory);
		progressDialog = new ProgressDialog(context);
		progressDialog.setMax(0);
		for (File child : fileDirectory.listFiles()) {
			if (".".equals(child.getName()) || "..".equals(child.getName()))
				continue;
			try {
				if (!child.getName().startsWith("incomplete")) {
					progressDialog.setMax(progressDialog.getMax() + 1);
				}
			}
            catch (Exception e) {e.printStackTrace();}
		}
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		fileName = "...";
		progressDialog.setMessage("Uploading " + fileName);
		fileCount = 0;
		progressDialog.setProgress(fileCount);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
		progressDialog.show();
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		for (File file : fileDirectory.listFiles()) {
			if (".".equals(file.getName()) || "..".equals(file.getName()))
				continue;
			try {
				if (!file.getName().startsWith("incomplete"))  {
                    fileName = file.getName();
					FileInputStream inputStream = new FileInputStream(file);
					try {
						if (key != null) {
                            fileName = fileName+".enc";
							int fileLength = inputStream.available();
							byte[] fileBytes = new byte[fileLength];
							inputStream.read(fileBytes);
							Crypto crypto = new Crypto(key);
							byte[] encrypted = crypto.encrypt(fileBytes);
							InputStream encryptedStream = new ByteArrayInputStream(encrypted);
							System.out.println("Uploading "+fileName);
							uploadRequest = dropboxApi.putFileOverwriteRequest(fileName, encryptedStream,
                                encryptedStream.available(), new ProgressListener() {
                                    @Override
                                    public long progressInterval() {
                                        return 500;
                                    }

                                    @Override
                                    public void onProgress(long bytes, long total) {
                                        publishProgress(bytes);
                                    }
                                });
						}
						else {
							uploadRequest = dropboxApi.putFileOverwriteRequest(fileName, inputStream,
                                file.length(), new ProgressListener() {
                                    @Override
                                    public long progressInterval() {
                                        return 500;
                                    }

                                    @Override
                                    public void onProgress(long bytes,
                                                           long total) {
                                        publishProgress(bytes);
                                    }
                                });
						}
						if (uploadRequest != null) {
							uploadRequest.upload();
							file.delete();
							fileCount = fileCount + 1;
						}

					}
                    catch (DropboxUnlinkedException e) {
						errorMessage = "Please sign in to Dropbox first.";
						return false;
					}
                    catch (DropboxFileSizeException e) {
						errorMessage = "This file is too big to upload";
						return false;
					}
                    catch (DropboxPartialFileException e) {
						errorMessage = "Upload canceled";
						return false;
					}
                    catch (DropboxServerException e) {
						if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                            errorMessage = "401 Unauthorized";
                            return false;
						}
                        else if (e.error == DropboxServerException._403_FORBIDDEN) {
                            errorMessage = "403 Forbidden";
                            return false;
						}
                        else if (e.error == DropboxServerException._404_NOT_FOUND) {
                            errorMessage = "404 Not Found";
                            return false;
						}
                        else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                            errorMessage = "Your Dropbox is full";
                            return false;
						}
                        else {
                            errorMessage = e.body.userError;
                            if (errorMessage == null)
                                errorMessage = e.body.error;
                            return false;
                        }
					}
                    catch (DropboxIOException e) {
						errorMessage = "Network error. Try again.";
						return false;
					}
                    catch (DropboxParseException e) {
						errorMessage = "Dropbox error. Try again.";
						return false;
					}
                    catch (DropboxLocalStorageFullException e) {
						errorMessage = "Your Dropbox is full.";
						return false;
					}
                    catch (DropboxException e) {
						errorMessage = "Unknown error. Try again.";
						return false;
					}
				}
			}
            catch (Exception e) {
				errorMessage = e.getMessage();
				return false;
			}
		}
		return true;
	}

	@Override
	protected void onProgressUpdate(Long... progress) {
		progressDialog.setMessage("Uploading " + fileName);
		progressDialog.setProgress(fileCount);
	}

	@Override
	protected void onPostExecute(Boolean result) {
		progressDialog.dismiss();
		LocalBroadcastManager.getInstance(context).sendBroadcast(
				new Intent(RecordingService.REDRAW_MAIN));
		if (!result) {
			showToast(errorMessage);
		}
	}

	private void showToast(String msg) {
		Toast error = Toast.makeText(context, msg, Toast.LENGTH_LONG);
		error.show();
	}
}


