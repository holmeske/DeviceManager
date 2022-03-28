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
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class UsbHostController {
    private static final String TAG = "UsbHostController";
    private final Context mContext;
    private final AppController mAppController;
    private final DeviceHandlerResolver mDeviceHandlerResolver;
    private final List<OnConnectListener> mOnConnectListeners;
    private List<Device> deviceList = new ArrayList<>();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");

            if (UsbHelper.Companion.isBluePort()) return;

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            //Log.d(TAG, "onReceive: " + CommonUtilsKt.toJson(device));
            if (device != null) {
                UsbInterface usbInterface = device.getInterface(0);
                if (usbInterface != null) {
                    int mClass = usbInterface.getInterfaceClass();
                    Log.d(TAG, "mClass = " + mClass + " mVendorId = " + device.getVendorId() + " mProductId = " + device.getProductId());
                    if (mClass == 8 || mClass == 7) {
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
    private boolean isGetCarServiceValue = false;

    public UsbHostController(Context mContext, AppController mAppController, List<OnConnectListener> mOnConnectListeners) {
        Log.d(TAG, "UsbHostController() called");

        this.mContext = mContext;
        this.mAppController = mAppController;
        this.mOnConnectListeners = mOnConnectListeners;

        mDeviceHandlerResolver = new DeviceHandlerResolver(mContext);
        registerReceiver();

        if (mAppController.getAapBinderClient() != null) {
            mAppController.getAapBinderClient().setOnBindIAapReceiverServiceListener(() -> {
                Log.d(TAG, "bind IAapReceiverService success");
                isBoundIAapReceiverService = true;
                connectFirstUsbDevice();
            });
        }
    }

    public void setCarHelper(CarHelper mCarHelper) {
        Log.d(TAG, "setCarHelper() called");
        mCarHelper.setOnGetValidValueListener(() -> {
            Log.d(TAG, "CarService returned valid value");
            isGetCarServiceValue = true;
            connectFirstUsbDevice();
        });
    }

    private void connectFirstUsbDevice() {
        Log.d(TAG, "isBoundIAapReceiverService = " + isBoundIAapReceiverService + ", isGetCarServiceValue = " + isGetCarServiceValue);
        if (isBoundIAapReceiverService && isGetCarServiceValue) {
            UsbDevice d = USBKt.firstUsbDevice(mContext);
            if (d != null) {
                attach(d);
            } else {
                Log.d(TAG, "current no attached usb device");
            }
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        this.deviceList = deviceList;
    }

    /**
     * usb device connect state changed notice
     *
     * @param usbDevice usb device
     * @param attached  usb attached
     * @param ios       true: ios device
     */
    private void onDeviceUpdate(UsbDevice usbDevice, boolean attached, boolean ios) {
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
                device.setUsbAA(mDeviceHandlerResolver.isDeviceAoapPossible(usbDevice));
            }
        }

        if (attached) {
            AtomicBoolean isContain = new AtomicBoolean(false);
            deviceList.forEach(d -> {
                if (ObjectsCompat.equals(d.getSerial(), device.getSerial())) {
                    isContain.set(true);
                }
            });
            if (!isContain.get()) {
                deviceList.add(device);
            } else {
                Log.d(TAG, "list already contains the device, do not add");
            }
        } else {
            deviceList.removeIf(d -> ObjectsCompat.equals(d.getSerial(), device.getSerial()));
        }

        Log.d(TAG, "update usb " + device);
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener == null) {
                    Log.d(TAG, "OnConnectListener is null");
                } else {
                    listener.onDeviceUpdate(device);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onDeviceUpdate: ", e);
            }
        }

        for (int i = 0; i < deviceList.size(); i++) {
            Log.d(TAG, "device list " + (i + 1) + " : " + deviceList.get(i));
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
                Log.d(TAG, "certified version not attach usb carplay");
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
            if (CarHelper.isOpenQDLink()) return;
        }
        if (!mAppController.isSwitchingState()) {
            onDeviceUpdate(device, true, ios);
            if (mAppController.isIdleState()) {
                mAppController.updateCurrentDevice(device, ios ? 3 : 1);
            } else {
                if (!ios) {
                    onNotification(device);
                    return;
                }
            }
        }
        if (mAppController.sameUsbDevice(device)) {
            if (ios) {
                if (CarHelper.isOpenCarPlay()) {
                    if (mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) {
                        if (mDeviceHandlerResolver.roleSwitch(device)) {
                            mAppController.roleSwitchComplete(device.getSerialNumber());
                        }
                    }
                }
            } else {
                if (CarHelper.isOpenAndroidAuto()) {
                    if (!mAppController.isAutoConnectUsbAndroidAuto()) return;
                    if (mDeviceHandlerResolver.isDeviceAoapPossible(device)) {
                        if (AppSupport.isDeviceInAoapMode(device)) {
                            mAppController.startAndroidAuto(device.getDeviceName());
                        } else {
                            if (mAppController.isIdleState()) {
                                try {
                                    mAppController.updatePreparingState();
                                    mDeviceHandlerResolver.requestAoapSwitch(device);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void detach(UsbDevice device) {
        /*if (device == null) {
            Log.d(TAG, "detach device is null");
            return;
        }
        Log.d(TAG, "detach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        boolean ios = AppSupport.isIOSDevice(device);
        if (!ios) {
            if (CarHelper.isOpenQDLink()) return;
            if (mAppController.isPreParingState()) {
                mAppController.updateSwitchingState();
            } else {
                onDeviceUpdate(device, false, ios);
                mAppController.updateIdleState();
            }
        }*/
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
        if (mAppController.sameUsbDevice(device)) {
            if (mAppController.isPreParingState()) {
                mAppController.updateSwitchingState();
            } else {
                if (!ios) {
                    mAppController.stopAndroidAuto();
                }
            }
        }

        if (!mAppController.isPreParingState()) {
            onDeviceUpdate(device, false, ios);
            if (!ios) {
                mAppController.updateIdleState();
            }
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * when session not null ,  attach android auto device , need notify users
     */
    private void onNotification(UsbDevice device) {
        for (OnConnectListener listener : mOnConnectListeners) {
            try {
                if (listener == null) {
                    Log.d(TAG, "OnConnectListener is null");
                } else {
                    String c = "There is available device  " + device.getProductName() + "  , do you want to start Android Auto ?";
                    listener.onNotification(1, c, device.getSerialNumber(), "", 1);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onDeviceUpdate: ", e);
            }
        }
    }

    public void unRegisterReceiver() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

}
