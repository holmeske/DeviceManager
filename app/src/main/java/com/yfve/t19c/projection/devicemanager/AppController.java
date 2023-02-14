package com.yfve.t19c.projection.devicemanager;

import static com.yfve.t19c.projection.devicemanager.constant.LocalData.AvailableDeviceBtMacList;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindCurrentAvailableByMac;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindInstanceIdByMac;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindInstanceIdBySerial;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindMacByInstanceId;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindMacBySerial;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindPreAvailableByMac;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.FindSerialByInstanceId;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.HistoryDeviceList;
import static com.yfve.t19c.projection.devicemanager.constant.LocalData.LAST_REASON;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.yfve.t19c.projection.devicemanager.callback.OnUpdateClientStsListener;
import com.yfve.t19c.projection.devicemanager.constant.CacheHelperKt;
import com.yfve.t19c.projection.devicemanager.constant.CarPowerState;
import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;
import com.yfve.t19c.projection.devicemanager.constant.LocalData;
import com.yfve.t19c.projection.devicemanager.constant.Phone;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AppController {
    public static final int USB_ANDROID_AUTO = 1;
    public static final int WIFI_ANDROID_AUTO = 2;
    private static final String TAG = "AppController";
    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_PREPARING = 3;
    private static final int STATE_AOA_SWITCHING = 4;
    private static final int NULL = 0;
    private static final int USB_CARPLAY = 3;
    private static final int WIFI_CARPLAY = 4;
    public static boolean isResettingUsb = false;
    public static boolean isCanConnectingCPWifi = false;
    public static boolean isConnectingCarPlay = false;
    public static boolean isConnectingWiFiAndroidAuto = false;
    public static boolean isCertifiedVersion = false;   //certify version
    public static boolean isSOPVersion = true;         //sop version
    public static boolean isReplugged = true;
    public static int CURRENT_SESSION = 0;
    private static int isReplugged_id;
    private static int CURRENT_CONNECT_STATE = 0;
    private final CarPlayClient mCarPlayClient;
    private final AapBinderClient mAapProxy;
    private final AndroidAutoDeviceClient mAndroidAutoDeviceClient;
    private final Context mContext;
    private final DeviceInfo lastDevice = new DeviceInfo(); //record last connected cp device, exit standby mode restore connection
    private final List<String> mACLConnectedList = new ArrayList<>();
    private final Runnable runnable = () -> {
        isCanConnectingCPWifi = false;
        Log.d(TAG, "isCanConnectingCPWifi == false");
    };
    private final DeviceListHelper mDeviceListHelper;
    public DeviceInfo currentDevice = new DeviceInfo();
    public Phone switchingPhone = new Phone();
    public boolean CAR_PLAY_BIND_SUCCESS = false;
    private boolean autoConnectUsbAndroidAuto = true;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            if (what == 0) {
                setAutoConnectUsbAndroidAuto(false);
            } else if (what == 1) {
                setAutoConnectUsbAndroidAuto(true);
            } else if (what == 2) {
                isResettingUsb = false;
                Log.d(TAG, "isResettingUsb == false");
            } else if (what == 3) {
                isConnectingCarPlay = false;
                Log.d(TAG, "isStartingCarPlay == false");
            } else if (what == 4) {
                stopCarPlay();
            }
        }
    };
    private OnUpdateClientStsListener onUpdateClientStsListener;
    private List<OnConnectListener> mOnConnectListeners;
    private List<Device> aliveDeviceList = new ArrayList<>();
    private UsbHostController mUsbHostController;
    private boolean canConnectUsbCarPlay = true;
    private boolean isSwitchingSession;
    private final Runnable SwitchingEndRunnable = () -> {
        isSwitchingSession = false;
        Log.d(TAG, "isSwitchingSession == false");
        switchingPhone.clear();
    };
    private final AapListener mAapListener = new AapListener() {
        @Override
        public void sessionStarted(boolean b, String smallIcon, String mediumIcon, String largeIcon, String label, String deviceName, String instanceId) {
            Log.d(TAG, "sessionStarted() called with: isUsb = [" + b + "], label = [" + label + "], deviceName = [" + deviceName + "], instanceId = [" + instanceId + "]");
            isConnectingWiFiAndroidAuto = false;
            CURRENT_SESSION = b ? USB_ANDROID_AUTO : WIFI_ANDROID_AUTO;
            CURRENT_CONNECT_STATE = STATE_CONNECTED;
            currentDevice.setDeviceName(deviceName);
            currentDevice.setInstanceId(instanceId);
            resetSwitchingSessionState();
            if (b) {
                LocalData.LAST_ANDROID_AUTO_DEVICE_SERIAL = label;
                FindSerialByInstanceId.put(instanceId, label);

                currentDevice.setConnectionType(1);
                currentDevice.setSerialNumber(label);

                String mac = FindMacBySerial.get(label);
                Log.d(TAG, "mac == " + mac);
                if (TextUtils.isEmpty(mac) || TextUtils.equals(mac, "null")) {
                    mac = mDeviceListHelper.getMac(label);
                }
                currentDevice.setBluetoothMac(mac);
                Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(currentDevice));

                CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, label, mac == null ? "" : mac, 1);
                mDeviceListHelper.write(deviceName, label, mac, 1);

                onSessionStateUpdate(label, mac, 0, "connected");
            } else {
                currentDevice.setConnectionType(2);

                String mac = FindMacByInstanceId.get(instanceId);
                String serial = FindSerialByInstanceId.get(instanceId);
                Log.d(TAG, "mac = " + mac + "  ,  serial  =  " + serial);
                currentDevice.setSerialNumber(serial);

                if (!TextUtils.isEmpty(mac)) {
                    currentDevice.setBluetoothMac(mac);
                } else {
                    mac = currentDevice.getBluetoothMac();
                }

                Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(currentDevice));

                CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, serial == null ? "" : serial, mac == null ? "" : mac, 2);
                mDeviceListHelper.write(deviceName, serial, mac, 2);

                onSessionStateUpdate(serial, mac, 0, "connected");
            }
            Log.d(TAG, "sessionStarted end");
        }

        @Override
        public void sessionTerminated(boolean b, int reason) {
            LAST_REASON = reason;
            isConnectingWiFiAndroidAuto = false;
            //USER_SELECTION = 1, DEVICE_SWITCH = 2, NOT_SUPPORTED = 3, NOT_CURRENTLY_SUPPORTED = 4, PROBE_SUPPORTED = 5
            //old device  1 2 3 4  not process , 0 auto connect
            Log.d(TAG, "sessionTerminated() called with: isUsb = [" + b + "], reason = [" + reason + "]");
            String mac = currentDevice.BluetoothMac;
            String serial = currentDevice.SerialNumber;
            if (b) {
                Log.d(TAG, "release session");
                mAapProxy.stopAndroidAuto();
                int attached = USBKt.usbDeviceList(mContext).size();
                Log.d(TAG, "attached size = " + attached + ", current mac = " + mac + ", current serial = " + serial);
                if (attached == 0) {
                    if (aliveDeviceList.stream().anyMatch(device -> Objects.equals(device.getMac(), mac))) {
                        startWirelessAndroidAuto(mac, 1);
                    } else {
                        Log.d(TAG, "device not available , not start wifi android auto");
                    }
                }
            }
            updateIdleState();
            if (reason == 0) {
                Log.d(TAG, "sessionTerminated: reconnect");
                if (b) {
                    resetUsb(true);
                } else {//"30:6A:85:15:1D:35"
                    startWirelessAndroidAuto(mac, 2);
                }
            } else if (reason == 1) {
                if (b) {
                    resetUsb(false);
                }
            } else if (reason == 3) {
                isReplugged = false;
                isReplugged_id = -3;
                onNotification(-3);
            } else if (reason == 4) {
                isReplugged = false;
                isReplugged_id = -4;
                //onNotification(-4);
            }
            onSessionStateUpdate(serial, mac, 1, "disconnected");
            Log.d(TAG, "sessionTerminated end");
        }
    };

    /*private final BroadcastReceiver receiver = new BroadcastReceiver() {
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
                onSessionStateUpdate(currentDevice.SerialNumber, currentDevice.BluetoothMac, 1, "disconnected");
                updateIdleState();
                Log.d(TAG, "onReceive: currentDevice == " + CommonUtilsKt.toJson(currentDevice));
                Log.d(TAG, "onReceive: getUsbDeviceList size == " + USBKt.usbDeviceList(mContext.getApplicationContext()).size());
            } else if (bundle.containsKey("reset")) {
                resetUsb(false);
            } else if (bundle.containsKey("switch")) {
                Log.d(TAG, "onReceive: " + bundle.get("switch"));
                switchSession("802KPVH1524647", "");
            } else if (bundle.containsKey("history")) {
                Log.d(TAG, "onReceive: " + bundle.get("history"));
                for (Device d : historyDeviceList) {
                    Log.d(TAG, "history device " + d.toString());
                }
            } else if (bundle.containsKey("reason")) {
                Log.d(TAG, "onReceive: " + bundle.get("reason"));
                try {
                    String reason = (String) bundle.get("reason");
                    if (reason != null) {
                        stopAndroidAuto();
                        Thread.sleep(1000);
                        mAapListener.sessionTerminated(false, Integer.parseInt(reason));
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    };*/

    private BroadcastReceiver mBlueToothBroadcastReceiver;

    public AppController(Context context) {
        Log.d(TAG, "AppController() called");

        this.mContext = context;

        /*IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.klv.test");
        context.registerReceiver(receiver, intentFilter);*/

        onBlueToothBroadcast();
        registerBluetoothReceiver();

        mDeviceListHelper = new DeviceListHelper(context);
//        mDeviceListHelper.clear();
        mDeviceListHelper.read();

        HistoryDeviceList.clear();
        mDeviceListHelper.queryAll().forEach(d ->
                HistoryDeviceList.add(new Device(-1, d.getName(), d.getSerial(), d.getMac(),
                        1 == d.getAbility() || 3 == d.getAbility(),
                        2 == d.getAbility() || 3 == d.getAbility(),
                        4 == d.getAbility() || 12 == d.getAbility(),
                        8 == d.getAbility() || 12 == d.getAbility(),
                        false))
        );
        HistoryDeviceList.forEach(d -> Log.d(TAG, "history   " + d));

        CarHelper.INSTANCE.setOnCarPowerStateListener(new CarHelper.OnCarPowerStateListener() {
            @Override
            public void standby() {
                Log.d(TAG, "standby() called");
                Log.d(TAG, "standby == " + CarHelper.isStandby());
                if (isSOPVersion) {
                    Log.d(TAG, "sop version not stop carplay");
                } else {
                    if (isCertifiedVersion) {
                        //Log.d(TAG, "certified version not stop carplay");
                        Log.d(TAG, "delay 20s stop carplay");
                        handler.sendEmptyMessageDelayed(4, 20000);
                    } else {
                        stopCarPlay();
                    }
                }
            }

            @Override
            public void run() {
                Log.d(TAG, "run() called");
                Log.d(TAG, "standby == " + CarHelper.isStandby());
                Log.d(TAG, "last device bt mac = " + lastDevice.BluetoothMac);
                Log.d(TAG, "last device serial = " + lastDevice.SerialNumber);
                Log.d(TAG, "handler remove msg 4");
                handler.removeMessages(4);
                if (isSOPVersion) {
                    Log.d(TAG, "sop version not start carplay");
                } else {
                    Log.d(TAG, "last device connect type is " + lastDevice.getLastConnectType());
                    if (lastDevice.getLastConnectType() == USB_CARPLAY) {
                        if (lastDevice.isAttached()) {
                            startCarPlay(lastDevice.SerialNumber, true);
                        }
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
                if (isConnectingCarPlay) {
                    Log.d(TAG, "isStartingCarPlay == true");
                    return;
                }
                if (CarHelper.isOpenAndroidAuto()) {
                    if (isIdleState()) {
                        if (isSwitchingSession) {
                            if (TextUtils.equals(btMac, switchingPhone.getMac())) {
                                Log.d(TAG, "idle state, switching device mac same as btMac, directly connect");
                                startWirelessAndroidAuto(btMac, 0);
                            }
                        } else {
                            if (mDeviceListHelper.query("", btMac) == null) {
                                onNotification(2, "", "", btMac, 2);//start or not now popup
                            } else {
                                Log.i(TAG, "find current " + FindCurrentAvailableByMac.get(btMac));
                                Log.i(TAG, "find pre     " + FindPreAvailableByMac.get(btMac));
                                Log.i(TAG, "old device not notification 2 popup, directly connect");
                                startWirelessAndroidAuto(btMac, 1);
                            }
                        }
                    } else {
                        Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(currentDevice));
                        Log.d(TAG, "FindInstanceIdByMac = " + FindInstanceIdByMac.get(btMac));
                        if (TextUtils.equals(currentDevice.getInstanceId(), FindInstanceIdByMac.get(btMac))) {
                            Log.d(TAG, "btMac same as current device mac");
                            return;
                        }

                        AtomicReference<String> name = new AtomicReference<>("");
                        aliveDeviceList.forEach(d -> {
                            if (TextUtils.equals(btMac, d.getMac())) {
                                name.set(d.getName());
                            }
                        });
                        if (isSwitchingSession) {
                            Log.d(TAG, "switching session do not display popup");
                            if (TextUtils.equals(btMac, switchingPhone.getMac())) {
                                Log.d(TAG, "non idle state, switching device mac same as btMac, directly connect");
                                startWirelessAndroidAuto(btMac, 1);
                            }
                        } else {
                            if (mDeviceListHelper.query("", btMac) == null) {
                                onNotification(1, name.get(), "", btMac, 2);//switch new session popup, yes or no
                            } else {
                                Log.d(TAG, "old device not notification 1 popup");
                            }
                        }
                    }
                }
            }

            @Override
            public void onWirelessConnectionFailure(String btMac) {
                super.onWirelessConnectionFailure(btMac);
                Log.d(TAG, "onWirelessConnectionFailure() called with: btMac = [" + btMac + "]");
                resetSwitchingSessionState();
                isConnectingWiFiAndroidAuto = false;
                Log.d(TAG, "onWirelessConnectionFailure() end");
            }

            @Override
            public void onUpdateWirelessDevice(AAWDeviceInfo device) {
                super.onUpdateWirelessDevice(device);
                Log.d(TAG, "onUpdateWirelessDevice: " + CommonUtilsKt.toJson(device));
                String id = device.getInstanceID();
                Log.d(TAG, "instanceId: " + id);
                if (device.getAvailable()) {
                    AvailableDeviceBtMacList.add(device.getMacAddress());
                } else {
                    AvailableDeviceBtMacList.remove(device.getMacAddress());
                }
                Log.d(TAG, "AvailableDeviceBtMacList = " + CommonUtilsKt.toJson(AvailableDeviceBtMacList));
                if (TextUtils.isEmpty(id) || TextUtils.equals("null", id)) {
                    Log.d(TAG, "instanceId is invalid value");
                } else {
                    FindInstanceIdByMac.put(device.getMacAddress(), id);
                    FindInstanceIdBySerial.put(device.getSerialNumber(), id);
                    FindSerialByInstanceId.put(id, device.getSerialNumber());
                    FindMacByInstanceId.put(id, device.getMacAddress());
                }

                if (!FindPreAvailableByMac.containsKey(device.getMacAddress())) {
                    FindPreAvailableByMac.put(device.getMacAddress(), false);
                } else {
                    FindPreAvailableByMac.put(device.getMacAddress(), FindCurrentAvailableByMac.get(device.getMacAddress()));
                }
                FindCurrentAvailableByMac.put(device.getMacAddress(), device.getAvailable());
                FindMacBySerial.put(device.getSerialNumber(), device.getMacAddress());
                if (CarHelper.isOpenAndroidAuto()) {
                    onWiFiDeviceStateUpdate(device, 2);
                }
            }

            @Override
            public void onUpdateMDBtMac(String id, String btMac) {
                super.onUpdateMDBtMac(id, btMac);
                Log.d(TAG, "onUpdateMDBtMac() called with: id = [" + id + "], btMac = [" + btMac + "]");
                Log.d(TAG, "currentDevice ConnectionType = " + currentDevice.getConnectionType());
                if (currentDevice.getConnectionType() == 1) {
                    if (TextUtils.isEmpty(id) || TextUtils.equals(id, "null") || TextUtils.isEmpty(btMac) || TextUtils.equals(btMac, "null")) {
                        return;
                    }
                    String serial = FindSerialByInstanceId.get(id);
                    Log.d(TAG, "serial = " + serial);
                    if (!TextUtils.isEmpty(serial)) {
                        Device device = new Device(1, currentDevice.getDeviceName(), currentDevice.getSerialNumber()
                                , currentDevice.getBluetoothMac(), true, false, false, false, true);
                        onDeviceUpdate(device);
                        aliveDeviceList.forEach(item -> {
                            if (TextUtils.equals(item.getSerial(), serial)) {
                                Log.d(TAG, "old alive device = " + new Gson().toJson(item));
                                item.setMac(btMac);
                                Log.d(TAG, "new alive device = " + new Gson().toJson(item));
                            }
                        });
                    }
                } else {
                    Log.d(TAG, "setBluetoothMac " + btMac);
                    currentDevice.setBluetoothMac(btMac);
                }
            }
        });

        mAapProxy = new AapBinderClient(this);
        mAapProxy.registerListener(mAapListener);

        mCarPlayClient = new CarPlayClient();
        mCarPlayClient.initialise(context);
        CarPlayListener carPlayListener = new CarPlayListener() {
            private int sessionType = -1;

            @Override
            public void onUpdateClientSts(boolean sts) {
                super.onUpdateClientSts(sts);
                Log.d(TAG, "onUpdateClientSts() called with: sts = [" + sts + "] , bind carplay service success");
                CAR_PLAY_BIND_SUCCESS = true;
                if (onUpdateClientStsListener != null) {
                    onUpdateClientStsListener.bindSuccess();
                }
            }

            @Override
            public void onSessionStsUpdate(int sts, String btMac, String deviceName) {
                super.onSessionStsUpdate(sts, btMac, deviceName);
                String serial = mCarPlayClient.getSerialNumber();
                Log.d(TAG, "onSessionStsUpdate() called with: sts = [" + sts + "], btMac = [" + btMac + "], deviceName = [" + deviceName + "], SerialNumber = [" + serial + "]");

                boolean isUsb = sessionType == 1;

                handler.removeMessages(3);
                handler.sendEmptyMessage(3);

                btMac = CacheHelperKt.toHexString(btMac);
                Log.d(TAG, "btMac toHexString == " + btMac);

                if (sts == 0) {
                    resetSwitchingSessionState();
                    CURRENT_SESSION = isUsb ? USB_CARPLAY : WIFI_CARPLAY;
                    CURRENT_CONNECT_STATE = STATE_CONNECTED;
                    currentDevice.update(serial, btMac, deviceName, CURRENT_SESSION);

                    onSessionStateUpdate(serial, btMac, sts, "connected");

                    CacheHelperKt.saveLastConnectDeviceInfo(mContext, deviceName, serial, btMac, isUsb ? 4 : 8);
                    mDeviceListHelper.write(deviceName, serial, btMac, isUsb ? 4 : 8);

                    lastDevice.SerialNumber = serial;
                    lastDevice.BluetoothMac = btMac;
                    Log.d(TAG, "last device bt mac = " + lastDevice.BluetoothMac);
                    Log.d(TAG, "last device serial = " + lastDevice.SerialNumber);
                    lastDevice.setLastConnectType(CURRENT_SESSION);

                } else if (sts == 1) {
                    onSessionStateUpdate(currentDevice.SerialNumber, currentDevice.BluetoothMac, sts, "disconnected");
                    if (isSOPVersion) {
                        USBKt.usbDeviceList(mContext).values().forEach(d -> Log.d(TAG, "attached usb device  " + d.getSerialNumber() + "  " + d.getProductName()));
                        if (USBKt.usbDeviceList(mContext).values().stream().anyMatch(AppSupport::isIOSDevice)) {
                            resetUsb();
                        }
                    }
                }
                isCanConnectingCPWifi = false;
            }

            @Override
            public void onNotifyCPReadyToAuth(String uniqueInfo, int connectType) {
                super.onNotifyCPReadyToAuth(uniqueInfo, connectType);
                Log.d(TAG, "onNotifyCPReadyToAuth() called with: uniqueInfo = [" + uniqueInfo + "], connectType = [" + connectType + "]");
                if (CarHelper.isStandby()) {
                    Log.e(TAG, "standby mode, not start carplay");
                    return;
                }
                if (!CarHelper.isOpenCarPlay()) {
                    return;
                }
                if (TextUtils.isEmpty(uniqueInfo)) {
                    Log.e(TAG, "illegal parameter");
                    return;
                }
                if (connectType != 1) {
                    isCanConnectingCPWifi = true;
                    handler.removeCallbacks(runnable);
                    Log.d(TAG, "after 30 seconds, isCanConnectingCPWifi = false");
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
                    detachIOSDevice(serialNum);
                    if (CURRENT_SESSION == USB_CARPLAY) updateIdleState();
                    handler.removeMessages(3);
                    handler.sendEmptyMessage(3);
                }
                isCanConnectingCPWifi = false;
                lastDevice.setAttached(isDeviceAttached);
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
                if (CarHelper.isOpenCarPlay()) {
                    onWiFiDeviceStateUpdate(aawDeviceInfo, 4);
                }
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

    public static boolean currentSessionIsWifiAndroidAuto() {
        return CURRENT_SESSION == WIFI_ANDROID_AUTO;
    }

    public static boolean currentSessionIsWifiCarPlay() {
        return CURRENT_SESSION == WIFI_CARPLAY;
    }

    public boolean isNotSwitchingSession() {
        Log.d(TAG, "isSwitchingSession == " + isSwitchingSession);
        return !isSwitchingSession;
    }

    public void setOnUpdateClientStsListener(OnUpdateClientStsListener onUpdateClientStsListener) {
        this.onUpdateClientStsListener = onUpdateClientStsListener;
    }

    public void removeResetUsbMessages() {
        Log.d(TAG, "removeResetUsbMessages() called");
        handler.removeMessages(2);
    }

    private void resetUsb() {
        Log.d(TAG, "sys usb otg power 0");
        SystemProperties.set("sys.usbotg.power", "0");
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        Log.d(TAG, "sys usb otg power 1");
        SystemProperties.set("sys.usbotg.power", "1");
    }

    private void resetUsb(boolean autoConnect) {
        Log.d(TAG, "resetUsb() called with: autoConnect = [" + autoConnect + "]");
        int attached = USBKt.usbDeviceList(mContext).size();
        Log.d(TAG, "attached usb device size = " + attached);
        if (attached > 0) {
            if (!autoConnect) {
                handler.sendEmptyMessage(0);
            }
            isResettingUsb = true;
            Log.d(TAG, "isResettingUsb = true");
            resetUsb();
            handler.sendEmptyMessageDelayed(2, 4000);
            Log.d(TAG, "after 3 second , isResettingUsb value will be reset");
            if (!autoConnect) {
                handler.sendEmptyMessageDelayed(1, 3000);
            }
        } else {
            Log.d(TAG, "current no android auto usb device , do not need reset usb");
        }
    }

    private void onNotification(int id) {
        Log.d(TAG, "onNotification() called with: id = [" + id + "]" + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener != null) {
                    listener.onNotification(id, "", "", "", 0);
                } else {
                    Log.d(TAG, "listener == null ");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onNotification: ", e);
            }
        }
    }

    public boolean isAutoConnectUsbAndroidAuto() {
        Log.d(TAG, "autoConnectUsbAndroidAuto == " + autoConnectUsbAndroidAuto);
        return autoConnectUsbAndroidAuto;
    }

    public void setAutoConnectUsbAndroidAuto(boolean autoConnectUsbAndroidAuto) {
        Log.d(TAG, "setAutoConnectUsbAndroidAuto() called with: autoConnectUsbAndroidAuto = [" + autoConnectUsbAndroidAuto + "]");
        this.autoConnectUsbAndroidAuto = autoConnectUsbAndroidAuto;
    }

    public AapBinderClient getAapBinderClient() {
        return mAapProxy;
    }

    public boolean canConnectUsbCarPlay() {
        if (canConnectUsbCarPlay) {
            return true;
        } else {
            Log.e(TAG, "AuthType != 1 && state != 15 , can not connect usb carplay");
            return false;
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        this.aliveDeviceList = deviceList;
    }

    public void setUsbHostController(UsbHostController controller) {
        this.mUsbHostController = controller;
    }

    public void stopLastSession() {
        Log.d(TAG, "stopLastSession() called");
        switch (CURRENT_SESSION) {
            case USB_ANDROID_AUTO:
            case WIFI_ANDROID_AUTO:
                stopAndroidAuto();
                break;
            case USB_CARPLAY:
            case WIFI_CARPLAY:
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
        Log.d(TAG, "stopLastSession end");
    }

    private void onSessionStateUpdate(String serial, String mac, int state, String msg) {
        Log.d(TAG, "onSessionStateUpdate() called with: serial = [" + serial + "], mac = [" + mac + "], state = [" + state + "], msg = [" + msg + "]"
                + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener != null) {
                    listener.onSessionStateUpdate(serial, mac, state, msg);
                } else {
                    Log.d(TAG, "listener == null ");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onSessionStateUpdate: ", e);
            } finally {
                if (state == 1) {
                    if (!isIdleState()) {
                        updateIdleState();
                    }
                }
            }
        }
        Log.d(TAG, "onSessionStateUpdate() end");
    }

    public void onNotification(int id, String content, String serial, String mac, int connectType) {
        Log.d(TAG, "onNotification() called with: id = [" + id + "], content = [" + content + "], serial = [" + serial + "], mac = [" + mac + "], connectType = [" + connectType + "]" + ", OnConnectListener size == " + mOnConnectListeners.size());

        if (CarPowerState.PWR_MODE_PROTECTION || CarPowerState.PWR_SCREEN_ON) {
            CarHelper.INSTANCE.sendPROModeExit();
        }

        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener != null) {
                    listener.onNotification(id, content, serial, mac, connectType);
                } else {
                    Log.d(TAG, "listener == null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        }
        Log.d(TAG, "onNotification() end");
    }

    private void updateSwitchingSessionState(String serial, String mac) {
        Log.d(TAG, "updateSwitchingSessionState() called with: serial = [" + serial + "], mac = [" + mac + "]");
        isSwitchingSession = true;
        handler.postDelayed(SwitchingEndRunnable, 60000);
        switchingPhone.update(serial, mac);
    }

    public void resetSwitchingSessionState() {
        Log.d(TAG, "resetSwitchingSessionState() called");
        if (isSwitchingSession) {
            handler.removeCallbacks(SwitchingEndRunnable);
            handler.postDelayed(SwitchingEndRunnable, 0);
        }
    }

    /**
     * @param connectType 1:usb_aa , 2:wireless_aa , 3:usb_cp , 4:wireless_cp
     * @param serial      usb device serial number
     * @param mac         blue tooth mac
     */
    public void switchSession(int connectType, String serial, String mac) {
        Log.d(TAG, "switchSession() called with: connectType = [" + connectType + "], serial = [" + serial + "], mac = [" + mac + "]");
        if (isConnectingCarPlay && (connectType == WIFI_ANDROID_AUTO || connectType == USB_ANDROID_AUTO)) {
            Log.d(TAG, "isConnectingCarPlay is true, not allow switch android auto");
            return;
        }
        if (connectType == 1 && currentDevice.ConnectionType == 1 && TextUtils.equals(serial, currentDevice.SerialNumber)) {
            Log.d(TAG, "same as android auto usb device, not switch");
            return;
        }
        if (connectType == 3 && currentDevice.ConnectionType == 3 && TextUtils.equals(serial, currentDevice.SerialNumber)) {
            Log.d(TAG, "same as carplay usb device, not switch");
            return;
        }
        if (ObjectsCompat.equals(currentDevice.SerialNumber, serial) && !TextUtils.isEmpty(serial) && !TextUtils.isEmpty(currentDevice.SerialNumber)
                || ObjectsCompat.equals(currentDevice.BluetoothMac, mac) && !TextUtils.isEmpty(mac) && !TextUtils.isEmpty(currentDevice.BluetoothMac)) {
            Log.d(TAG, "same as current session, do not switch");
            Log.d(TAG, currentDevice.toString());
            return;
        }
        if (isSwitchingSession) {
            if (TextUtils.equals(serial, switchingPhone.getSerial()) || TextUtils.equals(mac, switchingPhone.getMac())) {
                onSessionStateUpdate(serial, mac, -1, "switching");
            } else {
                onSessionStateUpdate(serial, mac, -2, "busy");
            }
            return;
        } else {
            isSwitchingSession = true;
        }
        updateSwitchingSessionState(serial, mac);
        stopLastSession();
        connectSession(connectType, serial, mac);
    }

    public void setOnConnectListener(List<OnConnectListener> mOnConnectListeners) {
        this.mOnConnectListeners = mOnConnectListeners;
    }

    /**
     * click bluetooth device list item
     */
    public void switchSession(String serial, String mac) {
        Log.d(TAG, "switchSession() called with: serial = [" + serial + "], mac = [" + mac + "]");
        if (TextUtils.equals(currentDevice.BluetoothMac, mac) && !TextUtils.isEmpty(mac)) {
            Log.d(TAG, "same as current session bluetooth mac address");
            return;
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
        updateSwitchingSessionState(serial, mac);
        stopLastSession();
        Log.d(TAG, "switchSession go on");
        int type = 0;
        for (Device device : aliveDeviceList) {
            if (Objects.equals(device.getMac(), mac)) {
                type = (device.isWirelessAA() || device.isUsbAA()) ? 2 : 4;
            }
            if (TextUtils.isEmpty(mac)) {
                if (TextUtils.isEmpty(serial)) {
                    Log.d(TAG, "switchSession interrupt");
                    return;
                } else {
                    type = 1;
                }
            }
        }
        if (aliveDeviceList.stream().anyMatch(device -> Objects.equals(device.getMac(), mac) || Objects.equals(device.getSerial(), serial))) {
            connectSession(type, serial, mac);
        } else {
            if (TextUtils.isEmpty(mac)) return;
            if (mACLConnectedList.contains(mac)) {
                Log.d(TAG, "ACLConnectedList: " + new Gson().toJson(mACLConnectedList));
                onNotification(-5);
                resetSwitchingSessionState();
            } else {
                for (OnConnectListener l : mOnConnectListeners) {
                    try {
                        Log.d(TAG, "onRequestBluetoothPair " + mac);
                        if (l != null) {
                            l.onRequestBluetoothPair(mac);
                        } else {
                            Log.d(TAG, "listener == null ");
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "switchSession: ", e);
                    }
                }
            }
        }
    }

    private void detachIOSDevice(String serialNumber) {
        Device device = null;
        for (Device d : aliveDeviceList) {
            if (Objects.equals(d.getSerial(), serialNumber)) {
                device = new Device(d.getType(), d.getName(), d.getSerial(), d.getMac(), d.isUsbAA(), d.isWirelessAA(), d.isUsbCP(), d.isWirelessCP(), false);
            }
        }
        if (device != null) onDeviceUpdate(device);

        Log.d(TAG, "remove carplay usb alive device  " + serialNumber);

        aliveDeviceList.removeIf(d -> Objects.equals(d.getSerial(), serialNumber) && d.getType() == 1);

        aliveDeviceList.forEach(item -> Log.d(TAG, "alive  ————  " + item.toString()));
    }

    /**
     * @param type 2:wireless_aa , 4:wireless_cp
     */
    private void onWiFiDeviceStateUpdate(AAWDeviceInfo aawDeviceInfo, int type) {
        Log.d(TAG, "onWiFiDeviceStateUpdate() called with: mac = [" + aawDeviceInfo.getMacAddress() + "], type = [" + type + "]");
        Device device = new Device();
        device.setType(2);//1:usb , 2:wireless
        device.setName(aawDeviceInfo.getDeviceName());
        device.setSerial(aawDeviceInfo.getSerialNumber());
        device.setMac(aawDeviceInfo.getMacAddress());
        if (type == 2) {
            device.setWirelessAA(aawDeviceInfo.getCapability() == 16 || aawDeviceInfo.getCapability() == 17);
        } else if (type == 4) {
            device.setWirelessCP(true);
        }
        device.setAvailable(aawDeviceInfo.getAvailable());

        if (aawDeviceInfo.getAvailable()) {
            if (aliveDeviceList.stream().anyMatch(d -> d.getType() == 2 && Objects.equals(d.getMac(), device.getMac()))) {
                Log.d(TAG, "already contained  device " + device.getMac());
            } else {
                Log.d(TAG, "add wifi alive " + device);
                aliveDeviceList.add(device);
            }
        } else {
            Log.d(TAG, "remove wifi alive device  " + device.getMac());
            aliveDeviceList.removeIf(d -> Objects.equals(d.getMac(), device.getMac()) && d.getType() == 2);
        }
        aliveDeviceList.forEach(item -> Log.d(TAG, "alive  ————  " + item.toString()));
        onDeviceUpdate(device);
    }

    /**
     * @param mac    bluetooth address
     * @param reason USER_REQUEST_VALUE = 0 (user select) ; AUTO_LAUNCH_VALUE = 1 (boot auto reconnect old device) ; AUTOMATIC_RESTART_VALUE = 2 (no identify exception disconnect, auto reconnect)
     */
    public void startWirelessAndroidAuto(String mac, int reason) {
        Log.i(TAG, "startWirelessAndroidAuto() called with: mac = [" + mac + "], reason = [" + reason + "]");
        if (isConnectingCarPlay) {
            Log.d(TAG, "isStartingCarPlay is true, not allow start android auto");
            return;
        }
        if (TextUtils.isEmpty(mac)) return;
        if (AvailableDeviceBtMacList.contains(mac)) {
            isConnectingWiFiAndroidAuto = true;
            Log.i(TAG, "AndroidAutoDeviceClient ConnectWirelessDevice");
            mAndroidAutoDeviceClient.ConnectWirelessDevice(mac, reason);
            updateSwitchingSessionState("", mac);
        } else {
            Log.i(TAG, "device not available, can not connect wifi android auto");
            if (reason == 0) resetSwitchingSessionState();
        }
    }

    public void startUsbAndroidAuto(String deviceName, String serial) {
        Log.d(TAG, "startUsbAndroidAuto() called with: deviceName = [" + deviceName + "], serial = [" + serial + "]");
        if (isIdleState() || isSwitchingAOAState()) {
            if (isSwitchingSession) {
                if (Objects.equals(serial, switchingPhone.getSerial())) {
                    Log.d(TAG, "attached device serial same as switching device serial , auto connect");
                    mAapProxy.startAndroidAuto(deviceName);
                } else {
                    Log.d(TAG, "isSwitchingSession = true, not start usb android auto");
                }
            } else {
                mAapProxy.startAndroidAuto(deviceName);
            }
        }
    }

    public void stopUsbAndroidAuto() {
        if (CURRENT_SESSION == USB_ANDROID_AUTO) {
            Log.d(TAG, "stopUsbAndroidAuto() called");
            mAapProxy.stopAndroidAuto();
        } else {
            Log.d(TAG, CURRENT_SESSION + " isn't usb android auto, not need stop");
        }
    }

    public void stopAndroidAuto() {
        if (CURRENT_SESSION == USB_ANDROID_AUTO) {
            Log.d(TAG, "stop usb AndroidAuto ");
            mAapProxy.stopAndroidAuto();
        } else if (CURRENT_SESSION == WIFI_ANDROID_AUTO) {
            Log.d(TAG, "AndroidAutoDeviceClient.DisconnectWirelessDevice()");
            mAndroidAutoDeviceClient.DisconnectWirelessDevice();
        } else {
            Log.d(TAG, CURRENT_SESSION + " isn't android auto, not need stop");
        }
    }

    public void connectSession(int type, String serial, String mac) {
        Log.d(TAG, "connectSession() called with: type = [" + type + "], serial = [" + serial + "], mac = [" + mac + "]");
        if (!isIdleState()) return;
        if (type == 1 || type == 3) {
            if (isReplugged) {
                Log.d(TAG, "connectSession: UsbHostController.attach");
                mUsbHostController.attach(USBKt.queryUsbDevice(mContext, serial));
            } else {
                Log.d(TAG, "isReplugged = false");
                onNotification(isReplugged_id);
            }
        } else if (type == 2) {
            if (TextUtils.isEmpty(mac)) {
                Log.d(TAG, "mac == " + mac);
                return;
            }
            startWirelessAndroidAuto(mac, 0);
        } else if (type == 4) {
            if (TextUtils.isEmpty(mac)) {
                Log.d(TAG, "mac == " + mac);
                return;
            }
            startCarPlay(mac, false);
        }
    }

    public void stopCarPlay() {
        if (carPlayProxyValid()) {
            if (CURRENT_SESSION == USB_CARPLAY || CURRENT_SESSION == WIFI_CARPLAY) {
                Log.d(TAG, "CarPlayClient.stopSession()");
                mCarPlayClient.stopSession();
            } else {
                Log.d(TAG, "current session isn't carplay, no need stop");
            }
        }
    }

    private boolean carPlayProxyValid() {
        Log.d(TAG, "CAR_PLAY_BIND_SUCCESS == " + CAR_PLAY_BIND_SUCCESS);
        return CAR_PLAY_BIND_SUCCESS;
    }

    public boolean isIdleState() {
        boolean idle = (CURRENT_CONNECT_STATE == STATE_IDLE && CURRENT_SESSION == NULL) && !isConnectingCarPlay;
        if (!idle) {
            Log.e(TAG, "current connect state is " + CURRENT_CONNECT_STATE);
            Log.e(TAG, "current session state is " + CURRENT_SESSION);
            Log.e(TAG, "isConnectingCarPlay == " + isConnectingCarPlay);
        }
        return idle;
    }

    public boolean isPreParingState() {
        return CURRENT_CONNECT_STATE == STATE_PREPARING;
    }

    public boolean isSwitchingAOAState() {
        return CURRENT_CONNECT_STATE == STATE_AOA_SWITCHING;
    }

    public void onDeviceUpdate(Device device) {
        Log.d(TAG, "onDeviceUpdate() called with: " + device.toString() + ", OnConnectListener size == " + mOnConnectListeners.size());
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener != null) {
                    listener.onDeviceUpdate(device);
                } else {
                    Log.d(TAG, "listener == null ");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onDeviceUpdate: ", e);
            }
        }
        Log.d(TAG, "onDeviceUpdate() end");
    }

    public void roleSwitchComplete(String serialNumber) {
        Log.d(TAG, "roleSwitchComplete() called with: serialNumber = [" + serialNumber + "]");
        if (isConnectingWiFiAndroidAuto) {
            Log.e(TAG, "wifi android auto is connecting");
            return;
        }
        if (CAR_PLAY_BIND_SUCCESS) {
            mCarPlayClient.roleSwitchComplete(serialNumber);
        } else {
            Log.d(TAG, "delay 1 second, again role switch");
            handler.postDelayed(() -> {
                Log.d(TAG, "roleSwitchComplete: CarPlayBindSuccess == " + CAR_PLAY_BIND_SUCCESS);
                mCarPlayClient.roleSwitchComplete(serialNumber);
            }, 1000);
        }
        isConnectingCarPlay = true;
        Log.d(TAG, "isStartingCarPlay = true");
        handler.sendEmptyMessageDelayed(3, 30000);
        Log.d(TAG, "30s connecting protect start");
    }

    public void startCarPlay(String uniqueInfo, boolean isUSB) {
        Log.d(TAG, "startCarPlay() called with: uniqueInfo = [" + uniqueInfo + "], isUSB = [" + isUSB + "]");
        if (TextUtils.isEmpty(uniqueInfo)) return;
        if (isIdleState() && carPlayProxyValid()) {
            if (isConnectingCarPlay) {
                Log.d(TAG, "isStartingCarPlay == true, do not again start carplay");
            } else {
                if (isSwitchingSession && (!Objects.equals(switchingPhone.getSerial(), uniqueInfo) && !Objects.equals(switchingPhone.getMac(), uniqueInfo))) {
                    Log.d(TAG, "current is switching, not start carplay");
                    return;
                }
                isConnectingCarPlay = true;
                Log.d(TAG, "isStartingCarPlay = true");
                handler.sendEmptyMessageDelayed(3, 30000);
                Log.w(TAG, "start carplay session");
                mCarPlayClient.startSession(uniqueInfo, isUSB);
            }
        }
    }

    public void updatePreparingState() {
        Log.d(TAG, "update session state to preparing");
        CURRENT_CONNECT_STATE = STATE_PREPARING;
    }

    public void updateSwitchingAOAState() {
        Log.d(TAG, "update session state to switching aoa mode");
        CURRENT_CONNECT_STATE = STATE_AOA_SWITCHING;
    }

    public void updateIdleState() {
        Log.d(TAG, "update session state to idle");
        CURRENT_SESSION = NULL;
        CURRENT_CONNECT_STATE = STATE_IDLE;
        isConnectingWiFiAndroidAuto = false;
        currentDevice.reset();
        Log.d(TAG, "updateIdleState out");
    }

    public void release() {
        Log.d(TAG, "release() called");
        handler.removeCallbacksAndMessages(null);
        mAapProxy.unregisterListener(mAapListener);

        if (mBlueToothBroadcastReceiver != null)
            mContext.unregisterReceiver(mBlueToothBroadcastReceiver);
    }

    private void onBlueToothBroadcast() {
        mBlueToothBroadcastReceiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                int retVal = 0;
                switch (action) {
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                        if (null != device) {
                            Log.d(TAG, "ACTION_BOND_STATE_CHANGED    " + device.getBondState() + "    " + device.getAddress());
                            if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                                mDeviceListHelper.read();
                                mDeviceListHelper.deleteByMac(device.getAddress());
                                mDeviceListHelper.read();

                                Log.d(TAG, "delete alive device");
                                aliveDeviceList.removeIf(d -> (d.isUsbAA() || d.isWirelessAA()) && Objects.equals(device.getAddress(), d.getMac()));

                                if (!TextUtils.isEmpty(device.getAddress())) {
                                    if (TextUtils.equals(device.getAddress(), currentDevice.BluetoothMac)) {
                                        if (CURRENT_SESSION == WIFI_ANDROID_AUTO) {
                                            Log.d(TAG, "stop wifi android auto");
                                            mAndroidAutoDeviceClient.DisconnectWirelessDevice();
                                        } else if (CURRENT_SESSION == WIFI_CARPLAY) {
                                            Log.d(TAG, "stop wifi carplay");
                                            mCarPlayClient.stopSession();
                                        }
                                    }
                                    Log.d(TAG, "switchingPhone mac = " + switchingPhone.getMac());
                                    if (TextUtils.equals(device.getAddress(), switchingPhone.getMac())) {
                                        resetSwitchingSessionState();
                                    }
                                }
                            } else {
                                Log.d(TAG, "device bond state != BOND_NONE");
                            }
                        } else {
                            Log.d(TAG, "ACTION_BOND_STATE_CHANGED    device == null");
                        }
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        if (null != device) {
                            Log.d(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED   " + device.getAddress());
                        } else {
                            Log.d(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED    device == null");
                        }
                        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                            Log.d(TAG, "Bluetooth CarPlayState ON");
                        } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                            Log.d(TAG, "Bluetooth CarPlayState OFF");
                        }
                        break;
                    case BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED:
                        if (null != device) {
                            //Log.d(TAG, "BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED    " + device.getName() + "    " + device.getAddress());
                            if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                                Log.d(TAG, "HFP STATE CONNECTED " + device.getName() + " connected with address:" + device.getAddress());
                                if (!CarHelper.isOpenAndroidAuto() || !isIdleState()) return;
                                if (isSwitchingSession) {
                                    if (TextUtils.equals(switchingPhone.getMac(), device.getAddress())) {
                                        startWirelessAndroidAuto(device.getAddress(), 0);
                                    }
                                } else {
                                    startWirelessAndroidAuto(device.getAddress(), 0);
                                }
                            } else if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_DISCONNECTED) {
                                Log.d(TAG, "HFP STATE DISCONNECTED " + device.getName() + " disconnect with address:" + device.getAddress());
                            }
                        } else {
                            Log.d(TAG, "BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED    device == null");
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        if (null != device) {
                            Log.d(TAG, "BluetoothDevice.ACTION_ACL_CONNECTED    " + device.getName() + "    " + device.getAddress());
                            mACLConnectedList.add(device.getAddress());
                        } else {
                            Log.d(TAG, "BluetoothDevice.ACTION_ACL_CONNECTED    device == null");
                        }
                        Log.d(TAG, "mACLConnectedList = " + new Gson().toJson(mACLConnectedList));
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        if (null != device) {
                            Log.d(TAG, "BluetoothDevice.ACTION_ACL_DISCONNECTED    " + device.getName() + "    " + device.getAddress());
                            mACLConnectedList.remove(device.getAddress());
                        } else {
                            Log.d(TAG, "BluetoothDevice.ACTION_ACL_DISCONNECTED    device == null");
                        }
                        Log.d(TAG, "mACLConnectedList = " + new Gson().toJson(mACLConnectedList));
                        break;
                    default:
                        break;
                }
            }
        };
    }


    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
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








