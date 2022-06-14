package com.yfve.t19c.projection.devicemanager;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import java.util.List;

public class DeviceListHelper {
    private static final String TAG = "DeviceListHelper";
    private final StorageHelper mStorageHelper;

    public DeviceListHelper(Context c) {
        mStorageHelper = new StorageHelper(c);
        /*DeviceInfo info = new DeviceInfo();
        info.BluetoothMac = "123";
        String s = info.BluetoothMac;

        info.BluetoothMac = "null";
        Log.d(TAG, "s: " + s);

        info = null;
        Log.d(TAG, "s: " + s);*/
    }

    public void write(String name, String serial, String mac, int ability) {
        Log.d(TAG, "write() called with: name = [" + name + "], serial = [" + serial + "], mac = [" + mac + "], ability = [" + ability + "]");
        if (TextUtils.isEmpty(serial) && TextUtils.isEmpty(mac)) {
            Log.e(TAG, "serial and mac are both empty , do not write to database");
        } else {
            Device device = query(serial, mac);
            Log.d(TAG, "query device : " + new Gson().toJson(device));

            if (device != null) {
                int historyAbility = device.getAbility();

                if (TextUtils.isEmpty(name)) name = device.getName();
                if (TextUtils.isEmpty(serial)) serial = device.getSerial();
                if (TextUtils.isEmpty(mac)) mac = device.getMac();

                if (ability != historyAbility) {
                    if (ability == 1 || ability == 2) {// 1:usb android auto , 2: wifi android auto
                        if (historyAbility != 3) {
                            deleteBySerialMac(device.getSerial(), device.getMac());
                            insert(name, serial, mac, 3);
                        }
                    } else if (ability == 4 || ability == 8) {// 4:usb carplay , 8: wifi carplay
                        if (historyAbility != 12) {
                            deleteBySerialMac(device.getSerial(), device.getMac());
                            insert(name, serial, mac, 12);
                        }
                    }
                }
            } else {
                if (queryAll().size() == 5) {
                    delete(queryAll().get(0));
                }
                insert(name, serial, mac, ability);
            }
            read();
        }
    }

    private void insert(String name, String serial, String mac, int ability) {
        mStorageHelper.insert(new Device(name, serial, mac, ability));
    }

    public void deleteByMac(String mac) {
        mStorageHelper.deleteByMac(mac);
    }

    public void deleteBySerial(String serial) {
        mStorageHelper.deleteBySerial(serial);
    }

    public void deleteBySerialMac(String serial, String mac) {
        mStorageHelper.deleteBySerialMac(serial, mac);
    }

    public void delete(Device device) {
        mStorageHelper.delete(device);
    }

    public void update(String name, String serial, String mac, int ability) {
        mStorageHelper.update(new Device(name, serial, mac, ability));
    }

    public Device query(String serial, String mac) {
        if (!TextUtils.isEmpty(serial) && !TextUtils.isEmpty(mac)) {
            return mStorageHelper.query(serial, mac);
        } else if (!TextUtils.isEmpty(serial) && TextUtils.isEmpty(mac)) {
            return mStorageHelper.queryBySerial(serial);
        } else if (TextUtils.isEmpty(serial) && !TextUtils.isEmpty(mac)) {
            return mStorageHelper.queryByMac(mac);
        } else {
            return null;
        }
    }

    public String getMac(String serial) {
        Log.d(TAG, "getMac() called with: serial = [" + serial + "]");
        return mStorageHelper.queryBySerial(serial).getMac();
    }

    public String getSerial(String mac) {
        Log.d(TAG, "getSerial() called with: mac = [" + mac + "]");
        String serial = mStorageHelper.queryByMac(mac).getSerial();
        Log.d(TAG, "getSerial: " + serial);
        return serial;
    }

    public List<Device> queryAll() {
        return mStorageHelper.queryAll();
    }

    public void read() {
        queryAll().forEach(d -> Log.d(TAG, d.toString()));
    }

    public void clear() {
        Log.d(TAG, "clear() called");
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
//        write("7", "7", "7", 2);
        clear();
//        write("11111111", "11111111", "", 1);
//        write("1:1:1:1:1:1:1:1", "", "1:1:1:1:1:1:1:1", 2);
//        write("2:2:2:2:2:2:2:2", "", ":2:2:2:2:2:2:2", 8);
//        write("iphone", "22222222", ":2:2:2:2:2:2:2", 4);
        read();
//        CacheHelperKt.saveLastConnectDeviceInfo(c, "name", "serial", "mac", 3);
        //Log.d(TAG, "test: " + CommonUtilsKt.toJson(CacheHelperKt.getLastConnectDeviceInfo(c)));
    }

}
