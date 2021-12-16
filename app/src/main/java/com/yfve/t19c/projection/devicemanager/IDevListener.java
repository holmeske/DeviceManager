package com.yfve.t19c.projection.devicemanager;

import java.util.List;

public class IDevListener {
    void onNotifyDeviceLists(List<DeviceInfos> deviceLists) {
    }

    void optionsUpdated(List<Settings> settingLists) {
    }

    void onNotifySessionStatus(int appType, boolean sessionSts, String btMac) {
    }
}
