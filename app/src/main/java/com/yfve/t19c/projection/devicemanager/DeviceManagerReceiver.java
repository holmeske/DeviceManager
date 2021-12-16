package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Objects;

public class DeviceManagerReceiver extends BroadcastReceiver {
    private static final String TAG = "DeviceManagerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            context.startForegroundService(new Intent(context, DeviceManagerService.class));
        }
    }
}
