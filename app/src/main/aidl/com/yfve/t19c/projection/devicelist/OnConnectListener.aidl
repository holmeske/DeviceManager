package com.yfve.t19c.projection.devicelist;

import com.yfve.t19c.projection.devicelist.Device;

interface OnConnectListener {

     void onDeviceUpdate(in Device device);

     void onNotification(int id, String content, String serial, String mac, int connectType);

     void onRequestBluetoothPair(String mac);

     void onSessionStateUpdate(String serial, String mac, int state, String msg);

}