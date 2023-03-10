package com.yfve.t19c.projection.devicemanager;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Objects;

public class DeviceInfo {
    private static final String TAG = "DeviceInfo";
    private static final int ConnectType_None = 0;
    private static final int ConnectType_USB = 1;
    private static final int ConnectType_WIFI = 2;
    private String serialNumber = "";
    private String bluetoothMac = "";
    private String productName = "";
    private int connectionType;
    private boolean isAttached;
    private String instanceId;

    public DeviceInfo() {
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String deviceName) {
        productName = deviceName;
    }

    public int getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(int connectionType) {
        this.connectionType = connectionType;
    }

    public String getBluetoothMac() {
        return bluetoothMac;
    }

    public void setBluetoothMac(String bluetoothMac) {
        this.bluetoothMac = bluetoothMac;
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
        serialNumber = serial;
        bluetoothMac = mac;
        productName = name;
        connectionType = conType;
    }

    public void reset() {
        serialNumber = "";
        bluetoothMac = "";
        productName = "";
        connectionType = 0;
        instanceId = "";
    }

    public boolean match(String inputData, int conType) {
        Log.d(TAG, "match() called with: inputData = [" + inputData + "], conType = [" + conType + "]");
        if (serialNumber == null || inputData == null) return false;
        if ((conType & ConnectType_USB) > ConnectType_None) {
            return Objects.equals(serialNumber, inputData);
        } else if ((conType & ConnectType_WIFI) > ConnectType_None) {
            return Objects.equals(bluetoothMac, inputData);
        } else {
            // discard this device if connect type is error
            return true;
        }
    }

    public boolean match(DeviceInfo info) {
        if ((info.connectionType & ConnectType_USB) > ConnectType_None) {
            return Objects.equals(serialNumber, info.serialNumber);
        } else if ((info.connectionType & ConnectType_WIFI) > 0) {
            return Objects.equals(bluetoothMac, info.bluetoothMac);
        } else {
            // discard this device if connect type is error
            return true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "DeviceInfo{" +
                "serialNumber='" + serialNumber + '\'' +
                ", bluetoothMac='" + bluetoothMac + '\'' +
                ", productName='" + productName + '\'' +
                ", connectionType=" + connectionType +
                ", isAttached=" + isAttached +
                ", instanceId='" + instanceId + '\'' +
                '}';
    }
}