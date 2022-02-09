// DeviceListManager.aidl
package com.yfve.t19c.projection.devicelist;

import com.yfve.t19c.projection.devicelist.OnConnectListener;
import com.yfve.t19c.projection.devicelist.Device;

interface DeviceListManager {

    void registerListener(OnConnectListener listener);

    void unregisterListener(OnConnectListener listener);

    void projectionScreen(int connectType, String serialNumber, String btMac);

    List<Device> getList();

    void startSession();
}