package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
    private static final int STATE_DISCONNECTING = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_PREPARING = 3;
    private static final int STATE_SWITCHING = 4;

    private static final int TYPE_NO_SESSION = 0;
    private static final int TYPE_USB_ANDROID_AUTO = 1;
    private static final int TYPE_WIFI_ANDROID_AUTO = 2;
    private static final int TYPE_USB_CAR_PLAY = 3;
    private static final int TYPE_WIFI_CAR_PLAY = 4;
    public static boolean isCanConnectingCPWifi = false;
    private static int CURRENT_CONNECT_STATE = 0;
    private static int CURRENT_SESSION_TYPE = 0;
    private final Object mLock = new Object();  // lock protect session status
    private final CarPlayClient mCarPlayClient;
    private final AapBinderClient mAapProxy;
    private final AndroidAutoDeviceClient mAndroidAutoDeviceClient;
    private final Context mContext;
    private final DeviceInfo lastDevice = new DeviceInfo(); //record last connected cp device, exit standby mode restore connection
    private final List<Phone> phones = new ArrayList<>();
    private final Runnable runnable = () -> {
        isCanConnectingCPWifi = false;
        Log.d(TAG, "isCanConnectingCPWifi has been revised to false");
    };
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            if (what == 1) {
                Log.d(TAG, "handleMessage: received " + what);
            }
        }
    };
    private final DeviceListHelper mDeviceListHelper;
    public DeviceInfo currentDevice = new DeviceInfo();
    public Phone switchingPhone = new Phone();
    private boolean CAR_PLAY_BIND_SUCCESS = false;
    private List<OnConnectListener> mOnConnectListeners;
    private final AapListener mAapListener = new AapListener() {
        @Override
        public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label, String deviceName, String instanceId) {
            Log.d(TAG, "sessionStarted() called with: isUsb = [" + b + "], label = [" + label + "], deviceName = [" + deviceName + "], instanceId = [" + instanceId + "]");
            synchronized (mLock) {
                CURRENT_SESSION_TYPE = b ? TYPE_USB_ANDROID_AUTO : TYPE_WIFI_ANDROID_AUTO;
                CURRENT_CONNECT_STATE = STATE_CONNECTED;
            }
            CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, "", "", b ? 1 : 2);
            mDeviceListHelper.write(deviceName, "", "", b ? 1 : 2);

            for (OnConnectListener listener : mOnConnectListeners) {
                try {
                    listener.onSessionStateUpdate("", "", 0, "connect success");
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
            handler.removeCallbacks(switchRunnable);
            handler.postDelayed(switchRunnable, 0);
        }

        @Override
        public void sessionTerminated(boolean b, int reason) {
            Log.d(TAG, "sessionTerminated() called with: isUsb = [" + b + "], reason = [" + reason + "]");
            synchronized (mLock) {
                currentDevice.reset();
                updateIdleState();
            }
            String usb = SystemProperties.get("sys.usbotg.power");
            if (TextUtils.equals(usb, "1")) {
                Log.d(TAG, "usb reset");
                SystemProperties.set("sys.usbotg.power", "0");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                SystemProperties.set("sys.usbotg.power", "1");
            }

            for (OnConnectListener listener : mOnConnectListeners) {
                try {
                    listener.onSessionStateUpdate("", "", 1, "connect success");
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    };
    private List<Device> aliveDeviceList = new ArrayList<>();
    private UsbHostController mUsbHostController;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");
            Bundle bundle = intent.getExtras();
            if (bundle.containsKey("aa")) {
                String value = (String) bundle.get("aa");
                if ("usb".equals(value)) {
                    //Galaxy S9    4d4e484d44563398    30:6A:85:15:1D:35
                    switchSession(1, "4d4e484d44563398", "");
                } else if ("wifi".equals(value)) {
                    //Pixel 5    58:24:29:80:66:A0
                    switchSession(2, "", "58:24:29:80:66:A0");
                }
            } else if (bundle.containsKey("list")) {
                Log.d(TAG, "onReceive: " + bundle.get("list"));
                for (int i = 0; i < aliveDeviceList.size(); i++) {
                    Log.d(TAG, "device " + (i + 1) + " : " + aliveDeviceList.get(i));
                }
            } else if (bundle.containsKey("device")) {
                Log.d(TAG, "onReceive: " + bundle.get("device"));
                Log.d(TAG, "onReceive: " + CommonUtilsKt.toJson(currentDevice));
            } else if (bundle.containsKey("handler")) {
                Log.d(TAG, "onReceive: " + bundle.get("handler") + " send msg");
                handler.removeCallbacks(runnable);
                Log.d(TAG, "after 5 seconds, isCanConnectingCPWifi is changed to false");
                handler.postDelayed(runnable, 5000);
            }
        }
    };
    private boolean canConnectUSB = true;
    private boolean switching;
    private final Runnable switchRunnable = () -> {
        switching = false;
        switchingPhone.clear();
        Log.d(TAG, "switching value has been revised to false");
    };

    public AppController(Context context, CarHelper carHelper) {
        Log.d(TAG, "AppController() called");
        this.mContext = context;

//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction("com.klv.test");
//        context.registerReceiver(receiver, intentFilter);

        mDeviceListHelper = new DeviceListHelper(context);
        carHelper.setOnCarPowerStateListener(new CarHelper.OnCarPowerStateListener() {
            @Override
            public void standby() {
                Log.d(TAG, "standby() called");
                if (currentSessionIsCarPlay()) {
                    stopCarPlay();
                }
            }

            @Override
            public void run() {
                Log.d(TAG, "run() called");
                if (sessionNotExist()) {
                    if (lastDevice == null) {
                        Log.d(TAG, "lastDevice is null");
                        return;
                    }
                    if (lastDevice.getLastConnectType() == SessionType.USB_CP) {
                        if (TextUtils.isEmpty(lastDevice.SerialNumber)) {
                            Log.d(TAG, "lastDevice.SerialNumber = " + lastDevice.SerialNumber);
                            return;
                        }
                        startCarPlay(lastDevice.SerialNumber, true);
                    }
                    if (lastDevice.getLastConnectType() == SessionType.WIFI_CP) {
                        if (TextUtils.isEmpty(lastDevice.BluetoothMac)) {
                            Log.d(TAG, "lastDevice.BluetoothMac = " + lastDevice.BluetoothMac);
                            return;
                        }
                        startCarPlay(CacheHelperKt.toHexString(lastDevice.BluetoothMac), false);
                    }
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
                        updateConnectingState();
                        mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, true);
                    } else {
                        mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, false);
                        mOnConnectListeners.forEach(listener -> {
                            try {
                                AtomicReference<String> name = new AtomicReference<>("");
                                aliveDeviceList.forEach(d -> {
                                    if (TextUtils.equals(btMac, d.getMac())) {
                                        name.set(d.getName());
                                    }
                                });
                                String c = "There is available device  " + name.get() + "  , do you want to start Android Auto ?";
                                listener.onNotification(1, c, "", btMac, 2);
                            } catch (RemoteException e) {
                                Log.e(TAG, e.toString());
                            }
                        });
                    }
                }
            }

            @Override
            public void onWirelessConnectionFailure(String btMac) {
                super.onWirelessConnectionFailure(btMac);
                Log.d(TAG, "onWirelessConnectionFailure() called with: btMac = [" + btMac + "]");
                updateIdleState();
            }

            @Override
            public void onUpdateWirelessDevice(AAWDeviceInfo device) {
                super.onUpdateWirelessDevice(device);
                Log.d(TAG, "onUpdateWirelessDevice: " + CommonUtilsKt.toJson(device));
                noticeExternal(device, 2);
            }
        });

        mAapProxy = new AapBinderClient();
        mAapProxy.registerListener(mAapListener);

        mCarPlayClient = new CarPlayClient();
        mCarPlayClient.initialise(context);
        CarPlayListener carPlayListener = new CarPlayListener() {
            private int sessionType = -1;
            private String serial = "";

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
                    if (sts == 0) {
                        boolean isUsb = sessionType == 1;

                        CURRENT_SESSION_TYPE = isUsb ? TYPE_USB_CAR_PLAY : TYPE_WIFI_CAR_PLAY;
                        CURRENT_CONNECT_STATE = STATE_CONNECTED;

                        currentDevice.update(isUsb ? serial : "", isUsb ? "" : btMac, deviceName, isUsb ? 3 : 4);

                        CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, "", "", isUsb ? 4 : 8);

                        mDeviceListHelper.write(deviceName, "", "", isUsb ? 4 : 8);

                        serial = currentDevice.SerialNumber;

                        if (!TextUtils.isEmpty(btMac)) {
                            if (!CacheHelperKt.contains(phones, btMac)) {
                                phones.add(new Phone(serial, btMac));
                            } else {
                                if (!"".equals(serial)) {
                                    phones.forEach(t -> {
                                                if (btMac.equals(t.getMac())) {
                                                    t.setSerial(serial);
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
                    Log.e(TAG, "standby mode, not start carplay");
                    return;
                }
                if (uniqueInfo == null || "".equals(uniqueInfo)) {
                    Log.e(TAG, "can't start carplay");
                    return;
                }
                if (connectType != 1) {
                    isCanConnectingCPWifi = true;
                    handler.removeCallbacks(runnable);
                    Log.d(TAG, "after 5 seconds, isCanConnectingCPWifi is changed to false");
                    handler.postDelayed(runnable, 30000);
                }
                sessionType = connectType;
                if (connectType == 1) {
                    serial = uniqueInfo;
                }
                startCarPlay(uniqueInfo, connectType == 1);
            }

            @Override
            public void onUSBIAP2DeviceStsChanged(boolean isDeviceAttached, String serialNum) {
                super.onUSBIAP2DeviceStsChanged(isDeviceAttached, serialNum);
                Log.d(TAG, "onUSBIAP2DeviceStsChanged() called with: isDeviceAttatched = [" + isDeviceAttached + "], serialNum = [" + serialNum + "]");
                if (isDeviceAttached) {

                } else {
                    noticeExternal(serialNum);
                    updateIdleState();
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
                    canConnectUSB = false;
                }
                if (state == 15) {
                    canConnectUSB = true;
                }
            }
        };
        mCarPlayClient.registerListener(carPlayListener);
    }

    public AapBinderClient getAapBinderClient() {
        return mAapProxy;
    }

    public boolean canConnectUSB() {
        Log.d(TAG, "canConnectUSB: " + canConnectUSB);
        return canConnectUSB;
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
                stopAndroidAuto();
                break;
            case TYPE_WIFI_ANDROID_AUTO:
                mAndroidAutoDeviceClient.DisconnectWirelessDevice();
                break;
            case TYPE_USB_CAR_PLAY:
            case TYPE_WIFI_CAR_PLAY:
                stopCarPlay();
                break;
            default:
                break;
        }

        int cnt = 0;
        while (cnt < 10) {
            if (CURRENT_CONNECT_STATE == STATE_IDLE) break;
            Log.i(TAG, "current connect state is not idle, try to refresh");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            cnt++;
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
            Log.d(TAG, "current session same as new session , do not switch");
            Log.d(TAG, currentDevice.toString());
            return;
        }
        if (!sessionNotExist()) {
            if (connectType == 3 || connectType == 4) {
                Log.d(TAG, "current session not null , do not switch to carplay");
                return;
            }
            stopLastSession();
        }
        connectSession(connectType, serialNumber, btMac);
    }

    /**
     * click bluetooth device list item
     */
    public void switchSession(String serial, String mac) {
        Log.d(TAG, "switchSession() called with: serial = [" + serial + "], mac = [" + mac + "]");
        if (switching) {
            for (OnConnectListener listener : mOnConnectListeners) {
                try {
                    if (TextUtils.equals(serial, switchingPhone.getSerial()) || TextUtils.equals(serial, switchingPhone.getMac())) {
                        listener.onSessionStateUpdate(serial, mac, -1, "switching");
                    } else {
                        listener.onSessionStateUpdate(serial, mac, -2, "busy");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.toString());
                }
            }
            return;
        } else {
            switching = true;
        }
        handler.postDelayed(switchRunnable, 15000);
        switchingPhone.update(serial, mac);

        if (!sessionNotExist()) {
            stopLastSession();
        }
        for (OnConnectListener l : mOnConnectListeners) {
            try {
                l.onRequestBluetoothPair(mac);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void connectSession(int type, String serial, String mac) {
        Log.d(TAG, "connectSession() called with: type = [" + type + "], serial = [" + serial + "], mac = [" + mac + "]");
        if (type == 1) {
            if (mUsbHostController != null)
                mUsbHostController.attach(USBKt.queryUsbDevice(mContext, serial));
        } else if (type == 2) {
            mAndroidAutoDeviceClient.ConnectWirelessDevice(mac);
        } else if (type == 3) {
            if (mUsbHostController != null)
                mUsbHostController.attach(USBKt.queryUsbDevice(mContext, serial));
        } else if (type == 4) {
            startCarPlay(mac, false);
        }
    }

    public void setOnConnectListener(List<OnConnectListener> mOnConnectListeners) {
        this.mOnConnectListeners = mOnConnectListeners;
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

        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener == null) {
                    Log.d(TAG, "OnConnectListener is null");
                } else {
                    listener.onDeviceUpdate(device);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "noticeExternal: ", e);
            }
        }

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

        Log.d(TAG, "update wireless " + device);
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener == null) {
                    Log.d(TAG, "noticeExternal: listener is null");
                } else {
                    listener.onDeviceUpdate(device);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "noticeExternal: ", e);
            }
        }

        for (int i = 0; i < aliveDeviceList.size(); i++) {
            Log.d(TAG, "device list " + (i + 1) + " : " + aliveDeviceList.get(i));
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
        if (sessionNotExist()) {
            if (androidAutoProxyValid()) {
                mAapProxy.startSession(deviceName);
            }
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

    public void stopAndroidAuto() {
        updateDisConnectingState();
        if (androidAutoProxyValid()) {
            if (CURRENT_SESSION_TYPE == TYPE_USB_ANDROID_AUTO) {
                mAapProxy.stopSession();
            } else {
                Log.d(TAG, "current session isn't android auto");
            }
        }
        updateIdleState();
    }

    public void roleSwitchComplete(String serialNumber) {
        Log.d(TAG, "roleSwitchComplete() called with: serialNumber = [" + serialNumber + "]");
        if (CAR_PLAY_BIND_SUCCESS) {
            mCarPlayClient.roleSwitchComplete(serialNumber);
        } else {
            Log.d(TAG, "delay 1 second, again role switch");
            new Handler().postDelayed(() -> {
                Log.d(TAG, "roleSwitchComplete: CarPlayBindSuccess is " + CAR_PLAY_BIND_SUCCESS);
                mCarPlayClient.roleSwitchComplete(serialNumber);
            }, 1000);
        }
    }

    public void startCarPlay(String btMac, boolean isUSB) {
        Log.d(TAG, "startCarPlay() called with: btMac = [" + btMac + "], isUSB = [" + isUSB + "]");
        if (TextUtils.isEmpty(btMac)) return;
        if (sessionNotExist() && carPlayProxyValid()) {
            updateConnectingState();
            Log.w(TAG, "start carplay session");
            mCarPlayClient.startSession(btMac, isUSB);
        }
    }

    public void stopCarPlay() {
        updateDisConnectingState();
        if (carPlayProxyValid()) {
            if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY || CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
                Log.w(TAG, "carplay stopSession() called");
                mCarPlayClient.stopSession();
            } else {
                Log.d(TAG, "current session isn't carplay, can't stop");
            }
        }
        updateIdleState();
    }

    public boolean sessionNotExist() {
        if (CURRENT_SESSION_TYPE == TYPE_NO_SESSION) {
            return true;
        } else {
            Log.d(TAG, "session already exists");
            return false;
        }
    }

    public boolean androidAutoProxyValid() {
        if (mAapProxy.getClient() != null) {
            return true;
        } else {
            Log.e(TAG, "android auto proxy invalid");
            return false;
        }
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
        return CURRENT_CONNECT_STATE == STATE_IDLE;
    }

    public boolean isPreParingState() {
        return CURRENT_CONNECT_STATE == STATE_PREPARING;
    }

    public boolean isSwitchingState() {
        return CURRENT_CONNECT_STATE == STATE_SWITCHING;
    }

    public void updatePreparingState() {
        Log.d(TAG, "session update to preparing state");
        CURRENT_CONNECT_STATE = STATE_PREPARING;
    }

    public void updateSwitchingState() {
        Log.d(TAG, "session update to switching state");
        CURRENT_CONNECT_STATE = STATE_SWITCHING;
    }

    public void updateConnectingState() {
        Log.d(TAG, "session update to connecting state");
        CURRENT_CONNECT_STATE = STATE_CONNECTING;
    }

    public void updateDisConnectingState() {
        Log.d(TAG, "session update to disconnecting state");
        CURRENT_CONNECT_STATE = STATE_DISCONNECTING;
    }

    public void updateIdleState() {
        Log.d(TAG, "session update to idle state");
        CURRENT_SESSION_TYPE = TYPE_NO_SESSION;
        CURRENT_CONNECT_STATE = STATE_IDLE;
        currentDevice.reset();
    }

    public boolean currentSessionIsCarPlay() {
        if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY || CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
            return true;
        } else {
            Log.d(TAG, "current session isn't carplay");
            return false;
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        mAapProxy.unregisterListener(mAapListener);
    }
}
