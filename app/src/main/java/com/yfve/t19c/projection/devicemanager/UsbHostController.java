package com.yfve.t19c.projection.devicemanager;

import static com.yfve.t19c.projection.devicemanager.AppController.isCertifiedVersion;
import static com.yfve.t19c.projection.devicemanager.AppController.isConnectingCarPlay;
import static com.yfve.t19c.projection.devicemanager.AppController.isReplugged;
import static com.yfve.t19c.projection.devicemanager.AppController.isResettingUsb;
import static com.yfve.t19c.projection.devicemanager.constant.DM.AliveDeviceList;
import static com.yfve.t19c.projection.devicemanager.constant.DM.LAST_ANDROID_AUTO_DEVICE_SERIAL;
import static com.yfve.t19c.projection.devicemanager.constant.DM.LAST_ANDROID_AUTO_SESSION_TERMINATED_REASON;
import static com.yfve.t19c.projection.devicemanager.constant.DM.OnConnectListenerList;
import static com.yfve.t19c.projection.devicemanager.constant.DM.ProbedAndroidAutoUsbDeviceSerialNumberSet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import com.google.gson.Gson;
import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;

import java.util.Iterator;
import java.util.Objects;

public class UsbHostController {
    private static final String TAG = "UsbHostController";
    private final Context mContext;
    private final AppController mAppController;
    private final DeviceHandlerResolver mDeviceHandlerResolver;
    private final int AOAPSwitchTimeout = 2000;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");

            if (UsbHelper.Companion.isBluePort()) return;

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                attach(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                isReplugged = true;
                detach(device);
            }
        }
    };

    private boolean isBoundIAapReceiverService = false;
    private boolean isBoundCarPlayService = false;
    private boolean isGetCarServiceValue = false;
