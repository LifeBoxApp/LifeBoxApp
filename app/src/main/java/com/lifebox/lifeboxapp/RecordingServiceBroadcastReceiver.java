package com.lifebox.lifeboxapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RecordingServiceBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			context.startService(new Intent(context, RecordingService.class));
		}
	}
}
