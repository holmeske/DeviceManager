package com.yfve.t19c.projection.devicemanager;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class DeviceManagerApplication extends Application {
    private static final String TAG = "DeviceManagerApplication";
    public static DeviceManagerApplication INSTANCE;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() called");
        INSTANCE = this;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG, "attachBaseContext() called with: base = [" + base + "]");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "onTerminate() called");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory() called");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory() called with: level = [" + level + "]");
    }
}
