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

import com.android.internal.R;
import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.DeviceListManager;
import com.yfve.t19c.projection.devicelist.OnConnectListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DeviceManagerService extends Service {
    public static final List<Device> historyDeviceList = new ArrayList<>();
    public static final List<Device> aliveDeviceList = new ArrayList<>();
    private static final String TAG = "DeviceManagerService";
    public static boolean isStarted = false;
    private final String Data = "2023-01-04 10:25 base"; //auth   sop   base
    private final List<OnConnectListener> mOnConnectListeners = new ArrayList<>();
    private int retryCount;
    private UsbHostController mUsbHostController;
    private AppController mAppController;
    private Context mContext;
    private final IBinder binder = new DeviceListManager.Stub() {

        @Override
        public void registerListener(OnConnectListener listener) {
            Log.d(TAG, "registerListener() called");
            mOnConnectListeners.add(listener);
            Log.d(TAG, "OnConnectListener size = " + mOnConnectListeners.size());
        }

        @Override
        public void unregisterListener(OnConnectListener listener) {
            Log.d(TAG, "unregisterListener() called");
            mOnConnectListeners.remove(listener);
            Log.d(TAG, "OnConnectListener size = " + mOnConnectListeners.size());
        }

        @Override
        public void startSession(String serial, String mac, int connectType) {
            Log.d(TAG, "startSession() called with: serial = [" + serial + "], mac = [" + mac + "], connectType = [" + connectType + "]");
            if (mAppController != null) {
                if (connectType > 4 || connectType < 1) {
                    retryCount = 3;
                    mAppController.switchSession(serial, mac);//bluetooth connectType params only transport zero
                } else {
                    mAppController.switchSession(connectType, serial, mac);
                }
            }
        }

        @Override
        public List<Device> getAliveDevices() {
            Log.d(TAG, "getAliveDevices() called " + Data);
            return filteredAliveDeviceList();
        }

        @Override
        public List<Device> getHistoryDevices() {
            Log.d(TAG, "getHistoryDevices() called " + Data);
            historyDeviceList.forEach(d -> Log.d(TAG, "history   " + d));
            return historyDeviceList;
        }

        @Override
        public void onBluetoothPairResult(String mac, int result) {
            Log.d(TAG, "onBluetoothPairResult() called with: mac = [" + mac + "], result = [" + result + "]");
            if (TextUtils.isEmpty(mac)) return;
            //0:success  -1:scan not  -2:connect failed(retry three)
            for (OnConnectListener l : mOnConnectListeners) {
                if (l != null) {
                    try {
                        if (result == 0) {
                            retryCount = 0;
                        } else if (result == -1) {
                            Log.d(TAG, "onNotification -1 , not scanned");
                            mAppController.resetSwitchingSessionState();
                            l.onNotification(-1, "", "", mac, 0);
                            UsbDevice device = USBKt.queryUsbDevice(mContext, mAppController.switchingPhone.getSerial());
                            if (device != null) {
                                Log.d(TAG, "onBluetoothPairResult: UsbHostController.attach");
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
                                Log.d(TAG, "onNotification -2 , connection failed");
                                mAppController.resetSwitchingSessionState();
                                l.onNotification(-2, "", "", mac, 0);
                            }
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, e.toString());
                    }
                } else {
                    Log.d(TAG, "listener == null ");
                }
            }
        }

    };

    private static boolean anyMatch(String oldSerial, String newSerial, String oldMac, String newMac) {
        return (TextUtils.equals(oldSerial, newSerial) && !TextUtils.isEmpty(newSerial) && !TextUtils.equals(newSerial, "null"))
                || (TextUtils.equals(oldMac, newMac) && !TextUtils.isEmpty(newMac) && !TextUtils.equals(newMac, "null"));
    }

    public static List<Device> filteredAliveDeviceList() {
        List<Device> filteredDeviceList = new ArrayList<>();

        aliveDeviceList.forEach(item -> Log.d(TAG, "alive     " + item.toString()));
        aliveDeviceList.forEach(d -> {
            boolean repeat = filteredDeviceList.stream().anyMatch(it -> anyMatch(it.getSerial(), d.getSerial(), it.getMac(), d.getMac()));

            if (repeat) {
                if (d.getSerial() != null && d.getSerial().length() > 4 && d.getMac() != null && d.getMac().length() > 4) {
                    filteredDeviceList.stream().filter(old ->
                            anyMatch(old.getSerial(), d.getSerial(), old.getMac(), d.getMac())
                    ).collect(Collectors.toList()).forEach(i -> {
                        Log.d(TAG, "old : " + i);
                        Log.d(TAG, "new : " + d);

                        i.setType(d.getType());
                        i.setName(d.getName());
                        i.setSerial(d.getSerial());
                        i.setMac(d.getMac());
                        i.setUsbAA(d.isUsbAA());
                        i.setWirelessAA(d.isWirelessAA());
                        i.setUsbCP(d.isUsbCP());
                        i.setWirelessCP(d.isWirelessCP());
                        i.setAvailable(d.isAvailable());
                    });
                }
            } else {
                filteredDeviceList.add(new Device(d.getType(), d.getName(), d.getSerial(), d.getMac(), d.isUsbAA(), d.isWirelessAA(), d.isUsbCP(), d.isWirelessCP(), d.isAvailable()));
                Log.d(TAG, "add : " + d);
            }

        });
        filteredDeviceList.forEach(item -> Log.d(TAG, "filtered  " + item.toString()));
        return filteredDeviceList;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind() called " + Data);
        if (mContext == null) {
            mContext = App.INSTANCE;
        } else {
            Log.d(TAG, "mContext is already init");
        }
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind() called");
        return true;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate() called " + Data);
        isStarted = true;
        if (mContext == null) {
            mContext = App.INSTANCE;
        } else {
            Log.d(TAG, "mContext is already init");
        }

        CarHelper.INSTANCE.initCar(mContext);

        new UsbHelper();

        mAppController = new AppController(mContext);
        mAppController.setOnConnectListener(mOnConnectListeners);
        mAppController.setDeviceList(aliveDeviceList);
        mAppController.setHistoryDeviceList(historyDeviceList);

        mUsbHostController = new UsbHostController(mContext, mAppController, mOnConnectListeners);
        mUsbHostController.setDeviceList(aliveDeviceList);

        mAppController.setUsbHostController(mUsbHostController);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() called " + Data);
        if (mContext == null) {
            mContext = App.INSTANCE;
        } else {
            Log.d(TAG, "mContext is already init");
        }
        startForeground();
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
        isStarted = false;
        CarHelper.INSTANCE.release();
        mUsbHostController.unRegisterReceiver();
        mAppController.release();
    }

    private void startForeground() {
        Log.d(TAG, "startForeground() called");
        String channelId;
        channelId = createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        Notification notification = builder.setOngoing(true)
                .setSmallIcon(R.mipmap.sym_def_app_icon)
                .setPriority(PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(3839, notification);
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
