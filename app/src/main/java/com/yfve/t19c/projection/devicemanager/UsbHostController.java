package com.yfve.t19c.projection.devicemanager;

import static com.yfve.t19c.projection.devicemanager.AppController.isCertifiedVersion;
import static com.yfve.t19c.projection.devicemanager.AppController.isConnectingCarPlay;
import static com.yfve.t19c.projection.devicemanager.AppController.isReplugged;
import static com.yfve.t19c.projection.devicemanager.AppController.isResettingUsb;
import static com.yfve.t19c.projection.devicemanager.constant.CacheHelperKt.getLastConnectDeviceInfo;
import static com.yfve.t19c.projection.devicemanager.constant.DM.AliveDeviceList;
import static com.yfve.t19c.projection.devicemanager.constant.DM.LAST_ANDROID_AUTO_DEVICE_SERIAL;
import static com.yfve.t19c.projection.devicemanager.constant.DM.LAST_REASON;
import static com.yfve.t19c.projection.devicemanager.constant.DM.OnConnectListenerList;

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
    private int mClass;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");

            if (UsbHelper.Companion.isBluePort()) return;

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            //Log.d(TAG, "onReceive: " + CommonUtilsKt.toJson(device));
            if (device != null) {
                if (TextUtils.isEmpty(device.getSerialNumber())) return;
                if (device.getSerialNumber().contains(".")) return;
                UsbInterface usbInterface = device.getInterface(0);
                if (usbInterface != null) {
                    mClass = usbInterface.getInterfaceClass();
                    Log.d(TAG, "mClass = " + mClass + " , mVendorId = " + device.getVendorId() + " , mProductId = " + device.getProductId()
                            + " , mProductName = " + device.getProductName() + " , mName = " + device.getDeviceName()
                            + " , mManufacturerName = " + device.getManufacturerName());
                    if (mClass == UsbConstants.USB_CLASS_PRINTER) {
                        Log.d(TAG, "USB class for printers");
                        return;
                    }
                    if (mClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        Log.d(TAG, "USB class for mass storage device");
                        return;
                    }
                }
            }
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

    public UsbHostController(Context mContext, AppController mAppController) {
        Log.d(TAG, "UsbHostController() called");

        this.mContext = mContext;
        this.mAppController = mAppController;

        mDeviceHandlerResolver = new DeviceHandlerResolver(mContext);
        registerReceiver();
        Log.d(TAG, "LastConnectDeviceInfo = " + new Gson().toJson(getLastConnectDeviceInfo(mContext)));
        USBKt.usbDeviceList(mContext).values().forEach(d -> Log.d(TAG, "attached " + d.getSerialNumber() + ", " + d.getProductName()));

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

    private void connectProjectionUsbDevice() {
        Log.d(TAG, "isBoundIAapReceiverService = " + isBoundIAapReceiverService + ", isBoundCarPlayService = " + isBoundCarPlayService
                + ", isGetCarServiceValue = " + isGetCarServiceValue);
        if (isGetCarServiceValue && (isBoundIAapReceiverService || isBoundCarPlayService)) {
            UsbDevice d = USBKt.getProjectionDevice(mContext);
            Log.d(TAG, "attached available device = " + new Gson().toJson(d));
            if (d == null) return;
            if (AppSupport.isIOSDevice(d)) {
                if (isBoundCarPlayService) {
                    attach(d);
                }
            } else {
                if (isBoundIAapReceiverService) {
                    attach(d);
                }
            }
        }
    }

    /**
     * usb device connect state changed notice
     */
    private void onDeviceUpdate(UsbDevice usbDevice, boolean attached, boolean ios, boolean isSupportAOAP) {
        Log.d(TAG, "onDeviceUpdate() called with: usbDevice = [" + usbDevice.getSerialNumber() + "], attached = [" + attached + "], ios = [" + ios + "], isSupportAOAP = [" + isSupportAOAP + "]");
        Device device = new Device();
        device.setType(1); //1:usb , 2:wireless
        device.setName(usbDevice.getProductName());
        device.setSerial(usbDevice.getSerialNumber());
        device.setAvailable(attached);
        device.setUsbCP(ios);
        device.setWirelessCP(ios);
        device.setUsbAA(isSupportAOAP);
        device.setWirelessAA(isSupportAOAP);

        if (attached) {
            if (AliveDeviceList.stream().anyMatch(d -> d.getType() == 1 && Objects.equals(d.getSerial(), device.getSerial()))) {
                Log.d(TAG, "already contained, not add");
            } else {
                Log.d(TAG, "add usb alive device " + device.getSerial());
                AliveDeviceList.add(device);
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
                    Log.d(TAG, "listener == null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
                Log.e(TAG, id + " is died, will be removed");
                it.remove();
            }
        }
        Log.d(TAG, "onDeviceUpdate end");
    }

    public void attach(UsbDevice device) {
        if (device == null) {
            Log.e(TAG, "attach device is null");
            return;
        }
        if (device.getSerialNumber() == null) {
            Log.e(TAG, "attach device serialnumber is null");
            return;
        }
        Log.d(TAG, "attach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        boolean ios = AppSupport.isIOSDevice(device);
        boolean isSupportAOAP = mDeviceHandlerResolver.isSupportAOAP(device);
        if (!AppSupport.isDeviceInAOAMode(device)) {
            onDeviceUpdate(device, true, ios, isSupportAOAP);
        }
        if (ios) {
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
            if (!CarHelper.isOpenCarPlay()) return;
            if (!mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) return;
            if (mAppController.isIdleState()) {
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
            if (mClass == 8) {
                Log.d(TAG, "the device is a usb storage");
                return;
            }
            if (CarHelper.isOpenQDLink() || !CarHelper.isOpenAndroidAuto()) return;
            if (!isSupportAOAP) {
                Log.d(TAG, "isSupportAOAP == false");
                return;
            }
            if (mAppController.isIdleState()) {
                if (!mAppController.isAutoConnectUsbAndroidAuto()) return;
                if (AppSupport.isDeviceInAOAMode(device)) {
                    if (LAST_REASON == 0) {
                        try {
                            Log.d(TAG, "attach() last reason = 0, delay 2 second");
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    mAppController.startUsbAndroidAuto(device.getDeviceName(), device.getSerialNumber());
                } else {
                    mDeviceHandlerResolver.requestAOAPSwitch(device);
                }
            } else {
                Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(mAppController.mCurrentDevice));
                Log.d(TAG, "currentDevice.getSerialNumber() = " + mAppController.mCurrentDevice.getSerialNumber());
                if (TextUtils.equals(mAppController.mCurrentDevice.getSerialNumber(), device.getSerialNumber())) {
                    Log.d(TAG, "usb device serial number same as current device serial");
                    return;
                }
                if (mAppController.isNotSwitchingSession()) {
                    Log.d(TAG, "isResettingUsb = " + isResettingUsb);
                    Log.d(TAG, "LAST_ANDROID_AUTO_DEVICE_SERIAL = " + LAST_ANDROID_AUTO_DEVICE_SERIAL);
                    if (isResettingUsb && TextUtils.equals(LAST_ANDROID_AUTO_DEVICE_SERIAL, device.getSerialNumber())) {
                        isResettingUsb = false;
                        mAppController.removeResetUsbMessages();
                        Log.d(TAG, "usb reset end");
                        return;
                    }
                    if (AppSupport.isDeviceInAOAMode(device)) {
                        mAppController.attachedAndroidAutoDevice.setAttached(true);
                        mAppController.attachedAndroidAutoDevice.setProductName(device.getProductName());
                        mAppController.attachedAndroidAutoDevice.setSerialNumber(device.getSerialNumber());
                        mAppController.getAapBinderClient().startProbe(device.getSerialNumber(), device.getDeviceName());
                        //mAppController.onNotification(1, device.getProductName(), device.getSerialNumber(), "", 1);
                    } else {
                        mDeviceHandlerResolver.requestAOAPSwitch(device);
                    }
                }
            }
        }
    }

    public void detach(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "detach() called, device == null");
            return;
        }
        Log.d(TAG, "detach() called with: serial = [" + device.getSerialNumber() + "], name = [" + device.getProductName() + "]");
        boolean ios = AppSupport.isIOSDevice(device);
        boolean isSupportAOAP = mDeviceHandlerResolver.isSupportAOAP(device);
        if (ios) {
            if (isCertifiedVersion) {
                Log.d(TAG, "certified version not detach usb carplay");
            }
            if (!mAppController.isIdleState()) {
                onDeviceUpdate(device, false, false, isSupportAOAP);
            }
        } else {
            if (TextUtils.equals(mAppController.mCurrentDevice.getSerialNumber(), device.getSerialNumber()) && mAppController.mCurrentDevice.getConnectionType() == 1) {
                mAppController.stopUsbAndroidAuto(); //sometimes android auto surface not exit, must invoke this method
            }
            if (!USBKt.contains(mContext, device.getSerialNumber())) {
                onDeviceUpdate(device, false, false, isSupportAOAP);
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
