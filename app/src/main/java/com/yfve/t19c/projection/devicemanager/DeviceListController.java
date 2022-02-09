/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yfve.t19c.projection.devicemanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.core.util.ObjectsCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

// 1.usb插入后加入devicelist,此时更新数据库
// 2.bt pair后加入devicelist,此时不更新数据库
// 3.iap2认证后，上报serial number及btmac,此时更新数据库
// 4.设备usb拔出,此时更新数据库
// 5.蓝牙设备移除,此时更新数据库

public final class DeviceListController {
    private static final String TAG = "DeviceListController";
    private final CopyOnWriteArrayList<DeviceInfo> mDeviceList = new CopyOnWriteArrayList<>();
    private final Context mContext;
    private final BroadcastReceiver mBtBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /*Log.i(TAG, "bluetooth intent onReceive:" + intent.getAction());
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                Log.i(TAG, "BluetoothAdapter.ACTION_BOND_STATE_CHANGED");
                int curState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE); //当前的配对的状态
                int preState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE); //前一次的配对状态
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); //配对的设备信息
                Log.i(TAG, "curState: " + curState + " name:" + device.getName());
                Log.i(TAG, "bluetooth mac:" + device.getAddress());
                if (curState == BluetoothDevice.BOND_BONDED) {
                    addDeviceToList(device);
                    showListInfos();
                } else if (curState == BluetoothDevice.BOND_NONE) {
                    removeDeviceFromList(device);
                    showListInfos();
                }
            }*/
        }
    };
    private final UsbHelper mUsbHelper;
    public List<IDevListener> mCallbackList = new ArrayList<>();
    private DeviceListStorage mStorage = null;
    private final BroadcastReceiver mUsbBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mUsbHelper != null && mUsbHelper.isBluePort()) {
                Log.d(TAG, "blue port do not process device list");
                return;
            }
            showListInfos();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                removeDeviceFromList(device);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                addDeviceToList(device);
            }
        }
    };


    public DeviceListController(Context context, UsbHelper usbHelper) {
        Log.d(TAG, "DeviceListController() called");
        mContext = context;
        this.mUsbHelper = usbHelper;

        if (mContext != null) {
            mStorage = new DeviceListStorage(mContext);

            for (DeviceInfo info : mStorage.readDeviceList()) {
                Log.d(TAG, "SQLite saved device ---" + info.toString());
            }
            Log.i(TAG, "get initial list from database");
            initDeviceLists();
            // mDeviceList = mStorage.readDeviceList();

            showListInfos();

            syncDeviceList();
        }
        //ListenBtIntent();
        ListenUsbIntent();
    }

    public void registerListener(IDevListener listener) {
        if (listener == null) {
            Log.i(TAG, "listener == null");
            return;
        }
        Log.i(TAG, "add mCallbackList:" + listener);
        mCallbackList.add(listener);

        if (mDeviceList.size() > 0) {
            List<DeviceInfos> deviceList = new ArrayList<>();
            for (DeviceInfo localDevice : mDeviceList) {
                DeviceInfos device = new DeviceInfos();
                device.mConnectionType = localDevice.ConnectionType;
                device.mSerialNumber = localDevice.SerialNumber;
                device.mBluetoothMac = localDevice.BluetoothMac;
                device.mDeviceName = localDevice.DeviceName;
                device.mAppAvailable = localDevice.AppAvailable.clone();

                deviceList.add(device);
            }
            try {
                listener.onNotifyDeviceLists(deviceList);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void unregisterListener(IDevListener listener) {
        if (listener == null) {
            Log.i(TAG, "listener == null");
            return;
        }
        Log.i(TAG, "remove mCallbackList:" + listener);
        mCallbackList.remove(listener);
    }

    public void syncDeviceList() {
        Log.i(TAG, "syncDeviceList deviceList size:" + mDeviceList.size());
        if (mDeviceList.size() > 0) {
            List<DeviceInfos> deviceList = new ArrayList<DeviceInfos>();
            for (DeviceInfo localDevice : mDeviceList) {
                DeviceInfos device = new DeviceInfos();
                device.mConnectionType = localDevice.ConnectionType;
                device.mSerialNumber = localDevice.SerialNumber;
                device.mBluetoothMac = localDevice.BluetoothMac;
                device.mDeviceName = localDevice.DeviceName;
                device.mAppAvailable = localDevice.AppAvailable.clone();
                deviceList.add(device);
            }
            Log.i(TAG, "syncDeviceList mCallbackList size:" + mCallbackList.size());
            for (IDevListener listener : mCallbackList) {
                Log.i(TAG, "notify deviceLists to:" + listener);
                try {
                    listener.onNotifyDeviceLists(deviceList);
                } catch (Exception e) {
                    // TODO: handle exception
                    Log.e(TAG, e.toString());
                }

            }
        }
    }

    public void release() {
        Log.d(TAG, "release() called");
        //if (mStorage != null) mContext.unregisterReceiver(mBtBroadcastReceiver);
        if (mStorage != null) mContext.unregisterReceiver(mUsbBroadcastReceiver);
    }

    private void initDeviceLists() {
        Log.d(TAG, "initDeviceLists() called");
        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            addDeviceToList(device);
        }

        /*BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        for (BluetoothDevice btDevice : pairedDevices) {
            addDeviceToList(btDevice);
        }*/
    }

    public void showListInfos() {
        Log.d(TAG, "showListInfos() called");
        Log.i(TAG, "DeviceList size is " + mDeviceList.size());
        Log.i(TAG, "DeviceList info is as followed:");
        for (DeviceInfo localDevice : mDeviceList) {
            switch (localDevice.ConnectionType) {
                case DeviceInfo.ConnectType_USB:
                    Log.i(TAG, "connection type is USB");
                    break;
                case DeviceInfo.ConnectType_WIFI:
                    Log.i(TAG, "connection type is WIFI");
                    break;
                case DeviceInfo.ConnectType_Both:
                    Log.i(TAG, "connection type is BOTH");
                    break;
                default:
                    Log.i(TAG, "unknown connection type!");
                    break;
            }
            Log.i(TAG, "device serial:" + localDevice.SerialNumber);
            Log.i(TAG, "device mac:" + localDevice.BluetoothMac);
            Log.i(TAG, "device name:" + localDevice.DeviceName);
            Log.i(TAG, "\n");
        }
    }

    private void ListenBtIntent() {
        if (null == mContext) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        mContext.registerReceiver(mBtBroadcastReceiver, filter);
    }

    private void ListenUsbIntent() {
        if (null == mContext) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.registerReceiver(mUsbBroadcastReceiver, filter);
    }

    private void notifyDeviceList() {
        if (mDeviceList.size() > 0) {
            List<DeviceInfos> deviceList = new ArrayList<>();
            for (DeviceInfo localDevice : mDeviceList) {
                DeviceInfos device = new DeviceInfos();
                device.mConnectionType = localDevice.ConnectionType;
                device.mSerialNumber = localDevice.SerialNumber;
                device.mBluetoothMac = localDevice.BluetoothMac;
                device.mDeviceName = localDevice.DeviceName;
                device.mAppAvailable = localDevice.AppAvailable.clone();
                deviceList.add(device);
            }
            Log.i(TAG, "syncDeviceList mCallbackList size:" + mCallbackList.size());
            for (IDevListener listener : mCallbackList) {
                Log.i(TAG, "notify deviceLists to:" + listener);
                try {
                    listener.onNotifyDeviceLists(deviceList);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }

            }
        }
    }

    /**
     * 遍历数据库中的设备序列号和蓝牙地址，判断是否为新设备
     *
     * @param serialNum 序列号
     * @param btMac     蓝牙地址
     * @return 是否为新设备
     */
    private boolean isFirstConnect(String serialNum, String btMac) {
        for (DeviceInfo d : mStorage.readDeviceList()) {
            if (Objects.equals(d.SerialNumber, serialNum) && d.BluetoothMac.equals(btMac)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 弹框提示用户选择APP类型
     */
    private void hintUserSelect() {
        Log.d(TAG, "hintUserSelect() called");
        //Toast.makeText(mContext.getApplicationContext(), "请选择app类型(现在只有aa和cp)", Toast.LENGTH_SHORT).show();
        /*new AlertDialog.Builder(mContext).setTitle("请选择app类型(现在只有aa和cp)").setNegativeButton("aa", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(mContext.getApplicationContext(), "aa", Toast.LENGTH_SHORT).show();
            }
        }).setPositiveButton("cp", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(mContext.getApplicationContext(), "cp", Toast.LENGTH_SHORT).show();
            }
        }).create().show();*/
    }

    synchronized private void addDeviceToList(BluetoothDevice device) {
        boolean deviceMatch = false;
        for (DeviceInfo localDevice : mDeviceList) {
            if (localDevice.match(device.getAddress(), DeviceInfo.ConnectType_WIFI)) {
                deviceMatch = true;
            }
        }
        if (!deviceMatch) {
            // todo(lsw1):need bt add interface to check whether this device support aa or carplay
            DeviceInfo newDevice = new DeviceInfo(device.getAddress(), device.getName(), DeviceInfo.ConnectType_WIFI, true, true);
            boolean isFirstConnect = isFirstConnect(newDevice.SerialNumber, newDevice.BluetoothMac);
            Log.i(TAG, "add bt device,mac is " + newDevice.BluetoothMac + (isFirstConnect ? "  is FirstConnect" : "  not FirstConnect"));
            mDeviceList.add(newDevice);
            if (isFirstConnect) {
                Log.d(TAG, "isFirstConnect called with: device = [" + newDevice.BluetoothMac + "]");
                mStorage.saveDevice(newDevice);
                hintUserSelect();
            }
            notifyDeviceList();
        }
    }

    synchronized public void addUsbDeviceToList(String serialNumber) {
        Log.d(TAG, "addDeviceToList() called with: device = [" + serialNumber + "]");
        boolean deviceMatch = false;
        for (DeviceInfo localDevice : mDeviceList) {
            if (localDevice.match(serialNumber, DeviceInfo.ConnectType_USB)) {
                deviceMatch = true;
            }
        }
        if (!deviceMatch) {
            DeviceInfo newDevice = new DeviceInfo(serialNumber, "", DeviceInfo.ConnectType_USB, true, false);
            boolean isFirstConnect = isFirstConnect(newDevice.SerialNumber, newDevice.BluetoothMac);
            Log.i(TAG, "add usb device,serial is " + newDevice.SerialNumber + (isFirstConnect ? "  is FirstConnect" : "  not FirstConnect"));
            mDeviceList.add(newDevice);
            if (isFirstConnect) {
                mStorage.saveDevice(newDevice);
                hintUserSelect();
            }
            notifyDeviceList();
        }
    }

    synchronized public void removeUsbDeviceFromList(String serialNumber) {
        Log.d(TAG, "removeDeviceFromList() called with: device = [" + serialNumber + "]");
        for (DeviceInfo localDevice : mDeviceList) {
            if (localDevice.match(serialNumber, DeviceInfo.ConnectType_USB)) {
                localDevice.ConnectionType -= DeviceInfo.ConnectType_USB;
                if (localDevice.ConnectionType == DeviceInfo.ConnectType_None) {
                    mDeviceList.remove(localDevice);
                    //mStorage.deleteDevice(localDevice);
                } else {
                    localDevice.SerialNumber = "";
                }
                notifyDeviceList();
            }
        }
    }

    synchronized public void addDeviceToList(UsbDevice device) {
        if (CarHelper.isOpenQDLink()) return;
        if (AppSupport.isIOSDevice(device)) {
            if (!CarHelper.isOpenCarPlay()) {
                Log.d(TAG, "addDeviceToList: carplay is close , do not deal");
                return;
            }
        } else {
            if (!CarHelper.isOpenAndroidAuto()) {
                Log.d(TAG, "addDeviceToList: android auto is close , do not deal");
                return;
            }
        }

        Log.d(TAG, "addDeviceToList() called with: device = [" + device.getSerialNumber() + "]");
        boolean deviceMatch = false;
        for (DeviceInfo localDevice : mDeviceList) {
            //Log.i(TAG, "removeDeviceFromList " + device.getSerialNumber());
            if (localDevice.match(device.getSerialNumber(), DeviceInfo.ConnectType_USB)) {
                deviceMatch = true;
            }
        }
        if (!deviceMatch) {
            DeviceInfo newDevice = new DeviceInfo(device.getSerialNumber(), device.getDeviceName(), DeviceInfo.ConnectType_USB
                    , isDeviceCarPlayPossible(device),
                    !AppSupport.isIOSDevice(device) && new DeviceHandlerResolver(mContext).isDeviceAoapPossible(device));
            boolean isFirstConnect = isFirstConnect(newDevice.SerialNumber, newDevice.BluetoothMac);
            Log.i(TAG, "add usb device,serial is " + newDevice.SerialNumber + (isFirstConnect ? "  is FirstConnect" : "  not FirstConnect"));
            mDeviceList.add(newDevice);
            if (isFirstConnect) {
                mStorage.saveDevice(newDevice);
                hintUserSelect();
            }
            notifyDeviceList();
        }
    }

    synchronized private void removeDeviceFromList(BluetoothDevice device) {
        for (DeviceInfo localDevice : mDeviceList) {
            if (localDevice.match(device.getAddress(), DeviceInfo.ConnectType_WIFI)) {
                localDevice.ConnectionType -= DeviceInfo.ConnectType_WIFI;
                if (localDevice.ConnectionType == DeviceInfo.ConnectType_None) {
                    mDeviceList.remove(localDevice);
                    mStorage.deleteDevice(localDevice);
                } else {
                    localDevice.BluetoothMac = "";
                }
                notifyDeviceList();
            }
        }
    }

    synchronized public void removeDeviceFromList(UsbDevice device) {
        if (CarHelper.isOpenQDLink()) return;
        Log.d(TAG, "removeDeviceFromList() called with: device = [" + device.getSerialNumber() + "]");
        for (DeviceInfo localDevice : mDeviceList) {
            Log.i(TAG, "removeDeviceFromList " + device.getSerialNumber());
            if (localDevice.match(device.getSerialNumber(), DeviceInfo.ConnectType_USB)) {
                localDevice.ConnectionType -= DeviceInfo.ConnectType_USB;
                if (localDevice.ConnectionType == DeviceInfo.ConnectType_None) {
                    mDeviceList.remove(localDevice);
                    //mStorage.deleteDevice(localDevice);
                } else {
                    localDevice.SerialNumber = "";
                }
                notifyDeviceList();
            }
        }
    }

    synchronized private void removeDeviceFromList(String btMacAddress) {
        for (DeviceInfo localDevice : mDeviceList) {
            if (localDevice.match(btMacAddress, DeviceInfo.ConnectType_WIFI)) {
                localDevice.ConnectionType -= DeviceInfo.ConnectType_WIFI;
                if (localDevice.ConnectionType == DeviceInfo.ConnectType_None) {
                    mDeviceList.remove(localDevice);
                    mStorage.deleteDevice(localDevice);
                } else {
                    localDevice.BluetoothMac = "";
                }
                notifyDeviceList();
            }
        }
    }

    private boolean isDeviceCarPlayPossible(UsbDevice device) {
        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        UsbDeviceConnection connection = UsbUtil.openConnection(usbManager, device);
        boolean carplaySupported = AppSupport.isCarPlaySupport(mContext, device, connection);
        if (connection != null) {
            connection.close();
        } else {
            Log.d(TAG, "isDeviceCarPlayPossible: UsbDeviceConnection is null");
        }

        return carplaySupported;
    }

    // iap2 bt认证后，如果不支持CarPlay，从device list删除
    // iap2 usb认证后，只合并重复的deviceInfo
    synchronized public void updateDeviceList(String btMac, String serial, boolean isSupportCarPlay) {
        int cnt = 0;
        DeviceInfo btMatchDevice = null;
        DeviceInfo serialMatchDevice = null;
        for (DeviceInfo localDevice : mDeviceList) {
            if (!isSupportCarPlay) {
                Log.i(TAG, "device don't support carplay,remove it from list");

                if (ObjectsCompat.equals(localDevice.BluetoothMac, btMac)
                        || ObjectsCompat.equals(localDevice.SerialNumber, serial)) {
                    mDeviceList.remove(localDevice);
                    mStorage.deleteDevice(localDevice);
                }
            } else {
                if (Objects.equals(localDevice.BluetoothMac, btMac)) {
                    if (cnt >= 1) {
                        mDeviceList.remove(localDevice);
                        mStorage.deleteDevice(localDevice);
                    } else {
                        localDevice.SerialNumber = serial;
                        localDevice.ConnectionType |= DeviceInfo.ConnectType_USB;
                    }

                    cnt++;
                    break;
                }
                if (Objects.equals(localDevice.SerialNumber, serial)) {
                    if (cnt >= 1) {
                        mDeviceList.remove(localDevice);
                        mStorage.deleteDevice(localDevice);
                    } else {
                        localDevice.BluetoothMac = btMac;
                        localDevice.ConnectionType |= DeviceInfo.ConnectType_WIFI;
                    }
                    cnt++;
                    break;
                }
            }
        }
    }

    synchronized public boolean isBtMacInDeviceList(String btMac) {
        Log.i(TAG, "isBtMacInDeviceList");
        showListInfos();
        boolean ret = false;
        for (DeviceInfo localDevice : mDeviceList) {
            if (Objects.equals(localDevice.BluetoothMac, btMac)) {
                Log.i(TAG, "find corresponding btmac:" + btMac);
                ret = true;
                break;
            }
        }

        return ret;
    }

    synchronized public boolean isSerialInDeviceList(String serialNumber) {
        Log.i(TAG, "isSerialInDeviceList");
        showListInfos();
        boolean ret = false;
        for (DeviceInfo localDevice : mDeviceList) {
            if (Objects.equals(localDevice.SerialNumber, serialNumber)) {
                Log.i(TAG, "find corresponding serial number:" + serialNumber);
                ret = true;
                break;
            }
        }

        return ret;
    }


}