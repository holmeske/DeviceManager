package com.yfve.t19c.projection.devicemanager;

import static androidx.core.app.NotificationCompat.PRIORITY_MIN;
import static com.yfve.t19c.projection.devicemanager.BuildConfig.APPLICATION_ID;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.DeviceListManager;
import com.yfve.t19c.projection.devicelist.OnConnectListener;

import java.util.ArrayList;
import java.util.List;

public class DeviceManagerService extends Service {
    private static final String TAG = "DeviceManagerService";
    private static final List<Device> deviceList = new ArrayList<>();
    private final List<OnConnectListener> mOnConnectListeners = new ArrayList<>();
    private CarHelper mCarHelper;
    private UsbHostController mUsbHostController;
    private DeviceListController mDeviceListController;
    private AppController mAppController;
    private final IBinder binder = new DeviceListManager.Stub() {

        @Override
        public void registerListener(OnConnectListener listener) {
            Log.d(TAG, "registerListener() called");
            mOnConnectListeners.add(listener);
        }

        @Override
        public void unregisterListener(OnConnectListener listener) {
            Log.d(TAG, "unregisterListener() called");
            mOnConnectListeners.remove(listener);
        }

        @Override
        public void projectionScreen(int connectType, String serialNumber, String btMac) {
            Log.d(TAG, "projectionScreen() called with: connectType = [" + connectType + "], serialNumber = [" + serialNumber + "], btMac = [" + btMac + "]");
            if (mAppController != null) {
                mAppController.switchSession(connectType, serialNumber, btMac);
            }
        }

        @Override
        public List<Device> getList() {
            Log.d(TAG, "getList() called" + " size = " + deviceList.size());
            return deviceList;
        }

    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind() called");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called");
        return true;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate() called");
        startForeground();

        mCarHelper = new CarHelper(this);

        UsbHelper mUsbHelper = new UsbHelper();

        if (mDeviceListController == null) {
            mDeviceListController = new DeviceListController(this, mUsbHelper);
        }

        //Log.i(TAG, "DeviceManagerService Thread == " + Thread.currentThread().getId());

        mAppController = new AppController(this, mDeviceListController, mCarHelper);
        mAppController.setOnConnectListener(mOnConnectListeners);
        mAppController.setDeviceList(deviceList);

        mUsbHostController = new UsbHostController(this, mAppController, mUsbHelper, mOnConnectListeners);
        mUsbHostController.setCarHelper(mCarHelper);
        mUsbHostController.setDeviceList(deviceList);

        mAppController.setUsbHostController(mUsbHostController);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() called");



        return START_STICKY;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "onRebind() called");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        mCarHelper.release();
        mUsbHostController.unRegisterReceiver();
    }

    private void startForeground() {
        String channelId;
        channelId = createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        Notification notification = builder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(1, notification);
    }

    private String createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(APPLICATION_ID, "Device Manager", NotificationManager.IMPORTANCE_NONE);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(channel);
        return APPLICATION_ID;
    }
}
