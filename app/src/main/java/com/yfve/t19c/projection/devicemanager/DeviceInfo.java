package com.yfve.t19c.projection.devicemanager;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Objects;

public class DeviceInfo {
    private static final String TAG = "DeviceInfo";
    private static final int ConnectType_None = 0;
    private static final int ConnectType_USB = 1;
    private static final int ConnectType_WIFI = 2;
    private String SerialNumber = "";
    private String BluetoothMac = "";
    private String DeviceName = "";
    private int ConnectionType;
    private boolean isAttached;
    private String instanceId;

    public DeviceInfo() {
    }

    public String getSerialNumber() {
        return SerialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        SerialNumber = serialNumber;
    }

    public void setDeviceName(String deviceName) {
        DeviceName = deviceName;
    }

    public int getConnectionType() {
        return ConnectionType;
    }

    public void setConnectionType(int connectionType) {
        ConnectionType = connectionType;
    }

    public String getBluetoothMac() {
        return BluetoothMac;
    }

    public void setBluetoothMac(String bluetoothMac) {
        BluetoothMac = bluetoothMac;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isAttached() {
        return isAttached;
    }

    public void setAttached(boolean attached) {
        isAttached = attached;
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
        instanceId = "";
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
                ", isAttached=" + isAttached +
                ", instanceId='" + instanceId + '\'' +
                '}';
    }
}