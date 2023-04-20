package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;

import java.util.Objects;

public class DeviceManagerReceiver extends BroadcastReceiver {
    private static final String TAG = "DeviceManagerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            USBKt.usbDeviceList(context).values().forEach(d -> Log.d(TAG, "attached usb device ------ " + CommonUtilsKt.toJson(d)));
            if (DeviceManagerService.isStarted) {
                Log.d(TAG, "device manager service is started");
            } else {
                Log.d(TAG, "start device manager service");
                context.startForegroundService(new Intent(context, DeviceManagerService.class));
            }
        }
    }
}
