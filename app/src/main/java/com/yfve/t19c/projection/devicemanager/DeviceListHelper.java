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

        Device device = query(serial, mac);
        Log.d(TAG, "query device : " + new Gson().toJson(device));

        if (device != null) {
            int historyAbility = device.getAbility();
            if (ability != historyAbility) {
                if (ability == 1 || ability == 2) {// 1:usb android auto , 2: wifi android auto
                    if (historyAbility != 3) {
                        update(name, serial, mac, 3);
                    }
                } else if (ability == 4 || ability == 8) {// 4:usb carplay , 8: wifi carplay
                    if (historyAbility != 12) {
                        update(name, serial, mac, 12);
                    }
                }
            }
        } else {
            if (queryAll().size() == 5) {
                delete(queryAll().get(0));
            }
            insert(name, serial, mac, ability);
        }

    }

    private void insert(String name, String serial, String mac, int ability) {
        mStorageHelper.insert(new Device(name, serial, mac, ability));
    }

    public void delete(Device device) {
        mStorageHelper.delete(device);
    }

    public void update(String name, String serial, String mac, int ability) {
        mStorageHelper.update(new Device(name, serial, mac, ability));
    }

    public Device query(String serial, String mac) {
        return mStorageHelper.query(serial, mac);
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

//        write("1", "1", "1", 1);
//        write("2", "2", "2", 1);
//        write("3", "3", "3", 1);
//        write("4", "4", "4", 1);
//        write("5", "5", "5", 1);
//        write("6", "6", "6", 2);
        write("7", "7", "7", 2);
//        clear();
        read();

//        CacheHelperKt.saveLastConnectDeviceInfo(c, "name", "serial", "mac", 3);

        //Log.d(TAG, "test: " + CommonUtilsKt.toJson(CacheHelperKt.getLastConnectDeviceInfo(c)));


    }


}