//    private String currentAOAPSwitchSerialNumber;
//    private final Runnable mAOAPSwitchTimeoutRunnable = () -> {
//        Log.d(TAG, "2s aoa switch time out");
//        AOAPSwitchTimeoutSerialNumberSet.add(currentAOAPSwitchSerialNumber);
//    };

    public UsbHostController(Context mContext, AppController mAppController) {
        Log.d(TAG, "UsbHostController() called");

        this.mContext = mContext;
        this.mAppController = mAppController;

        mDeviceHandlerResolver = new DeviceHandlerResolver(mContext);
        registerReceiver();

        //Log.d(TAG, "LastConnectDeviceInfo = " + new Gson().toJson(getLastConnectDeviceInfo(mContext)));

        USBKt.usbDeviceList(mContext).values().forEach(d -> Log.d(TAG, "attached usb device ------ " + CommonUtilsKt.toJson(d)));

        if (mAppController.getAapBinderClient() != null) {
            Log.e(TAG, "Waiting for the callback that binds the Android Auto service successfully");
            mAppController.getAapBinderClient().setOnBindIAapReceiverServiceListener(() -> {
                Log.d(TAG, "bind IAapReceiverService success");
                isBoundIAapReceiverService = true;
                connectProjectionUsbDevice();
            });
        } else {
            Log.e(TAG, "AapBinderClient is null");
        }
        Log.e(TAG, "Waiting for the callback that binds the CarPlay service successfully");
        mAppController.setOnUpdateClientStsListener(() -> {
            Log.e(TAG, "bind CarPlay service success");
            isBoundCarPlayService = true;
            connectProjectionUsbDevice();
        });

        Log.e(TAG, "Waiting for the callback that binds the Car service successfully");
        CarHelper.INSTANCE.setOnGetValidValueListener(() -> {
            Log.d(TAG, "get valid value from CarService");
            isGetCarServiceValue = true;
            connectProjectionUsbDevice();
        });
    }

    public static boolean isAvailableUsbDevice(UsbDevice device) {
        if (device == null) {
            Log.e(TAG, "device is null");
            return false;
        }
        if (TextUtils.isEmpty(device.getSerialNumber())) {
            Log.e(TAG, "SerialNumber isEmpty");
            return false;
        }
        if (device.getInterfaceCount() > 0) {
            UsbInterface usbInterface = device.getInterface(0);
            if (usbInterface != null) {
                int mClass = usbInterface.getInterfaceClass();

                if (mClass == UsbConstants.USB_CLASS_HUB) {
                    Log.d(TAG, "USB class for USB hubs.");
                    return false;
                }
                if (mClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER) {
                    Log.d(TAG, "USB class for wireless controller devices.");
                    return false;
                }
                if (mClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                    Log.d(TAG, "USB class for mass storage device");
                    return false;
                }
                if (mClass == UsbConstants.USB_CLASS_HID) {
                    Log.d(TAG, "USB class for human interface devices (for example, mice and keyboards).");
                    return false;
                }
                if (mClass == UsbConstants.USB_CLASS_PRINTER) {
                    Log.d(TAG, "USB class for printers");
                    return false;
                }
            }
        }
        return true;
    }

    private void connectProjectionUsbDevice() {
        Log.d(TAG, "isBoundIAapReceiverService = " + isBoundIAapReceiverService + ", isBoundCarPlayService = " + isBoundCarPlayService + ", isGetCarServiceValue = " + isGetCarServiceValue);
        if (isGetCarServiceValue && (isBoundIAapReceiverService || isBoundCarPlayService)) {
            UsbDevice d = USBKt.getProjectionDevice(mContext);
            Log.d(TAG, "attached projection device = " + new Gson().toJson(d));
            attach(d);
        }
    }

    /**
     * usb device connect state changed notice
     */
    public void onDeviceUpdate(String serial, String name, boolean attached, boolean ios, boolean isSupportAOAP) {
        Log.d(TAG, "onDeviceUpdate() called with: usbDevice = [" + serial + "], attached = [" + attached + "], ios = [" + ios + "], isSupportAOAP = [" + isSupportAOAP + "]");
        Device device = new Device();
        device.setType(1); //1:usb , 2:wireless
        device.setName(name);
        device.setSerial(serial);
        device.setAvailable(attached);
        device.setUsbCP(ios);
        device.setWirelessCP(ios);
        device.setUsbAA(isSupportAOAP);

        if (attached) {
            if (AliveDeviceList.stream().anyMatch(d -> d.getType() == 1 && Objects.equals(d.getSerial(), device.getSerial()))) {
                Log.d(TAG, "already contained, not add");
            } else {
                if (ios) {
                    Log.d(TAG, "add usb alive device " + device.getSerial());
                    AliveDeviceList.add(device);
                } else {
                    if (isSupportAOAP) {
                        Log.d(TAG, "add usb alive device " + device.getSerial());
                        AliveDeviceList.add(device);
                    }
                }
            }
        } else {
            device.setUsbAA(false);//app popup need modify
            Log.d(TAG, "remove usb alive device " + device.getSerial());
            AliveDeviceList.removeIf(d -> ObjectsCompat.equals(d.getSerial(), device.getSerial()) && d.getType() == 1);
        }

        AliveDeviceList.forEach(item -> Log.d(TAG, "alive  ————  " + item.toString()));

        Log.d(TAG, "onDeviceUpdate begin");
        Iterator<OnConnectListener> it = OnConnectListenerList.iterator();
        while (it.hasNext()) {
            OnConnectListener listener = it.next();
            int id = System.identityHashCode(listener);
            Log.d(TAG, "listener " + id);
            try {
                if (listener != null) {
                    listener.onDeviceUpdate(device);
                } else {
                    Log.d(TAG, id + " == null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
                Log.e(TAG, id + " is died, will be removed");
                it.remove();
            }
        }
        Log.d(TAG, "onDeviceUpdate end");
    }

    // 255：Pixel 2 XL, OXF-AN10,
    // 6  ：Pixel 2 XL, iPhone,
    public void attach(UsbDevice device) {
        Log.d(TAG, "attach() called with: device = [" + new Gson().toJson(device) + "]");

        if (!isAvailableUsbDevice(device)) return;

        boolean ios = AppSupport.isIOSDevice(device);

        if (ios) {

            onDeviceUpdate(device.getSerialNumber(), device.getProductName(), true, true, false);

            if (!CarHelper.isOpenCarPlay()) return;
            if (isCertifiedVersion) {
                Log.d(TAG, "certified version not attach usb carplay");
                return;
            }
            if (AppController.isCanConnectingCPWifi) {
                Log.e(TAG, "cp wifi connecting, not connect usb carplay");
                return;
            }
            if (!mAppController.canConnectUsbCarPlay()) return;
            if (!mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) return;
            if (mAppController.isIdleState()) {
                Log.d(TAG, "bindCarPlayServiceSuccess == " + mAppController.bindCarPlayServiceSuccess);
                if (!mAppController.bindCarPlayServiceSuccess) return;
                if (mAppController.isNotSwitchingSession()) {
                    if (!isConnectingCarPlay) {
                        if (mDeviceHandlerResolver.roleSwitch(device)) {
                            mAppController.roleSwitchComplete(device.getSerialNumber());
                        }
                    }
                } else {
                    if (Objects.equals(mAppController.switchingPhone.getSerial(), device.getSerialNumber())) {
                        if (mDeviceHandlerResolver.roleSwitch(device)) {
                            mAppController.roleSwitchComplete(device.getSerialNumber());
                        }
                    }
                }
            } else {
                if (AppController.currentSessionIsWifiAndroidAuto()) {
                    mAppController.onNotification(1, device.getProductName(), device.getSerialNumber(), "", 3);
                    return;
                }
                String trimSerialNumber = device.getSerialNumber().substring(0, mAppController.mCurrentDevice.getSerialNumber().length());
                Log.d(TAG, "trimSerialNumber == " + trimSerialNumber);
                if (AppController.currentSessionIsWifiCarPlay() && !Objects.equals(mAppController.mCurrentDevice.getSerialNumber(), trimSerialNumber)) {
                    mAppController.onNotification(1, device.getProductName(), device.getSerialNumber(), "", 3);
                }
            }
        } else {

            if (ProbedAndroidAutoUsbDeviceSerialNumberSet.contains(device.getSerialNumber())) {
                onDeviceUpdate(device.getSerialNumber(), device.getProductName(), true, true, true);
            }

            if (CarHelper.isOpenQDLink() || !CarHelper.isOpenAndroidAuto()) return;
            if (!mDeviceHandlerResolver.isSupportAOAP(device)) return;
            if (mAppController.isIdleState()) {
                if (!mAppController.isAutoConnectUsbAndroidAuto()) return;
                if (AppSupport.isDeviceInAOAMode(device)) {
//                    mAppController.getHandler().removeCallbacks(mAOAPSwitchTimeoutRunnable);
                    if (LAST_ANDROID_AUTO_SESSION_TERMINATED_REASON == 0) {
                        try {
                            Log.d(TAG, "attach() last reason = 0, delay 2 second");
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d(TAG, "Probed == " + new Gson().toJson(ProbedAndroidAutoUsbDeviceSerialNumberSet));
                    if (ProbedAndroidAutoUsbDeviceSerialNumberSet.contains(device.getSerialNumber())) {
                        Log.d(TAG, "Probed remove " + device.getSerialNumber() + ", " + ProbedAndroidAutoUsbDeviceSerialNumberSet.remove(device.getSerialNumber()));
                        Log.d(TAG, "Probed == " + new Gson().toJson(ProbedAndroidAutoUsbDeviceSerialNumberSet));
                        mAppController.startUsbAndroidAuto(device.getDeviceName(), device.getSerialNumber());
                    } else {
                        mAppController.attachedAndroidAutoDevice.setAttached(true);
                        mAppController.attachedAndroidAutoDevice.setProductName(device.getProductName());
                        mAppController.attachedAndroidAutoDevice.setSerialNumber(device.getSerialNumber());
                        Log.d(TAG, "startProbe");
                        mAppController.getAapBinderClient().startProbe(device.getSerialNumber(), device.getDeviceName());
                    }
                } else {
//                    currentAOAPSwitchSerialNumber = device.getSerialNumber();
//                    mAppController.getHandler().postDelayed(mAOAPSwitchTimeoutRunnable, AOAPSwitchTimeout);
                    mDeviceHandlerResolver.requestAOAPSwitch(device);
                }
            } else {
                if (isConnectingCarPlay) return;
                Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(mAppController.mCurrentDevice));
                Log.d(TAG, "currentDevice.getSerialNumber() = " + mAppController.mCurrentDevice.getSerialNumber());
                if (TextUtils.equals(mAppController.mCurrentDevice.getSerialNumber(), device.getSerialNumber())) {
                    Log.d(TAG, "usb device serial number same as current device serial");
                    return;
                }
                if (mAppController.isNotSwitchingSession()) {
                    Log.d(TAG, "isResettingUsb == " + isResettingUsb);
                    Log.d(TAG, "last android auto device serial == " + LAST_ANDROID_AUTO_DEVICE_SERIAL);
                    if (isResettingUsb && TextUtils.equals(LAST_ANDROID_AUTO_DEVICE_SERIAL, device.getSerialNumber())) {
                        isResettingUsb = false;
                        mAppController.removeResetUsbMessages();
                        return;
                    }
                    if (AppSupport.isDeviceInAOAMode(device)) {
//                        mAppController.getHandler().removeCallbacks(mAOAPSwitchTimeoutRunnable);
                        Log.d(TAG, "Probed == " + new Gson().toJson(ProbedAndroidAutoUsbDeviceSerialNumberSet));
                        if (ProbedAndroidAutoUsbDeviceSerialNumberSet.contains(device.getSerialNumber())) {
                            Log.d(TAG, "Probed remove " + device.getSerialNumber() + ", " + ProbedAndroidAutoUsbDeviceSerialNumberSet.remove(device.getSerialNumber()));
                            Log.d(TAG, "Probed == " + new Gson().toJson(ProbedAndroidAutoUsbDeviceSerialNumberSet));
                            if (USBKt.containsInAttachedUsbDeviceList(mContext, device.getSerialNumber())) {
                                mAppController.onNotification(1, device.getProductName(), device.getSerialNumber(), "", 1);
                            }
                        } else {
                            mAppController.attachedAndroidAutoDevice.setAttached(true);
                            mAppController.attachedAndroidAutoDevice.setProductName(device.getProductName());
                            mAppController.attachedAndroidAutoDevice.setSerialNumber(device.getSerialNumber());
                            Log.d(TAG, "startProbe");
                            mAppController.getAapBinderClient().startProbe(device.getSerialNumber(), device.getDeviceName());
                        }
                    } else {
//                        currentAOAPSwitchSerialNumber = device.getSerialNumber();
//                        mAppController.getHandler().postDelayed(mAOAPSwitchTimeoutRunnable, AOAPSwitchTimeout);
                        mDeviceHandlerResolver.requestAOAPSwitch(device);
                    }
                }
            }
        }
    }

    public void detach(UsbDevice device) {
        Log.d(TAG, "detach() called with: device = [" + new Gson().toJson(device) + "]");

        if (!isAvailableUsbDevice(device)) return;

        boolean ios = AppSupport.isIOSDevice(device);

        if (ios) {
            if (isCertifiedVersion) {
                Log.d(TAG, "certified version not detach usb carplay");
                return;
            }
            if (!mAppController.isIdleState()) {
                onDeviceUpdate(device.getSerialNumber(), device.getProductName(), false, false, false);
            }
        } else {
            if (TextUtils.equals(mAppController.mCurrentDevice.getSerialNumber(), device.getSerialNumber()) && mAppController.mCurrentDevice.getConnectionType() == 1) {
                mAppController.stopUsbAndroidAuto(); // sometimes android auto surface not exit, must invoke this method
            }
            if (!USBKt.containsInAttachedUsbDeviceList(mContext, device.getSerialNumber())) {
                if (ProbedAndroidAutoUsbDeviceSerialNumberSet.contains(device.getSerialNumber())) {
                    onDeviceUpdate(device.getSerialNumber(), device.getProductName(), false, false, true);
                }
            }
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    public void unRegisterReceiver() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

}
