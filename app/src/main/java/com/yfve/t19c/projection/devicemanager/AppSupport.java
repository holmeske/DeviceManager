/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yfve.t19c.projection.devicemanager;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AppSupport {
    /**
     * Indexes for strings sent by the host via ACCESSORY_SEND_STRING
     */
    public static final int ACCESSORY_STRING_MANUFACTURER = 0;
    public static final int ACCESSORY_STRING_MODEL = 1;
    public static final int ACCESSORY_STRING_DESCRIPTION = 2;
    public static final int ACCESSORY_STRING_VERSION = 3;
    public static final int ACCESSORY_STRING_URI = 4;
    public static final int ACCESSORY_STRING_SERIAL = 5;
    /**
     * Control request for retrieving device's protocol version
     * <p>
     * requestType:    USB_DIR_IN | USB_TYPE_VENDOR
     * request:        ACCESSORY_GET_PROTOCOL
     * value:          0
     * index:          0
     * data            version number (16 bits little endian)
     * 1 for original accessory support
     * 2 adds HID and device to host audio support
     */
    public static final int ACCESSORY_GET_PROTOCOL = 51;
    /**
     * Control request for host to send a string to the device
     * <p>
     * requestType:    USB_DIR_OUT | USB_TYPE_VENDOR
     * request:        ACCESSORY_SEND_STRING
     * value:          0
     * index:          string ID
     * data            zero terminated UTF8 string
     * <p>
     * The device can later retrieve these strings via the
     * ACCESSORY_GET_STRING_* ioctls
     */
    public static final int ACCESSORY_SEND_STRING = 52;
    /**
     * Control request for starting device in accessory mode.
     * The host sends this after setting all its strings to the device.
     * <p>
     * requestType:    USB_DIR_OUT | USB_TYPE_VENDOR
     * request:        ACCESSORY_START
     * value:          0
     * index:          0
     * data            none
     */
    public static final int ACCESSORY_START = 53;
    /**
     * Max payload size for AOAP. Limited by driver.
     */
    public static final int MAX_PAYLOAD_SIZE = 16384;
    /**
     * Accessory write timeout.
     */
    public static final int AOAP_TIMEOUT_MS = 50;
    public static final int APPLE_DEVICE_VENDOR_ID = 0x05AC;
    public static final int APPLE_DEVICE_PRODUCT_ID_MASK = 0x1200; // only upper two bytes
    @Direction
    public static final int WRITE = 1;
    @Direction
    public static final int READ = 2;
    /**
     * Set of all accessory mode vendor IDs
     */
    private static final ArraySet<Integer> USB_ACCESSORY_VENDOR_ID = new ArraySet<Integer>();
    /**
     * Set of all accessory mode product IDs
     */
    private static final ArraySet<Integer> USB_ACCESSORY_MODE_PRODUCT_ID = new ArraySet<>(4);
    private static final String TAG = AppSupport.class.getSimpleName();
    /**
     * Set of VID:PID pairs blacklisted through config_AoapIncompatibleDeviceIds. Only
     * isDeviceBlacklisted() should ever access this variable.
     */
    private static Set<Pair<Integer, Integer>> sBlacklistedVidPidPairs;

    static {
        USB_ACCESSORY_VENDOR_ID.add(0x18d1); // Google
        USB_ACCESSORY_VENDOR_ID.add(0x04E8); // Samsung
        USB_ACCESSORY_VENDOR_ID.add(0x22b8); // Motorola
        USB_ACCESSORY_VENDOR_ID.add(0x0bb4); // HTC
        USB_ACCESSORY_VENDOR_ID.add(0x1004); // LGE
    }

    static {
        USB_ACCESSORY_MODE_PRODUCT_ID.add(0x2D00);
        USB_ACCESSORY_MODE_PRODUCT_ID.add(0x2D01);
        USB_ACCESSORY_MODE_PRODUCT_ID.add(0x2D04);
        USB_ACCESSORY_MODE_PRODUCT_ID.add(0x2D05);
    }

    public static int getProtocol(UsbDeviceConnection conn) {
        Log.d(TAG, "getProtocol() called");
        byte[] buffer = new byte[2];

        int len = transfer(conn, READ, ACCESSORY_GET_PROTOCOL, 0, buffer, buffer.length);
        if (len == 0) {
            return -1;
        }
        if (len < 0) {
            Log.w(TAG, "getProtocol() failed. Retrying...");
            len = transfer(conn, READ, ACCESSORY_GET_PROTOCOL, 0, buffer, buffer.length);
            if (len != buffer.length) {
                return -1;
            }
        }
        return (buffer[1] << 8) | buffer[0];
    }

    private static boolean getCarPlaySupport(UsbDeviceConnection conn) {
        byte[] rev = new byte[4];
        int ret = conn.controlTransfer(0xC0,              //requestType
                0x53,              // request
                0x00,              // value
                0x00,              // index
                rev,                    // buffer
                4,  // length
                1000);               // timeout

        if (ret < 0) return false;

        return rev[0] == 1;
    }

    public static boolean isIOSDevice(UsbDevice device) {
        //return (device.getVendorId() == APPLE_DEVICE_VENDOR_ID) && ((device.getProductId() & 0xff00) == APPLE_DEVICE_PRODUCT_ID_MASK);
        if (device == null) {
            Log.d(TAG, "device is null");
            return false;
        }
        if (device.getProductName() != null && device.getProductName().toLowerCase(Locale.ROOT).startsWith("iphone")) {
            return true;
        }
        if (device.getManufacturerName() != null && device.getManufacturerName().toLowerCase(Locale.ROOT).startsWith("apple")) {
            return true;
        }
        if (device.getVendorId() == APPLE_DEVICE_VENDOR_ID) {
            return true;
        } else {
            Log.d(TAG, "is not ios device");
            return false;
        }
    }

    public static boolean isAOASupported(Context context, UsbDevice device, UsbDeviceConnection conn) {
        return !isDeviceBlacklisted(context, device) && getProtocol(conn) >= 1;
    }

    public static boolean isCarPlaySupport(Context context, UsbDevice device, UsbDeviceConnection conn) {
        if (conn == null) {
            Log.d(TAG, "isCarPlaySupport: UsbDeviceConnection is null");
            return false;
        }
        return !isDeviceBlacklisted(context, device) && isIOSDevice(device) && getCarPlaySupport(conn);
    }

    public static void sendString(UsbDeviceConnection conn, int index, String string) throws IOException {
        byte[] buffer = (string + "\0").getBytes();
        int len = transfer(conn, WRITE, ACCESSORY_SEND_STRING, index, buffer, buffer.length);
        if (len != buffer.length) {
            Log.w(TAG, "sendString for " + index + ":" + string + " failed. Retrying...");
            len = transfer(conn, WRITE, ACCESSORY_SEND_STRING, index, buffer, buffer.length);
            if (len != buffer.length) {
                throw new IOException("Failed to send string " + index + ": \"" + string + "\"");
            }
        } else {
            Log.i(TAG, "Sent string " + index + ": \"" + string + "\"");
        }
    }

    public static void sendAoapStart(UsbDeviceConnection conn) throws IOException {
        int len = transfer(conn, WRITE, ACCESSORY_START, 0, null, 0);
        if (len < 0) {
            throw new IOException("Control transfer for accessory start failed: " + len);
        }
    }

    public static boolean isDeviceInAoapMode(UsbDevice device) {
        if (device == null) {
            return false;
        }
        final int vid = device.getVendorId();
        final int pid = device.getProductId();
        if (USB_ACCESSORY_VENDOR_ID.contains(vid) && USB_ACCESSORY_MODE_PRODUCT_ID.contains(pid)) {
            Log.d(TAG, "device is in AOA mode");
        } else {
            Log.d(TAG, "device is not in AOA mode");
        }
        return USB_ACCESSORY_VENDOR_ID.contains(vid) && USB_ACCESSORY_MODE_PRODUCT_ID.contains(pid);
    }

    public static synchronized boolean isDeviceBlacklisted(Context context, UsbDevice device) {
        if (sBlacklistedVidPidPairs == null) {
            sBlacklistedVidPidPairs = new HashSet<>();
            /*
            String[] idPairs =
                context.getResources().getStringArray(R.array.config_AoapIncompatibleDeviceIds);
            for (String idPair : idPairs) {
                boolean success = false;
                String[] tokens = idPair.split(":");
                if (tokens.length == 2) {
                    try {
                        sBlacklistedVidPidPairs.add(Pair.create(Integer.parseInt(tokens[0], 16),
                                                                Integer.parseInt(tokens[1], 16)));
                        success = true;
                    } catch (NumberFormatException e) {
                    }
                }
                if (!success) {
                    Log.e(TAG, "config_AoapIncompatibleDeviceIds contains malformed value: "
                            + idPair);
                }
            }
            */
        }

        return sBlacklistedVidPidPairs.contains(Pair.create(device.getVendorId(), device.getProductId()));
    }

    private static int transfer(UsbDeviceConnection conn, @Direction int direction, int string, int index, byte[] buffer, int length) {
        int directionConstant;
        switch (direction) {
            case READ:
                directionConstant = UsbConstants.USB_DIR_IN;
                break;
            case WRITE:
                directionConstant = UsbConstants.USB_DIR_OUT;
                break;
            default:
                Log.w(TAG, "Unknown direction for transfer: " + direction);
                return -1;
        }
        if (conn == null) {
            Log.d(TAG, "transfer: UsbDeviceConnection is null");
            return -1;
        }
        return conn.controlTransfer(directionConstant | UsbConstants.USB_TYPE_VENDOR, string, 0,
                index, buffer, length, AOAP_TIMEOUT_MS);
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    public @interface Direction {
    }
}
