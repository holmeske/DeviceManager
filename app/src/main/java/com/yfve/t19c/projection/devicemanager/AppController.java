package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static int CURRENT_CONNECT_STATE = 0;
    private static int CURRENT_SESSION_TYPE = 0;

    private final Object mLock = new Object();  // lock protect session status
    private final CarPlayClient mCarPlayClient;
    private final AapBinderClient mAapProxy;
    private final AndroidAutoDeviceClient mAndroidAutoDeviceClient;
    private final DeviceListController mDeviceListController;
    private final CarHelper mCarHelper;
    private final Context mContext;
    private boolean CAR_PLAY_BIND_SUCCESS = false;
    private DeviceInfo currentDevice = null;
    private List<OnConnectListener> mOnConnectListeners;
    private List<Device> deviceList = new ArrayList<>();
    private OnCallBackListener onCallBackListener;
    private UsbHostController mUsbHostController;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");
            Bundle bundle = intent.getExtras();
            if (bundle.containsKey("aa")) {
                String value = (String) bundle.get("aa");
                Log.d(TAG, "onReceive: " + bundle.get("aa"));
                if ("wifi0".equals(value)) {
                    //projectionScreen(1, "4d4e484d44563398", "");
                    mAndroidAutoDeviceClient.DisconnectWirelessDevice();
                } else if ("wifi1".equals(value)) {
                    //projectionScreen(2, "", "30:6A:85:15:1D:35");
                    mAndroidAutoDeviceClient.ConnectWirelessDevice("58:24:29:80:66:A0");//58:24:29:80:66:A0  30:6A:85:15:1D:35
                }
                if ("usb0".equals(value)) {
                    stopAndroidAuto();
                } else if ("usb1".equals(value)) {
                    /*UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
                    Class cls = usbManager.getClass();

                    for (Method method : cls.getDeclaredMethods()) {
                        Log.d(TAG, "onReceive: " + method.getName());
                        if (method.getName().equals("resetUsbGadget")) {
                            Log.d(TAG, "onReceive: hi hi hi , i am here");
                        }
                    }

                    Method method = null;
                    try {
                        method = cls.getMethod("resetUsbGadget");//
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                        Log.e(TAG, "", e);
                    }
                    try {
                        if (method == null) {
                            Log.d(TAG, "method = null");
                        } else {
                            method.invoke(usbManager, 1);
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                        Log.e(TAG, "", e);
                    }*/
                    UsbDevice device = USBKt.firstUsbDevice(mContext);
                    if (device == null) {
                        Log.d(TAG, "onReceive: connected usb device is null");
                        return;
                    }
                    startAndroidAuto(device.getDeviceName());
                } else if ("0".equals(value)) {
                    Log.d(TAG, "set property sys.usbotg.power 0");
                    SystemProperties.set("sys.usbotg.power", "0");
                    Log.d(TAG, "sys.usbotg.power = " + SystemProperties.get("sys.usbotg.power"));
                } else if ("1".equals(value)) {
                    Log.d(TAG, "set property sys.usbotg.power 1");
                    SystemProperties.set("sys.usbotg.power", "1");
                    Log.d(TAG, "sys.usbotg.power = " + SystemProperties.get("sys.usbotg.power"));
                } else if ("2".equals(value)) {
                    Log.d(TAG, "show dialog");
                    /*Toast.makeText(context.getApplicationContext(), "yes", Toast.LENGTH_LONG).show();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context.getApplicationContext());
                    builder.setTitle("提示");
                    builder.setMessage("connect new device ？");
                    builder.setIcon(R.mipmap.ic_launcher);
                    builder.setCancelable(true);
                    builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(context.getApplicationContext(), "yes", Toast.LENGTH_LONG).show();
                        }
                    });
                    builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    dialog.show();

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT);
                    params.gravity = Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;

                    Button mFloatingButton = new Button(mContext);
                    mFloatingButton.setText("Android Auto");
                    mFloatingButton.setVisibility(View.GONE);
                    WindowManager.LayoutParams fbtLp = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW + 26,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSPARENT
                    );
                    fbtLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                    WindowManager wm = (WindowManager) context.getApplicationContext().getSystemService(WINDOW_SERVICE);
                    wm.getDefaultDisplay().getRealMetrics(new DisplayMetrics());
                    wm.addView(mFloatingButton, params);*/
                }
            } else if (bundle.containsKey("cp")) {
                String value = (String) bundle.get("cp");
                Log.d(TAG, "onReceive: " + bundle.get("cp"));
                switch (value) {
                    case "usb":
                        switchSession(3, "d9b03f3b065e47605057ad57a2b8cf8dc71459d8", "");
                        break;
                    case "wifi":
                        switchSession(4, "", "B8:63:4D:11:8A:4C");
                        break;
                    case "stop":
                        stopCarPlay();
                        break;
                }
            }
        }
    };
    private boolean canConnectUSB = true;

    public AppController(Context context, DeviceListController deviceListController, CarHelper carHelper) {
        Log.d(TAG, "AppController() called");
        this.mContext = context;

//        IntentFilter intentFilter = new IntentFilter();
//        intentFilter.addAction("com.klv.test");
//        context.registerReceiver(receiver, intentFilter);

        this.mDeviceListController = deviceListController;
        this.mCarHelper = carHelper;

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
                Log.d(TAG, "onUpdateWirelessDevice() called with: mac = [" + device.getMacAddress() + "]"
                        + ", name = [" + device.getDeviceName() + "]" + ", serial = [" + device.getSerialNumber() + "]"
                        + ", type = [" + device.getDeviceType() + "]" + ", capability = [" + device.getCapability() + "]"
                        + ", available = [" + device.getAvailable() + "]");
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
            }

            @Override
            public void sessionTerminated(boolean b, int reason) {
                Log.d(TAG, "sessionTerminated() called with: isUsb = [" + b + "], reason = [" + reason + "]");
                synchronized (mLock) {
                    CURRENT_SESSION_TYPE = TYPE_NO_SESSION;
                    updateIdleState();
                }

                String usb = SystemProperties.get("sys.usbotg.power");
                Log.d(TAG, "sys.usbotg.power = " + usb);
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
                    } else if (sts == 1) {
                        CURRENT_SESSION_TYPE = TYPE_NO_SESSION;
                        updateIdleState();
                    }
                }
            }

            @Override
            public void onNotifyCPReadyToAuth(String uniqueInfo, int connectType) {
                super.onNotifyCPReadyToAuth(uniqueInfo, connectType);
                Log.d(TAG, "onNotifyCPReadyToAuth() called with: uniqueInfo = [" + uniqueInfo + "], connectType = [" + connectType + "]");
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
                    mDeviceListController.addUsbDeviceToList(serialNum);
                } else {
                    mDeviceListController.removeUsbDeviceFromList(serialNum);
                    noticeExternal(serialNum);
                    CURRENT_SESSION_TYPE = TYPE_NO_SESSION;
                    updateIdleState();
                }
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
                if (AuthType == 1) {

                } else {
                    if (state != 15) {
                        canConnectUSB = false;
                    }
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
        this.deviceList = deviceList;
    }

    public void setUsbHostController(UsbHostController controller) {
        this.mUsbHostController = controller;
    }

    /**
     * @param connectType  1:usb_aa , 2:wireless_aa , 3:usb_cp , 4:wireless_cp
     * @param serialNumber usb device serial number
     * @param btMac        blue tooth mac
     */
    public void switchSession(int connectType, String serialNumber, String btMac) {
        Log.d(TAG, "switchSession() called with: connectType = [" + connectType + "], serialNumber = [" + serialNumber + "], btMac = [" + btMac + "]");
        if (!sessionNotExist()) {
            return;
        }
//        if (CURRENT_SESSION_TYPE == TYPE_USB_ANDROID_AUTO) {
//            if (connectType == 1) {
//                Log.d(TAG, "same as the current session type, do not switch");
//                return;
//            } else {
//                stopAndroidAuto();
//            }
//        } else if (CURRENT_SESSION_TYPE == TYPE_WIFI_ANDROID_AUTO) {
//            if (connectType == 2) {
//                Log.d(TAG, "same as the current session type, do not switch");
//                return;
//            } else {
//                if (mAndroidAutoDeviceClient != null) {
//                    Log.d(TAG, "disconnect wireless android auto");
//                    mAndroidAutoDeviceClient.DisconnectWirelessDevice();
//                } else {
//                    Log.d(TAG, "AndroidAutoDeviceClient is null");
//                }
//            }
//        } else if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY) {
//            if (connectType == 3) {
//                Log.d(TAG, "same as the current session type, do not switch");
//                return;
//            } else {
//                stopCarPlay();
//            }
//        } else if (CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
//            if (connectType == 4) {
//                Log.d(TAG, "same as the current session type, do not switch");
//                return;
//            } else {
//                stopCarPlay();
//            }
//        }
//
//        int cnt = 0;
//        while (cnt < 10) {
//            if (CURRENT_CONNECT_STATE == STATE_IDLE) break;
//            Log.i(TAG, "current connect state is not idle, try to refresh");
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            cnt++;
//        }
        connectSession(connectType, serialNumber, btMac);
    }

    private void connectSession(int type, String serial, String mac) {
        Log.d(TAG, "connectSession() called with: type = [" + type + "], serial = [" + serial + "], mac = [" + mac + "]");
        if (type == 1) {
            if (mUsbHostController != null) {
                UsbDevice device = USBKt.queryUsbDevice(mContext, serial);
                mUsbHostController.attach(device);
            }
        } else if (type == 2) {
            mAndroidAutoDeviceClient.ConnectWirelessDevice(mac);
        } else if (type == 3) {
            if (currentSessionIsCarPlay()) {
                UsbDevice device = USBKt.firstUsbDevice(mContext);
                if (device == null) {
                    Log.d(TAG, "connectSession: first usb device is null");
                    return;
                }
                mUsbHostController.attach(device);
            } else {
                startCarPlay(serial, true);
            }
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
        for (Device d : deviceList) {
            if (Objects.equals(d.getSerial(), serialNumber)) {
                device = new Device(d.getType(), d.getName(), d.getSerial(), d.getMac(), d.isUsbAA(), d.isWirelessAA(), d.isUsbCP(), d.isWirelessCP(), false);
            }
        }
        Log.d(TAG, "update usb " + device);
        Device finalDevice = device;
        deviceList.removeIf(d -> Objects.equals(d.getSerial(), finalDevice == null ? "" : finalDevice.getSerial()));

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

        for (int i = 0; i < deviceList.size(); i++) {
            Log.d(TAG, "device " + (i + 1) + " : " + deviceList.get(i));
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
        deviceList.forEach(d -> {
            if (ObjectsCompat.equals(d.getMac(), device.getMac())) {
                isContain.set(true);
            }
        });
        if (aawDeviceInfo.getAvailable()) {
            if (!isContain.get()) {
                deviceList.add(device);
            } else {
                Log.d(TAG, "list already contains the device, do not add");
            }
        } else {
            deviceList.removeIf(d -> Objects.equals(d.getMac(), device.getMac()));
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

        for (int i = 0; i < deviceList.size(); i++) {
            Log.d(TAG, "device list " + (i + 1) + " : " + deviceList.get(i));
        }
    }

    public DeviceInfo getCurrentDevice() {
        return currentDevice;
    }

    public void setCurrentDevice(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "setCurrentDevice() called with: device = [" + null + "]");
            this.currentDevice = null;
        } else {
            Log.d(TAG, "setCurrentDevice() called with: serial = [" + device.getSerialNumber() + "], name = [" + device.getProductName() + "]");
            this.currentDevice = new DeviceInfo(device.getSerialNumber(), device.getDeviceName(), DeviceInfo.ConnectType_USB);
        }
    }

    public boolean isPresentAndroidAuto() {
        return mCarHelper.isOpenAndroidAuto();
    }

    public boolean isPresentCarPlay() {
        return mCarHelper.isOpenCarPlay();
    }

    public void startAndroidAuto(String deviceName) {
        Log.d(TAG, "startAndroidAuto() called with: deviceName = [" + deviceName + "]");
        if (sessionNotExist()) {
            if (androidAutoProxyValid()) {
                mAapProxy.startSession(deviceName);
            }
        }
    }

    public boolean deviceSame(UsbDevice device) {
        if (currentDevice != null && device != null && Objects.equals(currentDevice.SerialNumber, device.getSerialNumber())) {
            Log.d(TAG, "current device == intent device");
            return true;
        } else {
            Log.d(TAG, "current device != intent device ,"
                    + " current device = " + (currentDevice == null ? null : currentDevice.SerialNumber + ", " + currentDevice.DeviceName)
                    + " , intent  device = " + (device == null ? null : device.getSerialNumber() + ", " + device.getDeviceName()));
            return false;
        }
    }

    public void stopAndroidAuto() {
        updateDisConnectingState();
        if (androidAutoProxyValid()) {
            printCurrentSessionType();
            if (CURRENT_SESSION_TYPE == TYPE_USB_ANDROID_AUTO) {
                mAapProxy.stopSession();
            } else {
                Log.d(TAG, "current session is not android auto");
            }
        }
        updateIdleState();
    }

    private void printCurrentSessionType() {
        switch (CURRENT_SESSION_TYPE) {
            case TYPE_USB_ANDROID_AUTO:
                Log.d(TAG, "current session is usb android auto");
                break;
            case TYPE_WIFI_ANDROID_AUTO:
                Log.d(TAG, "current session is wifi android auto");
                break;
            case TYPE_USB_CAR_PLAY:
                Log.d(TAG, "current session is usb carplay");
                break;
            case TYPE_WIFI_CAR_PLAY:
                Log.d(TAG, "current session is wifi carplay");
                break;
        }
    }

    public void roleSwitchComplete(String serialNumber) {
        Log.d(TAG, "roleSwitchComplete() called with: serialNumber = [" + serialNumber + "]");
        if (CAR_PLAY_BIND_SUCCESS) {
            mCarPlayClient.roleSwitchComplete(serialNumber);
        } else {
            Log.d(TAG, "roleSwitchComplete: delay 1 second ");
            new Handler().postDelayed(() -> {
                Log.d(TAG, "roleSwitchComplete: CarPlayBindSuccess is " + CAR_PLAY_BIND_SUCCESS);
                mCarPlayClient.roleSwitchComplete(serialNumber);
            }, 1000);
        }
    }

    public void startCarPlay(String btMac, boolean isUSB) {
        Log.d(TAG, "startCarPlay() called with: btMac = [" + btMac + "], isUSB = [" + isUSB + "]");
        if (btMac == null) return;
        if (sessionNotExist()) {
            if (carPlayProxyValid()) {
                if (isConnectingState()) {
                    Log.d(TAG, "don't start carplay , because current state is connecting");
                } else {
                    Log.w(TAG, "start carplay session");
                    updateConnectingState();
                    mCarPlayClient.startSession(btMac, isUSB);
                }
            }
        }
    }

    public void stopCarPlay() {
        updateDisConnectingState();
        if (carPlayProxyValid()) {
            if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY || CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
                Log.w(TAG, "carplay stopSession() called");
                mCarPlayClient.stopSession();
            } else {
                Log.d(TAG, "current session is not carplay");
            }
        }
        updateIdleState();
    }

    public boolean sessionNotExist() {
        if (CURRENT_SESSION_TYPE == TYPE_NO_SESSION) {
            Log.d(TAG, "session is idle state");
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
            Log.d(TAG, "android auto proxy invalid");
            return false;
        }
    }

    private boolean carPlayProxyValid() {
        if (CAR_PLAY_BIND_SUCCESS) {
            return true;
        } else {
            Log.d(TAG, "carplay proxy invalid");
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

    public boolean isConnectingState() {
        return CURRENT_CONNECT_STATE == STATE_CONNECTING;
    }

    public void updatePreparingState() {
        CURRENT_CONNECT_STATE = STATE_PREPARING;
        Log.d(TAG, "current state is preparing");
    }

    public void updateSwitchingState() {
        CURRENT_CONNECT_STATE = STATE_SWITCHING;
        Log.d(TAG, "current state is switching");
    }

    public void updateConnectingState() {
        CURRENT_CONNECT_STATE = STATE_CONNECTING;
        Log.d(TAG, "current state is connecting");
    }

    public void updateDisConnectingState() {
        CURRENT_CONNECT_STATE = STATE_DISCONNECTING;
        Log.d(TAG, "current state is disconnecting");
    }

    public void updateIdleState() {
        CURRENT_CONNECT_STATE = STATE_IDLE;
        currentDevice = null;

        Log.d(TAG, "current state is idle , current device is null");
    }

    public boolean currentSessionIsCarPlay() {
        if (CURRENT_SESSION_TYPE == TYPE_USB_CAR_PLAY || CURRENT_SESSION_TYPE == TYPE_WIFI_CAR_PLAY) {
            Log.d(TAG, "current session is carplay");
            return true;
        } else {
            Log.d(TAG, "current session is not carplay");
            return false;
        }
    }

    public void updateUsbAvailableDevice(String serial, String btmac, boolean available) {
        Log.d(TAG, "updateUsbAvailableDevice() called with: serial = [" + serial + "], btmac = [" + btmac + "], available = [" + available + "]");
        mAndroidAutoDeviceClient.updateUsbDevice(serial, btmac, available);
    }

}
