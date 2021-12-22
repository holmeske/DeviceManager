package com.yfve.t19c.projection.devicemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
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
    private final List<String> mAndroidAutoList = new ArrayList<>();
    private final List<String> mCarPlayList = new ArrayList<>();
    private final UsbHelper mUsbHelper;
    private final List<OnConnectListener> mOnConnectListeners;
    private List<Device> deviceList = new ArrayList<>();
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive() called with: action = [" + intent.getAction() + "]");

            if (mUsbHelper != null && mUsbHelper.isBluePort()) return;

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                attach(device);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                detach(device);
            }
        }
    };
    private boolean isBoundIAapReceiverService = false;
    private boolean isGetCarServiceValue = false;

    public UsbHostController(Context mContext, AppController mAppController, UsbHelper usbHelper, List<OnConnectListener> mOnConnectListeners) {
        Log.d(TAG, "UsbHostController() called");

        this.mContext = mContext;
        this.mAppController = mAppController;
        this.mOnConnectListeners = mOnConnectListeners;

        mDeviceHandlerResolver = new DeviceHandlerResolver(mContext);
        registerReceiver();

        this.mUsbHelper = usbHelper;

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
        if (attached) {
            device.setUsbAA(mDeviceHandlerResolver.isDeviceAoapPossible(usbDevice));
        }
        device.setAvailable(attached);


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
            deviceList.removeIf(d -> ObjectsCompat.equals(d.getSerial(), device.getSerial()));
        }
        for (int i = 0; i < deviceList.size(); i++) {
            Log.d(TAG, "device list " + i + " : " + deviceList.get(i));
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
    }

    public void attach(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "attached device is null");
            return;
        }
        Log.d(TAG, "attach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        if (!mAppController.isSwitchingState()) {
            noticeExternal(device, true);
        }
        if (mAppController.getCurrentDevice() == null) {
            mAppController.setCurrentDevice(device);
        }
        if (mAppController.deviceSame(device)) {
            if (mDeviceHandlerResolver.isDeviceAoapPossible(device)) {
                if (mAppController.isPresentAndroidAuto()) {
                    if (AppSupport.isDeviceInAoapMode(device)) {
                        if (!mAndroidAutoList.contains(device.getSerialNumber())) {
                            mAndroidAutoList.add(device.getSerialNumber());
                            mAppController.updateUsbAvailableDevice(device.getSerialNumber(), "", true);
                        }
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
            } else if (mDeviceHandlerResolver.isDeviceCarPlayPossible(device)) {
                if (mAppController.isPresentCarPlay()) {
                    if (!mCarPlayList.contains(device.getSerialNumber())) {
                        mCarPlayList.add(device.getSerialNumber());
                    }
                    if (mDeviceHandlerResolver.roleSwitch(device)) {
                        mAppController.roleSwitchComplete(device.getSerialNumber());
                    }
                }
            } else {
                Log.d(TAG, "isDeviceAoapPossible = false , isDeviceCarPlayPossible = false");
                mAppController.setCurrentDevice(null);
            }
        }
    }

    public void detach(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "detached device is null");
            return;
        }
        Log.d(TAG, "detach() called with: name = [" + device.getProductName() + "], serial = [" + device.getSerialNumber() + "]");
        if (!mAppController.isPreParingState()) {
            if (mAndroidAutoList.contains(device.getSerialNumber())) {
                noticeExternal(device, false);
            }
        }

        if (mAppController.deviceSame(device)) {
            if (mAppController.isPreParingState()) {
                mAppController.updateSwitchingState();
            } else {
                if (mAndroidAutoList.contains(device.getSerialNumber())) {
                    if (mAppController.isPresentAndroidAuto()) {
                        mAppController.updateUsbAvailableDevice(device.getSerialNumber(), "", false);
                        mAppController.stopAndroidAuto();
                    }
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
