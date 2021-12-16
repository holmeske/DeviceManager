package com.yfve.t19c.projection.devicemanager;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class DeviceHandlerResolver {
    private static final String TAG = "DeviceHandlerResolver";
    private final Context mContext;
    private final UsbManager mUsbManager;

    public DeviceHandlerResolver(Context mContext) {
        this.mContext = mContext;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
    }

    public boolean isDeviceAoapPossible(UsbDevice device) {
        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        UsbDeviceConnection connection = UsbUtil.openConnection(usbManager, device);
        if (connection == null) {
            return false;
        }
        boolean aoapSupported = AppSupport.isAOASupported(mContext, device, connection);
        connection.close();
        return aoapSupported;
    }

    private String getHashed(String serial) {
        try {
            return Arrays.toString(MessageDigest.getInstance("MD5").digest(serial.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "could not create MD5 for serial number: " + serial);
            return Integer.toString(serial.hashCode());
        }
    }

    public void requestAoapSwitch(UsbDevice device) throws IOException {
        Log.d(TAG, "requestAoapSwitch() called");
        if (device == null) {
            Log.e(TAG, "invalid device");
            return;
        }
        UsbDeviceConnection connection = UsbUtil.openConnection(mUsbManager, device);
        if (connection == null) {
            Log.e(TAG, "Failed to connect to usb device.");
            return;
        }

        try {
            String hashedSerial;
            hashedSerial = getHashed(Build.getSerial());
            Log.d(TAG, "sendAoapAccessoryStart");
            UsbUtil.sendAoapAccessoryStart(
                    connection,
                    "Android",
                    "Android Auto",
                    "Android Auto",
                    "1.0",
                    "http://www.android.com/auto",
                    hashedSerial);
        } finally {
            connection.close();
        }
        Log.d(TAG, "sendAoapAccessoryStart end");
    }


    public boolean isDeviceCarPlayPossible(UsbDevice device) {
        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        UsbDeviceConnection connection = UsbUtil.openConnection(usbManager, device);
        boolean carplaySupported = AppSupport.isCarPlaySupport(mContext, device, connection);
        connection.close();
        return carplaySupported;
    }

    public boolean roleSwitch(UsbDevice device) {
        int ret;
        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        UsbDeviceConnection connection = UsbUtil.openConnection(usbManager, device);

         if (connection == null) {
             Log.d(TAG, "roleSwitch: UsbDeviceConnection is null");
            return false;
        }

        ret = connection != null ? connection.controlTransfer(
                0x40,              // requestType
                0x51,              // request
                1,              // value
                0x00,              // index
                null,              // buffer
                0x00,              // length
                1000) : 0;
        if (ret < 0) {
            Log.d(TAG, "RoleSwitch send role switch command failed to iphone......");
        } else {
            ret = 1;
            Log.d(TAG, "RoleSwitch send role switch command success to iphone......");
        }
        if (connection != null) {
            connection.close();
        }
        return ret == 1;
    }
}
