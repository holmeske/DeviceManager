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
import android.hardware.usb.UsbDevice;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.DeviceListManager;
import com.yfve.t19c.projection.devicelist.OnConnectListener;

import java.util.ArrayList;
import java.util.List;

public class DeviceManagerService extends Service {
    public static final List<Device> historyDeviceList = new ArrayList<>();
    private static final String TAG = "DeviceManagerService";
    private static final List<Device> aliveDeviceList = new ArrayList<>();
    private final List<OnConnectListener> mOnConnectListeners = new ArrayList<>();
    private int retryCount;
    private CarHelper mCarHelper;
    private UsbHostController mUsbHostController;
    private AppController mAppController;
    private Context mContext;
    private final IBinder binder = new DeviceListManager.Stub() {

        @Override
        public void registerListener(OnConnectListener listener) {
            Log.d(TAG, "registerListener() called, " + mOnConnectListeners.size());
            mOnConnectListeners.add(listener);
        }

        @Override
        public void unregisterListener(OnConnectListener listener) {
            Log.d(TAG, "unregisterListener() called, " + mOnConnectListeners.size());
            mOnConnectListeners.remove(listener);
        }

        @Override
        public void startSession(String serial, String mac, int connectType) {
            Log.d(TAG, "startSession() called with: serial = [" + serial + "], mac = [" + mac + "], connectType = [" + connectType + "]");
            if (mAppController != null) {
                if (connectType > 4 || connectType < 1) {
                    retryCount = 3;
                    mAppController.switchSession(serial, mac);
                } else {
                    mAppController.switchSession(connectType, serial, mac);
                }
            }
        }

        @Override
        public List<Device> getAliveDevices() {
            Log.d(TAG, "getAliveDevices() called" + " size = " + aliveDeviceList.size());
            return aliveDeviceList;
        }

        @Override
        public List<Device> getHistoryDevices() {
            Log.d(TAG, "getHistoryDevices() called" + " size = " + historyDeviceList.size());
            return historyDeviceList;
        }

        @Override
        public void onBluetoothPairResult(String mac, int result) {
            Log.d(TAG, "onBluetoothPairResult() called with: mac = [" + mac + "], result = [" + result + "]");
            if (TextUtils.isEmpty(mac)) return;
            //0:success  -1:scan not  -2:connect failed(retry three)
            for (OnConnectListener l : mOnConnectListeners) {
                try {
                    if (result == 0) {
                        retryCount = 0;
                    } else if (result == -1) {
                        Log.d(TAG, "onNotification -1");
                        l.onNotification(-1, "", "", mac, 0);
                        UsbDevice device = USBKt.queryUsbDevice(mContext, mAppController.switchingPhone.getSerial());
                        if (device != null) {
                            mUsbHostController.attach(device);
                        } else {
                            Log.d(TAG, "no find attached usb device");
                        }
                    } else if (result == -2) {
                        if (retryCount > 0) {
                            retryCount--;
                            Log.d(TAG, "onRequestBluetoothPair " + mac);
                            l.onRequestBluetoothPair(mac);
                        } else {
                            Log.d(TAG, "onNotification -2");
                            l.onNotification(-2, "", "", mac, 0);
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
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
        mContext = this;
        startForeground();

        mCarHelper = new CarHelper(this);

        new UsbHelper();

        mAppController = new AppController(this, mCarHelper);
        mAppController.setOnConnectListener(mOnConnectListeners);
        mAppController.setDeviceList(aliveDeviceList);
        mAppController.setHistoryDeviceList(historyDeviceList);

        mUsbHostController = new UsbHostController(this, mAppController, mOnConnectListeners);
        mUsbHostController.setCarHelper(mCarHelper);
        mUsbHostController.setDeviceList(aliveDeviceList);

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
        mAppController.release();
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
