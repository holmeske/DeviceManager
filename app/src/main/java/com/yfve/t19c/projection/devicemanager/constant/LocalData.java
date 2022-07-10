package com.yfve.t19c.projection.devicemanager.constant;

import android.util.Log;

import com.yfve.t19c.projection.devicelist.Device;

import java.util.HashMap;

public final class LocalData {
    /**
     * key: mac , value: instanceId
     */
    public final static HashMap<String, String> FindInstanceIdByMac = new HashMap<>();
    /**
     * key: serial , value: instanceId
     */
    public final static HashMap<String, String> FindInstanceIdBySerial = new HashMap<>();
    /**
     * key: instanceId , value: serial
     */
    public final static HashMap<String, String> FindSerialByInstanceId = new HashMap<>();
    /**
     * key: instanceId , value: mac
     */
    public final static HashMap<String, String> FindMacByInstanceId = new HashMap<>();
    /**
     * key: serial , value: mac
     */
    public final static HashMap<String, String> FindMacBySerial = new HashMap<>();
    /**
     * key: instanceId , value: Device
     */
    public final static HashMap<String, Device> GlobalDeviceMap = new HashMap<>();
    private static final String TAG = "LocalData";
    /**
     * android auto session terminated type
     */
    public static int LAST_REASON = -1;

    public static void updateLocalDeviceInfo(String instanceId, Device device) {
        Log.d(TAG, "updateLocalDeviceInfo() called with: instanceId = [" + instanceId + "], device = [" + device + "]");
        GlobalDeviceMap.put(instanceId, device);
    }
}
