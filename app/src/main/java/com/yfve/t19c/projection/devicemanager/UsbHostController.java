package com.yfve.t19c.projection.devicemanager;

import static com.yfve.t19c.projection.devicemanager.AppController.isCertifiedVersion;
import static com.yfve.t19c.projection.devicemanager.AppController.isReplugged;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class UsbHostController {
    private static final String TAG = "UsbHostController";
    private final Context mContext;
    private final AppController mAppController;
    private final DeviceHandlerResolver mDeviceHandlerResolver;
    private final List<OnConnectListener> mOnConnectListeners;
    private List<Device> mAliveDeviceList = new ArrayList<>();
    private int mClass;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");

            if (UsbHelper.Companion.isBluePort()) return;

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG, "onReceive: " + CommonUtilsKt.toJson(device));
            if (device != null) {
                UsbInterface usbInterface = device.getInterface(0);
                if (usbInterface != null) {
                    mClass = usbInterface.getInterfaceClass();
                    Log.d(TAG, "mClass = " + mClass + " , mVendorId = " + device.getVendorId() + " , mProductId = " + device.getProductId()
                            + " , mProductName = " + device.getProductName() + " , mName = " + device.getDeviceName()
                            + " , mManufacturerName = " + device.getManufacturerName());
                    if (mClass == 7) {
                        Log.d(TAG, "the device is a printer");
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

    public UsbHostController(Context mContext, AppController mAppController, List<OnConnectListener> mOnConnectListeners) {
        Log.d(TAG, "UsbHostController() called");

        this.mContext = mContext;
        this.mAppController = mAppController;
        this.mOnConnectListeners = mOnConnectListeners;

        mDeviceHandlerResolver = new DeviceHandlerResolver(mContext);
        registerReceiver();

        if (mAppController.getAapBinderClient() != null) {
            Log.e(TAG, "waiting for the callback of the Android Auto service to bind successfully");
            mAppController.getAapBinderClient().setOnBindIAapReceiverServiceListener(() -> {
                Log.d(TAG, "bind IAapReceiverService success");
                isBoundIAapReceiverService = true;
                connectLastUsbDevice();
            });
        } else {
            Log.e(TAG, "AapBinderClient is null");
        }
        Log.e(TAG, "waiting for the callback of the CarPlay service to bind successfully");
        mAppController.setOnUpdateClientStsListener(() -> {
            Log.e(TAG, "bind CarPlay service success");
            isBoundCarPlayService = true;
            connectLastUsbDevice();
        });
    }

    public void setCarHelper(CarHelper mCarHelper) {
        Log.d(TAG, "setCarHelper() called");
        mCarHelper.setOnGetValidValueListener(() -> {
            Log.d(TAG, "CarService returned valid value");
            isGetCarServiceValue = true;
            connectLastUsbDevice();
        });
    }

    private void connectLastUsbDevice() {
        Log.d(TAG, "isBoundIAapReceiverService = " + isBoundIAapReceiverService
                + ", isBoundCarPlayService = " + isBoundCarPlayService
                + ", isGetCarServiceValue = " + isGetCarServiceValue);
        if (isGetCarServiceValue && (isBoundIAapReceiverService || isBoundCarPlayService)) {
            UsbDevice d = USBKt.lastUsbDevice(mContext);
            if (d != null) {
                Log.d(TAG, "last usb device serial : " + d.getSerialNumber());
                attach(d);
            } else {
                Log.d(TAG, "current no attached usb device");
            }
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        this.mAliveDeviceList = deviceList;
    }

    /**
     * usb device connect state changed notice
     *
     * @param usbDevice usb device
     * @param attached  usb attached
     * @param ios       true: ios device
     */
    private void onDeviceUpdate(UsbDevice usbDevice, boolean attached, boolean ios) {
        Log.d(TAG, "onDeviceUpdate() called with: serial = [" + usbDevice.getSerialNumber() + "], attached = [" + attached + "], ios = [" + ios + "]");
        Device device = new Device();
        device.setType(1);//1:usb , 2:wireless
        device.setName(usbDevice.getProductName());
        device.setSerial(usbDevice.getSerialNumber());
        device.setAvailable(attached);

        if (ios) {
            if (attached) {
                device.setUsbAA(false);
            } else {
                return;
            }
            if (!CarHelper.isOpenCarPlay()) {
                Log.d(TAG, "onDeviceUpdate: carplay is close , do not notice external");
                return;
            }
        } else {
            if (!CarHelper.isOpenAndroidAuto()) {
                Log.d(TAG, "onDeviceUpdate: android auto is close , do not notice external");
                return;
            }
            if (attached) {
                device.setUsbAA(mDeviceHandlerResolver.isSupportedAOAP(usbDevice));
            }
        }

        if (attached) {
            AtomicBoolean isContain = new AtomicBoolean(false);
            mAliveDeviceList.forEach(d -> {
                if (ObjectsCompat.equals(d.getSerial(), device.getSerial())) {
                    isContain.set(true);
                }
            });
            if (!isContain.get()) {
                mAliveDeviceList.add(device);
            } else {
                Log.d(TAG, "list already contains the device, do not add");
            }
        } else {
            mAliveDeviceList.removeIf(d -> ObjectsCompat.equals(d.getSerial(), device.getSerial()));
        }
        for (int i = 0; i < mAliveDeviceList.size(); i++) {
            Log.d(TAG, "alive device " + i + " : " + mAliveDeviceList.get(i));
        }
        Log.d(TAG, "update usb " + device);
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                listener.onDeviceUpdate(device);
            } catch (RemoteException e) {
                Log.e(TAG, "onDeviceUpdate: ", e);
            }
        }
    }

    public void attach(UsbDevice device) {
        if (device == null) {
            Log.e(TAG, "attach device is null");
            return;
        }
        if (ObjectsCompat.equals(device.getSerialNumber(), null)) {
            Log.e(TAG, "attach device serialnumber is null");
            return;
        }
        Log.d(TAG, "attach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        boolean ios = AppSupport.isIOSDevice(device);
        if (ios) {
            if (isCertifiedVersion) {
                Log.d(TAG, "attach: certified version not attach usb carplay");
                return;
            }
            if (AppController.isCanConnectingCPWifi) {
                Log.e(TAG, "attach: cp wifi connecting");
                return;
            }
            if (!mAppController.canConnectUsbCarPlay()) {
                Log.d(TAG, "attach: don't connect usb car play");
                return;
            }
        } else {
            if (mClass == 8) {
                Log.d(TAG, "the device is a usb storage");
                return;
            }
            if (CarHelper.isOpenQDLink()) return;
        }
        if (!mAppController.isSwitchingState()) {
            onDeviceUpdate(device, true, ios);
            if (!mAppController.isIdleState()) {
                if (!ios) {
                    Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(mAppController.currentDevice));
                    if (TextUtils.equals(mAppController.currentDevice.SerialNumber, device.getSerialNumber())) {
                        Log.d(TAG, "usb device serial number same as current device serial");
                        return;
                    }
                    //when session not null ,  attach android auto device , need notify users
                    mAppController.onNotification(1, device.getProductName(), device.getSerialNumber(), "", 1);
                    return;
                }
            }
        }
        if (ios) {
            if (!CarHelper.isOpenCarPlay()) return;
            if (!mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) return;
            if (mDeviceHandlerResolver.roleSwitch(device)) {
                mAppController.roleSwitchComplete(device.getSerialNumber());
            }
        } else {
            if (!CarHelper.isOpenAndroidAuto()) return;
            if (!mAppController.isAutoConnectUsbAndroidAuto()) return;
            if (!mDeviceHandlerResolver.isSupportedAOAP(device)) return;
            if (AppSupport.isDeviceInAoapMode(device)) {
                mAppController.startUsbAndroidAuto(device.getDeviceName());
            } else {
                mAppController.updatePreparingState();
                mDeviceHandlerResolver.requestAoapSwitch(device);
            }
        }
    }

    public void detach(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "detach device is null");
            return;
        }
        Log.d(TAG, "detach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        boolean ios = AppSupport.isIOSDevice(device);
        if (ios) {
            if (isCertifiedVersion) {
                Log.d(TAG, "certified version not detach usb carplay");
                return;
            }
        } else {
            if (CarHelper.isOpenQDLink()) return;
        }
        if (mAppController.isPreParingState()) {
            mAppController.updateSwitchingState();
        } else {
            if (!ios) {
                Log.d(TAG, "currentDevice = " + CommonUtilsKt.toJson(mAppController.currentDevice));
                if (TextUtils.equals(mAppController.currentDevice.SerialNumber, device.getSerialNumber()) && mAppController.currentDevice.ConnectionType == 1) {
                    mAppController.stopAndroidAuto();//sometimes android auto surface not exit, must invoke this method
                }
            }
            mAppController.setAutoConnectUsbAndroidAuto(true);
            onDeviceUpdate(device, false, ios);
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
