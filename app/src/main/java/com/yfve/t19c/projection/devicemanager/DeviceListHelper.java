package com.yfve.t19c.projection.devicemanager;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import java.util.List;

public class DeviceListHelper {
    private static final String TAG = "DeviceListHelper";
    private StorageHelper mStorageHelper;

    public DeviceListHelper(Context c) {
        mStorageHelper = new StorageHelper(c);
    }

    public void write(String name, String serial, String mac, int ability) {
        Log.d(TAG, "write() called with: name = [" + name + "], serial = [" + serial + "], mac = [" + mac + "], ability = [" + ability + "]");

        if (TextUtils.isEmpty(serial) || TextUtils.isEmpty(mac)) return;

        Device device = query("serial", "mac");
        Log.d(TAG, "query device : " + new Gson().toJson(device));

        if (device != null) {
            int history = device.getAbility();
            if (ability != history) {
                //1:usb android auto  ,  2: wifi android auto
                if (ability == 1 || ability == 2) {
                    if (history != 3) {
                        update(name, serial, mac, 3);
                    }
                }
                //1:usb carplay  ,  2: wifi carplay
                if (ability == 4 || ability == 8) {
                    if (history != 12) {
                        insert(name, serial, mac, 12);
                    }
                }
            }
        } else {
            insert(name, serial, mac, ability);
        }

    }

    private void insert(String name, String serial, String mac, int ability) {
        mStorageHelper.insert(new Device(name, serial, mac, ability));
    }

    public void delete(String name, String serial, String mac, int ability) {
        mStorageHelper.delete(new Device(name, serial, mac, ability));
    }

    public void update(String name, String serial, String mac, int ability) {
        mStorageHelper.update(new Device(name, serial, mac, ability));
    }

    public Device query(String serial, String mac) {
        return mStorageHelper.query("serial", "mac");
    }

    public List<Device> queryAll() {
        return mStorageHelper.queryAll();
    }

    public void read() {
        List<Device> list = queryAll();
        Log.d(TAG, "read list size = " + list.size());
        list.forEach(d -> {
            Log.d(TAG, d.toString());
        });
    }

    public void clear() {
        mStorageHelper.clear();
    }

    public void test(Context c) {
//        delete("name", "serial", "mac", 2);

        write("name", "serial", "mac", 2);

        //clear();
        read();

//        CacheHelperKt.saveLastConnectDeviceInfo(c, "name", "serial", "mac", 3);

        //Log.d(TAG, "test: " + CommonUtilsKt.toJson(CacheHelperKt.getLastConnectDeviceInfo(c)));
    }


}
