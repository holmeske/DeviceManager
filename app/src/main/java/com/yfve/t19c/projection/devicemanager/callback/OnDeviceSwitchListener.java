package com.yfve.t19c.projection.devicemanager.callback;

public interface OnDeviceSwitchListener {
    void startSession(int connectType, String serialNumber, String btMac);
}
