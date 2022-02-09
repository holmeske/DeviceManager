// DeviceListManager.aidl
package com.yfve.t19c.projection.devicelist;

import com.yfve.t19c.projection.devicelist.Device;

interface OnConnectListener {

     void update(in Device device);

     void onNotification(int id, String content);

}