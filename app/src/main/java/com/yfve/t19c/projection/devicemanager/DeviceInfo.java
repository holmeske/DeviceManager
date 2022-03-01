package com.yfve.t19c.projection.devicemanager;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

public class DeviceInfo {
    public static final int ConnectType_None = 0;
    public static final int ConnectType_USB = 1;
    public static final int ConnectType_WIFI = 2;
    public static final int ConnectType_Both = 3;
    public static final int Connectivity_AppType_CarPlay = 0;
    public static final int Connectivity_AppType_AndroidAuto = 1;
    public static final int Connectivity_AppType_CarLife = 2;
    public static final int Connectivity_AppType_HiCar = 3;
    public static final int Connectivity_AppType_MAX = 4;
    private static final String TAG = "DeviceInfo";
    public String SerialNumber = "";
    public String BluetoothMac = "";
    public String DeviceName;
    public int ConnectionType;
    public boolean[] AppAvailable = new boolean[Connectivity_AppType_MAX];
    private int lastConnectType;

    public DeviceInfo() {
    }

    public DeviceInfo(String serial, String name, int conType) {
        if (conType == ConnectType_USB) {
            SerialNumber = serial;
        } else if (conType == ConnectType_WIFI) {
            BluetoothMac = serial;
        }

        DeviceName = name;
        ConnectionType = conType;
    }

    public DeviceInfo(String serial, String name, int conType, boolean isSupCp, boolean isSupAA) {
        if (conType == ConnectType_USB) {
            SerialNumber = serial;
        } else if (conType == ConnectType_WIFI) {
            BluetoothMac = serial;
        }

        DeviceName = name;
        ConnectionType = conType;
        AppAvailable[Connectivity_AppType_CarPlay] = isSupCp;
        AppAvailable[Connectivity_AppType_AndroidAuto] = isSupAA;
    }

    public DeviceInfo(String serial, String btMac, String name, int conType, boolean isSupCp, boolean isSupAA, boolean isSupCL, boolean isSupHC) {
        SerialNumber = serial;
        BluetoothMac = btMac;
        DeviceName = name;
        ConnectionType = conType;
        AppAvailable[Connectivity_AppType_CarPlay] = isSupCp;
        AppAvailable[Connectivity_AppType_AndroidAuto] = isSupAA;
        AppAvailable[Connectivity_AppType_CarLife] = isSupCL;
        AppAvailable[Connectivity_AppType_HiCar] = isSupHC;
    }

    public void update(String serial, String mac, String name, int conType) {
        SerialNumber = serial;
        BluetoothMac = mac;
        DeviceName = name;
        ConnectionType = conType;
    }

    public void reset() {
        SerialNumber = "";
        BluetoothMac = "";
        DeviceName = "";
        ConnectionType = 0;
        AppAvailable = new boolean[]{false, false, false, false};
        lastConnectType = 0;
    }

    public int getLastConnectType() {
        return lastConnectType;
    }

    public void setLastConnectType(int lastConnectType) {
        this.lastConnectType = lastConnectType;
    }

    public boolean match(String inputData, int conType) {
        Log.d(TAG, "match() called with: inputData = [" + inputData + "], conType = [" + conType + "]");
        if (SerialNumber == null || inputData == null) return false;
        if ((conType & ConnectType_USB) > ConnectType_None) {
            return Objects.equals(SerialNumber, inputData);
        } else if ((conType & ConnectType_WIFI) > ConnectType_None) {
            return Objects.equals(BluetoothMac, inputData);
        } else {
            // discard this device if connect type is error
            return true;
        }
    }

    public boolean match(DeviceInfo info) {
        if ((info.ConnectionType & ConnectType_USB) > ConnectType_None) {
            return Objects.equals(SerialNumber, info.SerialNumber);
        } else if ((info.ConnectionType & ConnectType_WIFI) > 0) {
            return Objects.equals(BluetoothMac, info.BluetoothMac);
        } else {
            // discard this device if connect type is error
            return true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "DeviceInfo{" +
                "SerialNumber='" + SerialNumber + '\'' +
                ", BluetoothMac='" + BluetoothMac + '\'' +
                ", DeviceName='" + DeviceName + '\'' +
                ", ConnectionType=" + ConnectionType +
                ", AppAvailable=" + Arrays.toString(AppAvailable) +
                '}';
    }


}