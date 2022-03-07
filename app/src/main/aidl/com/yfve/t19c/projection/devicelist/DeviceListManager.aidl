package com.yfve.t19c.projection.devicelist;

import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicelist.Device;

interface DeviceListManager {

    void registerListener(OnConnectListener listener);

    void unregisterListener(OnConnectListener listener);

    void startSession(String serial, String mac, int connectType);

    List<Device> getAliveDevices();

    List<Device> getHistoryDevices();

    void onBluetoothPairResult(String mac, int result);

}