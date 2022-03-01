package com.yfve.t19c.projection.devicemanager;

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
import com.yfve.t19c.projection.devicemanager.constant.CommonUtilsKt;

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
            if (device == null) {
                Log.e(TAG, "UsbDevice is null");
                return;
            } else {
                UsbInterface usbInterface = device.getInterface(0);
                if (usbInterface != null) {
                    int mClass = usbInterface.getInterfaceClass();
                    Log.d(TAG, "onReceive: mClass = " + mClass + " mVendorId = " + device.getVendorId() + " mProductId = " + device.getProductId());
                    if (mClass == 8 || mClass == 7) {
                        return;
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                attach(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
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

        mAppController.setOnCallBackListener(() -> {
            Log.d(TAG, "callback: Bound IAapReceiverService");
            isBoundIAapReceiverService = true;
            connectFirstUsbDevice();
        });
    }

    public void setCarHelper(CarHelper mCarHelper) {
        Log.d(TAG, "setCarHelper() called");
        mCarHelper.setOnGetBytePropertyListener(() -> {
            Log.d(TAG, "callback: car service returned valid value");
            isGetCarServiceValue = true;
            connectFirstUsbDevice();
        });
    }

    private void connectFirstUsbDevice() {
        Log.d(TAG, "isBoundIAapReceiverService = " + isBoundIAapReceiverService + ", isGetCarServiceValue = " + isGetCarServiceValue);
        if (isBoundIAapReceiverService && isGetCarServiceValue) {
            UsbDevice d = USBKt.firstUsbDevice(mContext);
            if (d != null) {
                Log.d(TAG, "first attached device serial is  " + d.getSerialNumber());
                attach(d);
            } else {
                Log.d(TAG, "first attached device is null");
            }
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        this.deviceList = deviceList;
    }

    private void noticeExternal(UsbDevice usbDevice, boolean attached) {
        Device device = new Device();
        device.setType(1);//1:usb , 2:wireless
        device.setName(usbDevice.getProductName());
        device.setSerial(usbDevice.getSerialNumber());
        device.setAvailable(attached);

        if (AppSupport.isIOSDevice(usbDevice)) {
            if (attached) {
                device.setUsbAA(false);
            } else {
                return;
            }
            if (!mAppController.isPresentCarPlay()) {
                Log.d(TAG, "noticeExternal: carplay is close , do not notice external");
                return;
            }
        } else {
            if (!mAppController.isPresentAndroidAuto()) {
                Log.d(TAG, "noticeExternal: android auto is close , do not notice external");
                return;
            }
            if (attached) {
                device.setUsbAA(mDeviceHandlerResolver.isDeviceAoapPossible(usbDevice));
            }
        }

        AtomicBoolean isContain = new AtomicBoolean(false);
        deviceList.forEach(d -> {
            if (ObjectsCompat.equals(d.getSerial(), device.getSerial())) {
                isContain.set(true);
            }
        });

        if (attached) {
            if (!isContain.get()) {
                deviceList.add(device);
            } else {
                Log.d(TAG, "list already contains the device, do not add");
            }
        } else {
            Device temp = deviceList.stream().findFirst().orElse(null);
            Log.d(TAG, "noticeExternal: tmep = " + CommonUtilsKt.toJson(temp));
            deviceList.removeIf(d -> ObjectsCompat.equals(d.getSerial(), device.getSerial()));
            Log.d(TAG, "noticeExternal: tmep = " + CommonUtilsKt.toJson(temp));
        }

        Log.d(TAG, "update usb " + device);
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
            Log.d(TAG, "device list " + (i + 1) + " : " + deviceList.get(i));
        }
    }

    public void attach(UsbDevice device) {
        boolean ios = AppSupport.isIOSDevice(device);
        if (device == null) {
            Log.d(TAG, "attach device is null");
            return;
        }
        if (AppController.isCanConnectingCPWifi) {
            Log.d(TAG, "attach: cp wifi connecting");
            return;
        }
        Log.d(TAG, "attach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        if (ObjectsCompat.equals(device.getSerialNumber(), null)) {
            Log.d(TAG, "attach device serialnumber is null");
            return;
        }
        if (ios) {
            if (!mAppController.canConnectUSB()) {
                Log.d(TAG, "attach: don't connect usb car play");
                return;
            }
        }
        if (!ios && CarHelper.isOpenQDLink()) return;

        if (!mAppController.isSwitchingState()) {
            noticeExternal(device, true);
            if (mAppController.sessionNotExist()) {
                mAppController.updateCurrentDevice(device, ios ? 3 : 1);
            }
            //when session not null ,  attach android auto device , need notify users
            if (!mAppController.sessionNotExist() && !ios) {
                for (OnConnectListener listener : mOnConnectListeners) {
                    try {
                        if (listener == null) {
                            Log.d(TAG, "OnConnectListener is null");
                        } else {
                            String c = "There is available device  " + device.getProductName() + "  , do you want to start Android Auto ?";
                            listener.onNotification(1, c, 1, device.getSerialNumber(), "");
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "noticeExternal: ", e);
                    }
                }
                return;
            }
        }
        if (mAppController.sameUsbDevice(device)) {
            if (ios) {
                if (mAppController.isPresentCarPlay()) {
                    if (mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) {
                        if (mDeviceHandlerResolver.roleSwitch(device)) {
                            mAppController.roleSwitchComplete(device.getSerialNumber());
                        }
                    }
                }
            } else {
                if (mAppController.isPresentAndroidAuto()) {
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
        boolean ios = AppSupport.isIOSDevice(device);
        if (device == null) {
            Log.d(TAG, "detach device is null");
            return;
        }
        if (!ios && CarHelper.isOpenQDLink()) return;
        Log.d(TAG, "detach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");

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
            noticeExternal(device, false);
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

    public void unRegisterReceiver() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

}
