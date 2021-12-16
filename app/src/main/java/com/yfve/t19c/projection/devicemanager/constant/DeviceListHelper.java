package com.yfve.t19c.projection.devicemanager.constant;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import com.yfve.t19c.projection.devicemanager.DeviceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DeviceListHelper {
    private static final String TAG = "DeviceListHelper";
    public static final List<DeviceInfo> allDevice = new ArrayList<>();
    public static final List<String> allSerialNumber = new ArrayList<>();
    public static final List<String> allBluetoothMac = new ArrayList<>();
    public static final List<UsbDevice> allUsbDevice = new ArrayList<>();

    public static void add(DeviceInfo info) {
        if (allSerialNumber.contains(info.SerialNumber)) {
            Log.d(TAG, "add: already contains " + info.SerialNumber);
        } else {
            allSerialNumber.add(info.SerialNumber);
        }

        if (allBluetoothMac.contains(info.BluetoothMac)) {
            Log.d(TAG, "add: already contains " + info.BluetoothMac);
        } else {
            allBluetoothMac.add(info.BluetoothMac);
        }
    }

    public static void delete(DeviceInfo info) {
        Log.d(TAG, "delete: " + info.SerialNumber + ",  " + info.BluetoothMac);
        allDevice.removeIf(it -> Objects.equals(it.SerialNumber, info.SerialNumber) || Objects.equals(it.BluetoothMac, info.BluetoothMac));
    }

    public static String getDeviceName(String serialNumber) {
        for (DeviceInfo info : allDevice) {
            if (ObjectsCompat.equals(info.SerialNumber, serialNumber)) {
                return info.DeviceName;
            }
        }
        return "";
    }
}
