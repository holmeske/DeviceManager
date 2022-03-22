package com.yfve.t19c.projection.devicemanager;

import static com.yfve.t19c.projection.devicemanager.DeviceManagerService.historyDeviceList;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;

import com.google.gson.Gson;
import com.yfve.t19c.projection.androidauto.proxy.AAProxyDeviceListener;
import com.yfve.t19c.projection.androidauto.proxy.AAWDeviceInfo;
import com.yfve.t19c.projection.androidauto.proxy.AndroidAutoDeviceClient;
import com.yfve.t19c.projection.carplay.proxy.CarPlayClient;
import com.yfve.t19c.projection.carplay.proxy.CarPlayListener;
import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicemanager.constant.CacheHelperKt;
import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;
import com.yfve.t19c.projection.devicemanager.constant.Phone;
import com.yfve.t19c.projection.devicemanager.constant.SessionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AppController {
    private static final String TAG = "AppController";
    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_PREPARING = 3;
    private static final int STATE_SWITCHING = 4;

    private static final int TYPE_NO_SESSION = 0;
    private static final int TYPE_USB_ANDROID_AUTO = 1;
    private static final int TYPE_WIFI_ANDROID_AUTO = 2;
    private static final int TYPE_USB_CAR_PLAY = 3;
    private static final int TYPE_WIFI_CAR_PLAY = 4;
    public static boolean isCanConnectingCPWifi = false;
    public static boolean isStartingCarPlay = false;
    private static int CURRENT_CONNECT_STATE = 0;
    private static int CURRENT_SESSION_TYPE = 0;
    private final Object mLock = new Object();  // lock protect session status
    private final CarPlayClient mCarPlayClient;
    private final AapBinderClient mAapProxy;
    private final AndroidAutoDeviceClient mAndroidAutoDeviceClient;
    private final Context mContext;
    private final DeviceInfo lastDevice = new DeviceInfo(); //record last connected cp device, exit standby mode restore connection
    private final List<Phone> phones = new ArrayList<>();
    public static boolean isCertifiedVersion = true;
    private final List<String> mACLConnectedList = new ArrayList<>();
    private final Runnable runnable = () -> {
        isCanConnectingCPWifi = false;
        Log.d(TAG, "isCanConnectingCPWifi has been revised to false");
    };
    private final DeviceListHelper mDeviceListHelper;
    public DeviceInfo currentDevice = new DeviceInfo();
    public Phone switchingPhone = new Phone();
    private boolean autoConnectUsbAndroidAuto = true;
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            Log.d(TAG, "msg.what == " + what);
            if (what == 0) {
                setAutoConnectUsbAndroidAuto(false);
            } else if (what == 1) {
                setAutoConnectUsbAndroidAuto(true);
                currentDevice.reset();
            } else if (what == 3) {
                isStartingCarPlay = false;
                Log.d(TAG, "isStartingCarPlay == false");
            }
        }
    };
    private boolean CAR_PLAY_BIND_SUCCESS = false;
    private List<OnConnectListener> mOnConnectListeners;
    private List<Device> aliveDeviceList = new ArrayList<>();
    private UsbHostController mUsbHostController;
    private boolean isClientActiveRequest = false;
    private boolean canConnectUsbCarPlay = true;
    private boolean isSwitchingSession;
    private final Runnable switchRunnable = () -> {
        isSwitchingSession = false;
        switchingPhone.clear();
        Log.d(TAG, "isSwitchingSession value has been revised to false");
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");
            Bundle bundle = intent.getExtras();
            if (bundle.containsKey("aa")) {
                String value = (String) bundle.get("aa");
                if ("usb".equals(value)) {//Galaxy S9    4d4e484d44563398    30:6A:85:15:1D:35
                    switchSession(1, "4d4e484d44563398", "");
                } else if ("wifi".equals(value)) {//Pixel 5    58:24:29:80:66:A0
                    switchSession(2, "", "58:24:29:80:66:A0");
                }
            } else if (bundle.containsKey("onNotification")) {
                Log.d(TAG, "onReceive: " + bundle.get("onNotification"));
                try {
                    String id = (String) bundle.get("onNotification");
                    if (id != null) {
                        onNotification(Integer.parseInt(id));
                    }

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            } else if (bundle.containsKey("alive")) {
                Log.d(TAG, "onReceive: " + bundle.get("alive"));
                for (Device d : aliveDeviceList) {
                    Log.d(TAG, "alive device " + d.toString());
                }
                Log.d(TAG, "onReceive: currentDevice ==" + CommonUtilsKt.toJson(currentDevice));
            } else if (bundle.containsKey("handler")) {
                Log.d(TAG, "onReceive: " + bundle.get("handler") + " send msg");
                resetUsb();
            } else if (bundle.containsKey("switch")) {
                Log.d(TAG, "onReceive: " + bundle.get("switch"));
                switchSession(null, "30:6A:85:15:1D:35");
            } else if (bundle.containsKey("history")) {
                Log.d(TAG, "onReceive: " + bundle.get("history"));
                for (Device d : historyDeviceList) {
                    Log.d(TAG, "history device " + d.toString());
                }
            }
        }
    };
    private final AapListener mAapListener = new AapListener() {
        @Override
        public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label, String deviceName, String instanceId) {
            Log.d(TAG, "sessionStarted() called with: isUsb = [" + b + "], label = [" + label + "], deviceName = [" + deviceName + "], instanceId = [" + instanceId + "]");
            synchronized (mLock) {
                isClientActiveRequest = false;
                CURRENT_SESSION_TYPE = b ? TYPE_USB_ANDROID_AUTO : TYPE_WIFI_ANDROID_AUTO;
                CURRENT_CONNECT_STATE = STATE_CONNECTED;
            }
            UsbDevice d = USBKt.firstUsbDevice(mContext);
            String serial = d == null ? "" : d.getSerialNumber();
            String mac = currentDevice.BluetoothMac;
            CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, b ? serial : "", b ? "" : mac, b ? 1 : 2);
            mDeviceListHelper.write(deviceName, b ? serial : "", b ? "" : mac, b ? 1 : 2);
            onSessionStateUpdate(serial, mac, 0, "connected");

            if (isSwitchingSession) {//reset switching state
                handler.removeCallbacks(switchRunnable);
                handler.postDelayed(switchRunnable, 0);
            }
        }

        @Override
        public void sessionTerminated(boolean b, int reason) {
            //USER_SELECTION = 1, DEVICE_SWITCH = 2, NOT_SUPPORTED = 3, NOT_CURRENTLY_SUPPORTED = 4, PROBE_SUPPORTED = 5
            Log.d(TAG, "sessionTerminated() called with: isUsb = [" + b + "], reason = [" + reason + "]");
            synchronized (mLock) {
                isClientActiveRequest = false;
                if (reason == 1) {
                    resetUsb();
                } else if (reason == 3) {
                    onNotification(-3);
                } else if (reason == 4) {
                    onNotification(-4);
                }
                onSessionStateUpdate("", "", 1, "disconnected");
                currentDevice.reset();
                updateIdleState();
            }
        }
    };
    private BroadcastReceiver mBlueToothBroadcastReceiver;

    public AppController(Context context, CarHelper carHelper) {
        Log.d(TAG, "AppController() called");
        this.mContext = context;

//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction("com.klv.test");
//        context.registerReceiver(receiver, intentFilter);

        onBlueToothBroadcast();
        registerBluetoothReceiver();

        mDeviceListHelper = new DeviceListHelper(context);
        carHelper.setOnCarPowerStateListener(new CarHelper.OnCarPowerStateListener() {
            @Override
            public void standby() {
                Log.d(TAG, "standby() called");
                Log.d(TAG, "standby == " + CarHelper.isStandby());
                if (isCertifiedVersion) {
                    Log.d(TAG, "certified version not stop carplay");
                } else {
                    stopCarPlay();
                }
            }

            @Override
            public void run() {
                Log.d(TAG, "run() called");
                Log.d(TAG, "standby == " + CarHelper.isStandby());
                Log.d(TAG, "lastDevice.SerialNumber = " + lastDevice.SerialNumber);
                Log.d(TAG, "lastDevice.BluetoothMac = " + lastDevice.BluetoothMac);
                if (lastDevice.getLastConnectType() == SessionType.USB_CP) {
                    startCarPlay(lastDevice.SerialNumber, true);
                }
                if (lastDevice.getLastConnectType() == SessionType.WIFI_CP) {
                    startCarPlay(CacheHelperKt.toHexString(lastDevice.BluetoothMac), false);
                }
            }
        });

        mAndroidAutoDeviceClient = new AndroidAutoDeviceClient();
        mAndroidAutoDeviceClient.initialise(context);
        mAndroidAutoDeviceClient.registerListener(new AAProxyDeviceListener() {
            @Override
            public void onArbitrationWirelessConnect(String btMac) {
                super.onArbitrationWirelessConnect(btMac);
                Log.d(TAG, "onArbitrationWirelessConnect() called with: btMac = [" + btMac + "]");
                if (CarHelper.isOpenAndroidAuto()) {
                    if (isIdleState()) {
                        if (isClientActiveRequest) {
                            Log.d(TAG, "onArbitrationWirelessConnect: aribitrationWirelessResponse true");
                            mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, true);
                        } else {
                            onNotification(2, "", btMac);
                        }
                    } else {
                        Log.d(TAG, "onArbitrationWirelessConnect: aribitrationWirelessResponse false");
                        mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, false);
                        AtomicReference<String> name = new AtomicReference<>("");
                        aliveDeviceList.forEach(d -> {
                            if (TextUtils.equals(btMac, d.getMac())) {
                                name.set(d.getName());
                            }
                        });
                        String c = "There is available device  " + name.get() + "  , do you want to start Android Auto ?";
                        onNotification(1, c, btMac);
                    }
                }
            }

            @Override
            public void onWirelessConnectionFailure(String btMac) {
                super.onWirelessConnectionFailure(btMac);
                Log.d(TAG, "onWirelessConnectionFailure() called with: btMac = [" + btMac + "]");
                if (CURRENT_SESSION_TYPE == TYPE_WIFI_ANDROID_AUTO) {
                    isClientActiveRequest = false;
                    updateIdleState();
                }
            }

            @Override
            public void onUpdateWirelessDevice(AAWDeviceInfo device) {
                super.onUpdateWirelessDevice(device);
                Log.d(TAG, "onUpdateWirelessDevice: " + CommonUtilsKt.toJson(device));
                noticeExternal(device, 2);
            }

            @Override
            public void onUpdateMDBtMac(String btMac) {
                super.onUpdateMDBtMac(btMac);
                Log.d(TAG, "onUpdateMDBtMac() called with: btMac = [" + btMac + "]");
                currentDevice.BluetoothMac = btMac;
            }
        });

        mAapProxy = new AapBinderClient();
        mAapProxy.registerListener(mAapListener);

        mCarPlayClient = new CarPlayClient();
        mCarPlayClient.initialise(context);
        CarPlayListener carPlayListener = new CarPlayListener() {
            private int sessionType = -1;
            //private String serial = "";

            @Override
            public void onUpdateClientSts(boolean sts) {
                super.onUpdateClientSts(sts);
                Log.d(TAG, "onUpdateClientSts() called with: sts = [" + sts + "] , bind carplay service success");
                CAR_PLAY_BIND_SUCCESS = true;
            }

            @Override
            public void onSessionStsUpdate(int sts, String btMac, String deviceName) {
                super.onSessionStsUpdate(sts, btMac, deviceName);
                Log.d(TAG, "onSessionStsUpdate() called with: sts = [" + sts + "], btMac = [" + btMac + "], deviceName = [" + deviceName + "]");
                synchronized (mLock) {
                    onSessionStateUpdate("", btMac, sts, sts == 0 ? "connected" : "disconnected");
                    if (sts == 0) {
                        handler.removeMessages(3);
                        handler.sendEmptyMessage(3);

                        boolean isUsb = sessionType == 1;

                        CURRENT_SESSION_TYPE = isUsb ? TYPE_USB_CAR_PLAY : TYPE_WIFI_CAR_PLAY;
                        CURRENT_CONNECT_STATE = STATE_CONNECTED;

                        UsbDevice d = USBKt.firstUsbDevice(mContext);
                        String serial = d == null ? "" : d.getSerialNumber();
                        currentDevice.update(isUsb ? serial : "", btMac, deviceName, isUsb ? 3 : 4);
                        CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, isUsb ? serial : "", btMac, isUsb ? 4 : 8);
                        mDeviceListHelper.write(deviceName, isUsb ? serial : "", btMac, isUsb ? 4 : 8);

                        serial = currentDevice.SerialNumber;

                        if (!TextUtils.isEmpty(btMac)) {
                            if (!CacheHelperKt.contains(phones, btMac)) {
                                phones.add(new Phone(serial, btMac));
                            } else {
                                if (!"".equals(serial)) {
                                    String finalSerial = serial;
                                    phones.forEach(t -> {
                                                if (btMac.equals(t.getMac())) {
                                                    t.setSerial(finalSerial);
                                                }
                                            }
                                    );
                                }
                            }
                        }

                        Phone phone = CacheHelperKt.find(phones, btMac);
                        if (phone != null) {
                            lastDevice.SerialNumber = phone.getSerial();
                            lastDevice.BluetoothMac = phone.getMac();
                        } else {
                            Log.d(TAG, "phone is null");
                        }

                        Log.d(TAG, "lastDevice blueToothMac = " + lastDevice.BluetoothMac);
                        Log.d(TAG, "lastDevice serialNumber = " + lastDevice.SerialNumber);

                        lastDevice.setLastConnectType(sessionType == 1 ? 3 : 4);
                    } else if (sts == 1) {
                        currentDevice.reset();
                        updateIdleState();
                    }
                    isCanConnectingCPWifi = false;
                }
            }

            @Override
            public void onNotifyCPReadyToAuth(String uniqueInfo, int connectType) {
                super.onNotifyCPReadyToAuth(uniqueInfo, connectType);
                Log.d(TAG, "onNotifyCPReadyToAuth() called with: uniqueInfo = [" + uniqueInfo + "], connectType = [" + connectType + "]");
                if (CarHelper.isStandby()) {
                    Log.e(TAG, "standby mode, not start carplay");
                    return;
                }
                if (!isPresentCarPlay()) {
                    Log.e(TAG, "not start carplay");
                    return;
                }
                if (TextUtils.isEmpty(uniqueInfo)) {
                    Log.e(TAG, "illegal parameter");
                    return;
                }
                if (connectType != 1) {
                    isCanConnectingCPWifi = true;
                    handler.removeCallbacks(runnable);
                    Log.d(TAG, "after 5 seconds, isCanConnectingCPWifi is changed to false");
                    handler.postDelayed(runnable, 30000);
                }
                sessionType = connectType;
                startCarPlay(uniqueInfo, connectType == 1);
            }

            @Override
            public void onUSBIAP2DeviceStsChanged(boolean isDeviceAttached, String serialNum) {
                super.onUSBIAP2DeviceStsChanged(isDeviceAttached, serialNum);
                Log.d(TAG, "onUSBIAP2DeviceStsChanged() called with: isDeviceAttached = [" + isDeviceAttached + "], serialNum = [" + serialNum + "]");
                if (!isDeviceAttached) {
                    noticeExternal(serialNum);
                    updateIdleState();
                    handler.removeMessages(3);
                    handler.sendEmptyMessage(3);
                }
                isCanConnectingCPWifi = false;
            }

            @Override
            public void onNotifyWifi(int connectType, boolean available, String uniqueInfo, String deviceName) {
                super.onNotifyWifi(connectType, available, uniqueInfo, deviceName);
                Log.d(TAG, "onNotifyWifi() called with: connectType = [" + connectType + "], available = [" + available + "], uniqueInfo = [" + uniqueInfo + "], deviceName = [" + deviceName + "]");
                AAWDeviceInfo aawDeviceInfo = new AAWDeviceInfo();
                aawDeviceInfo.setDeviceType(2);
                aawDeviceInfo.setDeviceName(deviceName);
                aawDeviceInfo.setMacAddress(uniqueInfo);
                aawDeviceInfo.setAvailable(available);
                noticeExternal(aawDeviceInfo, 4);
            }


            @Override
            public void onNotiftIApAuthStatus(int AuthType, int state) {
                super.onNotiftIApAuthStatus(AuthType, state);
                Log.d(TAG, "onNotiftIApAuthStatus() called with: AuthType = [" + AuthType + "], state = [" + state + "]");
                if (AuthType != 1 && state != 15) {
                    canConnectUsbCarPlay = false;
                }
                if (state == 15) {
                    canConnectUsbCarPlay = true;
                }
            }
        };
        mCarPlayClient.registerListener(carPlayListener);
    }

    private void resetUsb() {
        if (aliveDeviceList.stream().anyMatch(device -> (device.isUsbAA() || device.isWirelessAA()) && device.getType() == 1)) {
            Log.d(TAG, "resetUsb() called");
            handler.sendEmptyMessage(0);
            SystemProperties.set("sys.usbotg.power", "0");
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            SystemProperties.set("sys.usbotg.power", "1");
            handler.sendEmptyMessageDelayed(1, 3000);
        } else {
            Log.d(TAG, "current no android auto usb device , do not need reset usb");
        }
    }

    private void onNotification(int id) {
        Log.d(TAG, "onNotification() called with: id = [" + id + "]" + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                listener.onNotification(id, "", "", "", 0);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public boolean isAutoConnectUsbAndroidAuto() {
        if (!autoConnectUsbAndroidAuto) {
            Log.d(TAG, "autoConnectUsbAndroidAuto == false");
        }
        return autoConnectUsbAndroidAuto;
    }

    public void setAutoConnectUsbAndroidAuto(boolean autoConnectUsbAndroidAuto) {
        this.autoConnectUsbAndroidAuto = autoConnectUsbAndroidAuto;
    }

    public AapBinderClient getAapBinderClient() {
        return mAapProxy;
    }

    public boolean canConnectUsbCarPlay() {
        if (canConnectUsbCarPlay) {
            return true;
        } else {
            Log.e(TAG, "canConnectUSB == false");
            return false;
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        this.aliveDeviceList = deviceList;
    }

    public void setHistoryDeviceList(List<Device> list) {
        mDeviceListHelper.queryAll().forEach(d -> list.add(new Device(-1, d.getName(), d.getSerial(), d.getMac(),
                1 == d.getAbility() || 3 == d.getAbility(),
                2 == d.getAbility() || 3 == d.getAbility(),
                4 == d.getAbility() || 12 == d.getAbility(),
                8 == d.getAbility() || 12 == d.getAbility(),
                false))
        );
        Log.d(TAG, "----------------------------------------HistoryDeviceList size = " + list.size());
    }

    public void setUsbHostController(UsbHostController controller) {
        this.mUsbHostController = controller;
    }

    public void stopLastSession() {
        switch (CURRENT_SESSION_TYPE) {
            case TYPE_USB_ANDROID_AUTO:
            case TYPE_WIFI_ANDROID_AUTO:
                stopAndroidAuto();
                break;
            case TYPE_USB_CAR_PLAY:
            case TYPE_WIFI_CAR_PLAY:
                stopCarPlay();
                break;
        }

        int cnt = 0;
        while (cnt < 10) {
            if (isIdleState()) break;
            Log.i(TAG, "current connect state is not idle, try to refresh");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
            cnt++;
        }
    }

    private void onNotification(int id, String content, String mac) {
        Log.d(TAG, "OnConnectListener.onNotification() called with: id = [" + id + "], content = [" + content + "], mac = [" + mac + "]"
                + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                listener.onNotification(id, content, "", mac, 2);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void onSessionStateUpdate(String serial, String mac, int state, String msg) {
        Log.d(TAG, "OnConnectListener.onSessionStateUpdate() called with: serial = [" + serial + "], mac = [" + mac + "], state = [" + state + "], msg = [" + msg + "]"
                + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                listener.onSessionStateUpdate(serial, mac, state, msg);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * @param connectType  1:usb_aa , 2:wireless_aa , 3:usb_cp , 4:wireless_cp
     * @param serialNumber usb device serial number
     * @param btMac        blue tooth mac
     */
    public void switchSession(int connectType, String serialNumber, String btMac) {
        Log.d(TAG, "switchSession() called with: connectType = [" + connectType + "], serialNumber = [" + serialNumber + "], btMac = [" + btMac + "]");
        if (ObjectsCompat.equals(currentDevice.SerialNumber, serialNumber) && !TextUtils.isEmpty(serialNumber) && !TextUtils.isEmpty(currentDevice.SerialNumber)
                || ObjectsCompat.equals(currentDevice.BluetoothMac, btMac) && !TextUtils.isEmpty(btMac) && !TextUtils.isEmpty(currentDevice.BluetoothMac)) {
            Log.d(TAG, "same as current session, do not switch");
            Log.d(TAG, currentDevice.toString());
            return;
        }
        stopLastSession();
        connectSession(connectType, serialNumber, btMac);
    }

    public void setOnConnectListener(List<OnConnectListener> mOnConnectListeners) {
        this.mOnConnectListeners = mOnConnectListeners;
    }

    /**
     * click bluetooth device list item
     */
    public void switchSession(String serial, String mac) {
        Log.d(TAG, "switchSession() called with: serial = [" + serial + "], mac = [" + mac + "]");
        if (TextUtils.equals(currentDevice.BluetoothMac, mac)) {
            Log.d(TAG, "same as current session bluetooth mac address");
            return;
        }
        for (Device d : aliveDeviceList) {
            if (TextUtils.equals(d.getMac(), mac) && (d.isWirelessCP() || d.isUsbCP())) {
                Log.d(TAG, "now not support carplay from bluetooth list switch session");
                return;
            }
        }
        if (isSwitchingSession) {
            if (TextUtils.equals(serial, switchingPhone.getSerial()) || TextUtils.equals(serial, switchingPhone.getMac())) {
                onSessionStateUpdate(serial, mac, -1, "switching");
            } else {
                onSessionStateUpdate(serial, mac, -2, "busy");
            }
            return;
        } else {
            isSwitchingSession = true;
        }
        handler.postDelayed(switchRunnable, 60000);
        switchingPhone.update(serial, mac);

        stopLastSession();

        if (aliveDeviceList.stream().anyMatch(device -> device.getMac().equals(mac))) {
            connectSession(2, serial, mac);
        } else {
            if (mACLConnectedList.contains(mac)) {
                onNotification(-5);
            } else {
                for (OnConnectListener l : mOnConnectListeners) {
                    try {
                        Log.d(TAG, "onRequestBluetoothPair " + mac);
                        l.onRequestBluetoothPair(mac);
                    } catch (RemoteException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    /**
     * update usb device
     *
     * @param serialNumber usb device serial number
     */
    private void noticeExternal(String serialNumber) {
        Device device = null;
        for (Device d : aliveDeviceList) {
            if (Objects.equals(d.getSerial(), serialNumber)) {
                device = new Device(d.getType(), d.getName(), d.getSerial(), d.getMac(), d.isUsbAA(), d.isWirelessAA(), d.isUsbCP(), d.isWirelessCP(), false);
            }
        }
        Log.d(TAG, "update usb " + device);
        Device finalDevice = device;
        aliveDeviceList.removeIf(d -> Objects.equals(d.getSerial(), finalDevice == null ? "" : finalDevice.getSerial()));

        onDeviceUpdate(device);
        for (int i = 0; i < aliveDeviceList.size(); i++) {
            Log.d(TAG, "device " + (i + 1) + " : " + aliveDeviceList.get(i));
        }
    }

    /**
     * update wireless device
     *
     * @param aawDeviceInfo device info
     * @param type          2:wireless_aa , 4:wireless_cp
     */
    private void noticeExternal(AAWDeviceInfo aawDeviceInfo, int type) {
        Log.d(TAG, "noticeExternal() called with: mac = [" + aawDeviceInfo.getMacAddress() + "], type = [" + type + "]");
        Device device = new Device();
        device.setType(2);//1:usb , 2:wireless
        device.setName(aawDeviceInfo.getDeviceName());
        device.setMac(aawDeviceInfo.getMacAddress());
        if (type == 2) {
            device.setWirelessAA(aawDeviceInfo.getCapability() == 16 || aawDeviceInfo.getCapability() == 17);
        } else if (type == 4) {
            device.setWirelessCP(true);
        }
        device.setAvailable(aawDeviceInfo.getAvailable());

        AtomicBoolean isContain = new AtomicBoolean(false);
        aliveDeviceList.forEach(d -> {
            if (ObjectsCompat.equals(d.getMac(), device.getMac())) {
                isContain.set(true);
            }
        });
        if (aawDeviceInfo.getAvailable()) {
            if (!isContain.get()) {
                aliveDeviceList.add(device);
            } else {
                Log.d(TAG, "list already contains the device, do not add");
            }
        } else {
            aliveDeviceList.removeIf(d -> Objects.equals(d.getMac(), device.getMac()));
        }
        onDeviceUpdate(device);
        for (int i = 0; i < aliveDeviceList.size(); i++) {
            Log.d(TAG, "device list " + (i + 1) + " : " + aliveDeviceList.get(i));
        }
    }

    public boolean sameUsbDevice(UsbDevice device) {
        if (Objects.equals(currentDevice.SerialNumber, device.getSerialNumber())) {
            return true;
        } else {
            Log.d(TAG, "current device != intent device ,"
                    + " current device = " + currentDevice.SerialNumber + ", " + currentDevice.DeviceName
                    + " , intent  device = " + device.getSerialNumber() + ", " + device.getDeviceName());
            return false;
        }
    }

    public void updateCurrentDevice(UsbDevice device, int connectType) {
        Log.d(TAG, "updateCurrentDevice() called with: serial = [" + device.getSerialNumber() + "], name = [" + device.getProductName() + "]");
        currentDevice.update(device.getSerialNumber(), "", device.getDeviceName(), connectType);
    }

    public boolean isPresentCarPlay() {
        return CarHelper.isOpenCarPlay();
    }

    public void startAndroidAuto(String deviceName) {
        Log.d(TAG, "startAndroidAuto() called with: deviceName = [" + deviceName + "]");
        if (isIdleState()) {
            mAapProxy.startAndroidAuto(deviceName);
        }
    }

    public void stopAndroidAuto() {
        if (CURRENT_SESSION_TYPE == TYPE_USB_ANDROID_AUTO) {
            mAapProxy.stopAndroidAuto();
        } else if (CURRENT_SESSION_TYPE == TYPE_WIFI_ANDROID_AUTO) {
            mAndroidAutoDeviceClient.DisconnectWirelessDevice();
        } else {
            Log.d(TAG, "current session isn't android auto, no need stop");
        }
        updateIdleState();
    }

    public void roleSwitchComplete(String serialNumber) {
        Log.d(TAG, "roleSwitchComplete() called with: serialNumber = [" + serialNumber + "]");
        if (CAR_PLAY_BIND_SUCCESS) {
            mCarPlayClient.roleSwitchComplete(serialNumber);
        } else {
            Log.d(TAG, "delay 1 second, again role switch");
            handler.postDelayed(() -> {
                Log.d(TAG, "roleSwitchComplete: CarPlayBindSuccess == " + CAR_PLAY_BIND_SUCCESS);
                mCarPlayClient.roleSwitchComplete(serialNumber);
            }, 1000);
        }
    }

    public void connectSession(int type, String serial, String mac) {
        Log.d(TAG, "connectSession() called with: type = [" + type + "], serial = [" + serial + "], mac = [" + mac + "]");
        if (type == 1 || type == 3) {
            mUsbHostController.attach(USBKt.queryUsbDevice(mContext, serial));
        } else if (type == 2) {
            Log.d(TAG, "connectSession: aribitrationWirelessResponse false");
            mAndroidAutoDeviceClient.aribitrationWirelessResponse(mac, false);
            Log.d(TAG, "connectSession: isClientActiveRequest == true");
            isClientActiveRequest = true;
            Log.d(TAG, "connectSession: ConnectWirelessDevice " + mac);
            mAndroidAutoDeviceClient.ConnectWirelessDevice(mac);
        } else if (type == 4) {
            startCarPlay(mac, false);
        }
    }

    public void stopCarPlay() {
        if (carPlayProxyValid()) {
            if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY || CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
                Log.w(TAG, "carplay stopSession() called");
                mCarPlayClient.stopSession();
            } else {
                Log.d(TAG, "current session isn't carplay, no need stop");
            }
        }
        updateIdleState();
    }

    private boolean carPlayProxyValid() {
        if (CAR_PLAY_BIND_SUCCESS) {
            return true;
        } else {
            Log.e(TAG, "carplay proxy invalid");
            return false;
        }
    }

    public boolean isIdleState() {
        if (CURRENT_CONNECT_STATE == STATE_IDLE) {
            return true;
        } else {
            Log.e(TAG, "current connect state is not idle, current session type is " + CURRENT_SESSION_TYPE);
            return false;
        }
    }

    public boolean isPreParingState() {
        return CURRENT_CONNECT_STATE == STATE_PREPARING;
    }

    public boolean isSwitchingState() {
        return CURRENT_CONNECT_STATE == STATE_SWITCHING;
    }

    public void onDeviceUpdate(Device device) {
        Log.d(TAG, "onDeviceUpdate() called with: " + device.toString() + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener == null) {
                    Log.d(TAG, "noticeExternal: listener is null");
                } else {
                    listener.onDeviceUpdate(device);
                }
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void startCarPlay(String uniqueInfo, boolean isUSB) {
        Log.d(TAG, "startCarPlay() called with: uniqueInfo = [" + uniqueInfo + "], isUSB = [" + isUSB + "]");
        if (isSwitchingSession) {
            Log.d(TAG, "current is switching android auto, do not start carplay");
            return;
        }
        if (TextUtils.isEmpty(uniqueInfo)) return;
        if (isIdleState() && carPlayProxyValid()) {
            if (isStartingCarPlay) {
                Log.d(TAG, "isStartingCarPlay == true, do not again start carplay");
            } else {
                isStartingCarPlay = true;
                Log.d(TAG, "isStartingCarPlay = true");
                handler.sendEmptyMessageDelayed(3, 30000);
                Log.w(TAG, "start carplay session");
                mCarPlayClient.startSession(uniqueInfo, isUSB);
            }
        }
    }

    public void updatePreparingState() {
        Log.d(TAG, "session state update to preparing");
        CURRENT_CONNECT_STATE = STATE_PREPARING;
    }

    public void updateSwitchingState() {
        Log.d(TAG, "session state update to switching");
        CURRENT_CONNECT_STATE = STATE_SWITCHING;
    }

    public void updateIdleState() {
        Log.d(TAG, "session state update to idle");
        CURRENT_SESSION_TYPE = TYPE_NO_SESSION;
        CURRENT_CONNECT_STATE = STATE_IDLE;
        currentDevice.reset();
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        mAapProxy.unregisterListener(mAapListener);

        if (mBlueToothBroadcastReceiver != null)
            mContext.unregisterReceiver(mBlueToothBroadcastReceiver);
    }

    private void onBlueToothBroadcast() {
        mBlueToothBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
//                int retVal = 0;
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        Log.d(TAG, "onReceive: ACTION_STATE_CHANGED");
                        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                            Log.d(TAG, "Bluetooth State ON");
//                            retVal = mServerListener.startRfcommServer();
//                            if (0 != retVal) {
//                                Log.e(TAG, "Couldn't start RFCOMM server");
//                            }
                        } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                            Log.d(TAG, "Bluetooth State OFF");
//                            mServerListener.stopRfcommServer();
//                            clearAllAuthDeviceFromQueue();
//                            clearAllAvailableDevice();
                        }
                        break;
                    case BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED:
                        Log.d(TAG, "onReceive: ACTION_CONNECTION_STATE_CHANGED");
//                        if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
//                            Log.d(TAG, "HFP connection to device");
//                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                            Log.d(TAG, "+++++ device : " + device.getName() + " connected with address:" + device.getAddress());
//                            //checkConnectedDevice(device.getAddress());
//                            mHfpDeviceHeadsetClient = device.getAddress();
//                            updateAvailableDeviceInfo(device.getName(), device.getAddress());
//                        } else if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_DISCONNECTED) {
//                            Log.d(TAG, "current bt device disconnect with remote");
//                            BluetoothDevice disDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                            Log.d(TAG, "----- device : " + disDevice.getName() + " disconnect with address:" + disDevice.getAddress());
//                            /*if (null != mServerListener) {
//                                mServerListener.hfpDisconnect(disDevice.getAddress());
//                                removeAvailableDevice(disDevice.getAddress());
//                            }*/
//                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Log.d(TAG, "onReceive: ACTION_ACL_CONNECTED");
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (null != device) {
                            Log.d(TAG, "+++++ ACL link up device : " + device.getName() + " with address:" + device.getAddress());
                            if (!mACLConnectedList.contains(device.getAddress())) {
                                mACLConnectedList.add(device.getAddress());
                            }
//                            if (null != mServerListener) {
//                                mServerListener.deviceConnectedWithACL(device.getAddress());
//                            }
                        }
                        new Gson().toJson(mACLConnectedList);
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Log.d(TAG, "onReceive: ACTION_ACL_DISCONNECTED");
                        BluetoothDevice aclDisconnectDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (null != aclDisconnectDevice) {
                            Log.d(TAG, "----- ACL link down device : " + aclDisconnectDevice.getName() + " with address:" + aclDisconnectDevice.getAddress());
                            mACLConnectedList.add(aclDisconnectDevice.getAddress());
//                            if (null != mServerListener) {
//                                mServerListener.hfpDisconnect(aclDisconnectDevice.getAddress());
//                            }
                        }
                        new Gson().toJson(mACLConnectedList);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
        if (null != mContext) {
            mContext.registerReceiver(mBlueToothBroadcastReceiver, filter);
        } else {
            Log.e(TAG, "Couldn't register notifier into bt with nul context.");
        }
    }
}
