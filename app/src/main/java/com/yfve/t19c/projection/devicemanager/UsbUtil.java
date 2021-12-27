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

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class UsbUtil {
    private static final String TAG = "UsbUtil";

    public static List<UsbDevice> findAllPossibleAndroidDevices(Context context, UsbManager usbManager) {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        ArrayList<UsbDevice> androidDevices = new ArrayList<>(devices.size());
        for (UsbDevice device : devices.values()) {
            UsbDeviceConnection connection = openConnection(usbManager, device);
            if (AppSupport.isAOASupported(context, device, connection)) {
                androidDevices.add(device);
            }
            if (connection != null) {
                connection.close();
            } else {
                Log.d(TAG, "findAllPossibleAndroidDevices: UsbDeviceConnection is null");
            }
        }
        return androidDevices;
    }

    public static void sendAoapAccessoryStart(UsbDeviceConnection connection, String manufacturer,
                                              String model, String description, String version, String uri, String serial) throws IOException {
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_MANUFACTURER, manufacturer);
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_MODEL, model);
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_DESCRIPTION, description);
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_VERSION, version);
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_URI, uri);
        AppSupport.sendString(connection, AppSupport.ACCESSORY_STRING_SERIAL, serial);
        AppSupport.sendAoapStart(connection);
    }

    @Nullable
    public static UsbDeviceConnection openConnection(UsbManager manager, UsbDevice device) {
        //manager.grantPermission(device);
        return manager.openDevice(device);
    }

    public static boolean isTheSameDevice(UsbDevice l, UsbDevice r) {
        return TextUtils.equals(l.getManufacturerName(), r.getManufacturerName())
                && TextUtils.equals(l.getProductName(), r.getProductName())
                && TextUtils.equals(l.getSerialNumber(), r.getSerialNumber());
    }

    public static boolean isDevicesMatching(UsbDevice l, UsbDevice r) {
        return l.getVendorId() == r.getVendorId() && l.getProductId() == r.getProductId()
                && TextUtils.equals(l.getSerialNumber(), r.getSerialNumber());
    }

    public static boolean isDeviceConnected(UsbManager usbManager, UsbDevice device) {
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice dev : devices.values()) {
            if (isDevicesMatching(dev, device)) {
                return true;
            }
        }
        return false;
    }


}
