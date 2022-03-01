package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import com.yfve.t19c.projection.androidauto.proxy.AAProxyDeviceListener;
import com.yfve.t19c.projection.androidauto.proxy.AAWDeviceInfo;
import com.yfve.t19c.projection.androidauto.proxy.AndroidAutoDeviceClient;
import com.yfve.t19c.projection.carplay.proxy.CarPlayClient;
import com.yfve.t19c.projection.carplay.proxy.CarPlayListener;
import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicemanager.callback.OnCallBackListener;
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
    //private final DeviceListController mDeviceListController;
    private final Context mContext;
    private final DeviceInfo lastConnectedDevice = new DeviceInfo(); //record last connected cp device
    private final List<Phone> phones = new ArrayList<>();
    public DeviceInfo currentDevice = new DeviceInfo();
    private boolean CAR_PLAY_BIND_SUCCESS = false;
    private List<OnConnectListener> mOnConnectListeners;
    private List<Device> aliveDeviceList = new ArrayList<>();
    private List<Device> historyDeviceList = new ArrayList<>();
    private OnCallBackListener onCallBackListener;
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
            } else if (bundle.containsKey("cp")) {
                String value = (String) bundle.get("cp");
                switch (value) {
                    case "usb":
                        switchSession(3, "4d4e484d44563398", "");
                        break;
                    case "wifi":
                        switchSession(4, "", "30:6A:85:15:1D:35");
                        break;
                    case "stop":
                        stopCarPlay();
                        break;
                }
            } else if (bundle.containsKey("list")) {
                Log.d(TAG, "onReceive: " + bundle.get("list"));
                for (int i = 0; i < aliveDeviceList.size(); i++) {
                    Log.d(TAG, "device " + (i + 1) + " : " + aliveDeviceList.get(i));
                }
            } else if (bundle.containsKey("device")) {
                Log.d(TAG, "onReceive: " + bundle.get("device"));
                Log.d(TAG, "onReceive: " + CommonUtilsKt.toJson(currentDevice));
            }
        }
    };
    private DeviceListHelper mDeviceListHelper;
    private AAWDeviceInfo lastAAWDeviceInfo;
    private boolean canConnectUSB = true;

    public AppController(Context context, DeviceListController deviceListController, CarHelper carHelper) {
        Log.d(TAG, "AppController() called");
        this.mContext = context;

//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction("com.klv.test");
//        context.registerReceiver(receiver, intentFilter);

        mDeviceListHelper = new DeviceListHelper(context);
        //this.mDeviceListController = deviceListController;
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
                    if (lastConnectedDevice == null) {
                        Log.d(TAG, "lastConnectedDevice is null");
                        return;
                    }
                    if (lastConnectedDevice.getLastConnectType() == SessionType.USB_CP) {
                        if (TextUtils.isEmpty(lastConnectedDevice.SerialNumber)) {
                            Log.d(TAG, "lastConnectedDevice.SerialNumber = " + lastConnectedDevice.SerialNumber);
                            return;
                        }
                        startCarPlay(lastConnectedDevice.SerialNumber, true);
                    }
                    if (lastConnectedDevice.getLastConnectType() == SessionType.WIFI_CP) {
                        if (TextUtils.isEmpty(lastConnectedDevice.BluetoothMac)) {
                            Log.d(TAG, "lastConnectedDevice.BluetoothMac = " + lastConnectedDevice.BluetoothMac);
                            return;
                        }
                        startCarPlay(CacheHelperKt.toHexString(lastConnectedDevice.BluetoothMac), false);
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
                if (isPresentAndroidAuto()) {
                    if (isIdleState()) {
                        updateConnectingState();
                        mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, true);
                    } else {
                        mAndroidAutoDeviceClient.aribitrationWirelessResponse(btMac, false);
                        if (!sessionNotExist()) {
                            mOnConnectListeners.forEach(listener -> {
                                try {
                                    AtomicReference<String> name = new AtomicReference<>("");
                                    aliveDeviceList.forEach(d -> {
                                        if (TextUtils.equals(btMac, d.getMac())) {
                                            name.set(d.getName());
                                        }
                                    });
                                    String c = "There is available device  " + name.get() + "  , do you want to start Android Auto ?";
                                    listener.onNotification(1, c, 2, "", btMac);
                                } catch (RemoteException e) {
                                    Log.e(TAG, e.toString());
                                }
                            });
                        }
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
                lastAAWDeviceInfo = device;
                noticeExternal(device, 2);
            }
        });

        mAapProxy = new AapBinderClient();
        mAapProxy.setOnCallBackListener(() -> {
            if (onCallBackListener != null) {
                onCallBackListener.callback();
            }
        });
        mAapProxy.registerListener(new AapListener() {
            @Override
            public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label, String deviceName, String instanceId) {
                Log.d(TAG, "sessionStarted() called with: isUsb = [" + b + "], label = [" + label + "], deviceName = [" + deviceName + "], instanceId = [" + instanceId + "]");
                synchronized (mLock) {
                    CURRENT_SESSION_TYPE = b ? TYPE_USB_ANDROID_AUTO : TYPE_WIFI_ANDROID_AUTO;
                    CURRENT_CONNECT_STATE = STATE_CONNECTED;
                }
                CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, "", "", b ? 1 : 2);
                mDeviceListHelper.write(deviceName, "", "", b ? 1 : 2);
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
            }
        });

        mCarPlayClient = new CarPlayClient();
        mCarPlayClient.initialise(context);
        CarPlayListener carPlayListener = new CarPlayListener() {
            private int sessionType = -1;
            private String serial;

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
                        CURRENT_SESSION_TYPE = sessionType == 1 ? TYPE_USB_CAR_PLAY : TYPE_WIFI_CAR_PLAY;
                        CURRENT_CONNECT_STATE = STATE_CONNECTED;

                        boolean isUsb = sessionType == 1;
                        currentDevice.update(isUsb ? btMac : "", isUsb ? "" : btMac, deviceName, isUsb ? 3 : 4);
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
                            lastConnectedDevice.SerialNumber = phone.getSerial();
                            lastConnectedDevice.BluetoothMac = phone.getMac();
                        } else {
                            Log.d(TAG, "phone is null");
                        }

                        Log.d(TAG, "lastConnectedDevice blueToothMac = " + lastConnectedDevice.BluetoothMac);
                        Log.d(TAG, "lastConnectedDevice serialNumber = " + lastConnectedDevice.SerialNumber);

                        lastConnectedDevice.setLastConnectType(sessionType == 1 ? 3 : 4);

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
                    Log.d(TAG, "in standby mode, don't start carplay");
                    return;
                }
                if (uniqueInfo == null || "".equals(uniqueInfo)) {
                    Log.e(TAG, "can't start carplay");
                    return;
                }
                if (connectType != 1) {
                    isCanConnectingCPWifi = true;
                }
                if (isPresentCarPlay()) {
                    sessionType = connectType;
                    startCarPlay(uniqueInfo, connectType == 1);
                }
            }

            @Override
            public void onUSBIAP2DeviceStsChanged(boolean isDeviceAttatched, String serialNum) {
                super.onUSBIAP2DeviceStsChanged(isDeviceAttatched, serialNum);
                Log.d(TAG, "onUSBIAP2DeviceStsChanged() called with: isDeviceAttatched = [" + isDeviceAttatched + "], serialNum = [" + serialNum + "]");
                if (isDeviceAttatched) {
                    //mDeviceListController.addUsbDeviceToList(serialNum);
                } else {
                    //mDeviceListController.removeUsbDeviceFromList(serialNum);
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

    public boolean canConnectUSB() {
        Log.d(TAG, "canConnectUSB: " + canConnectUSB);
        return canConnectUSB;
    }

    public void setOnCallBackListener(OnCallBackListener onCallBackListener) {
        this.onCallBackListener = onCallBackListener;
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
        Log.d(TAG, "----------------------------------------setHistoryDeviceList size = " + list.size());
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

    public void switchAAWDevice() {
        Log.d(TAG, "switchAAWDevice() called");
        switchSession(2, lastAAWDeviceInfo.getSerialNumber(), lastAAWDeviceInfo.getMacAddress());
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
                    listener.update(device);
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
                    listener.update(device);
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

    public boolean isPresentAndroidAuto() {
        return CarHelper.isOpenAndroidAuto();
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

}
