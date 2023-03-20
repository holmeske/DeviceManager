package com.yfve.t19c.projection.devicemanager.constant;

import com.yfve.t19c.projection.devicelist.Device;
import com.yfve.t19c.projection.devicelist.OnConnectListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class DM {
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
     * key: mac , value: pre available
     */
    public final static HashMap<String, Boolean> FindPreAvailableByMac = new HashMap<>();

    /**
     * key: mac , value: current available
     */
    public final static HashMap<String, Boolean> FindCurrentAvailableByMac = new HashMap<>();

    public static final List<Device> HistoryDeviceList = new ArrayList<>();

    public static final List<Device> AliveDeviceList = new ArrayList<>();

    public static final List<OnConnectListener> OnConnectListenerList = new ArrayList<>();

    /**
     * android auto session terminated type
     */
    public static int LAST_ANDROID_AUTO_SESSION_TERMINATED_REASON = -1;

    /**
     * last connect successfully android auto device serial number
     */
    public static String LAST_ANDROID_AUTO_DEVICE_SERIAL;

    public static HashSet<String> AvailableAndroidAutoWirelessDeviceBtMacSet = new HashSet<>();

    public static HashSet<String> ProbedAndroidAutoUsbDeviceSet = new HashSet<>();
    /**
     * key: serialNumber , value: productName
     */
    public static HashMap<String, String> AttachedAndroidAutoUsbDeviceMap = new HashMap<>();

    public static HashSet<String> AttachedUsbDeviceSerialNumberSet = new HashSet<>();

    public static HashSet<String> AttachedIOSUsbDeviceSerialNumberSet = new HashSet<>();

    public static HashSet<String> AttachedAOAPSerialNumberSet = new HashSet<>();
}
