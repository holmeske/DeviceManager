package com.yfve.t19c.projection.devicemanager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.text.TextUtils
import android.util.Log
import com.yfve.t19c.projection.devicemanager.constant.getLastConnectDeviceInfo
import java.util.*

private const val TAG = "USB"

fun Context.firstUsbDevice(): UsbDevice? {
    usbManager().deviceList?.let {
        Log.d(TAG, "UsbManager getDeviceList() size = ${it.size}")
        if (it.isNotEmpty()) {
            return it.values.first()
        }
    }
    return null
}

fun Context.lastUsbDevice(): UsbDevice? {
    Log.d(TAG, "UsbManager getDeviceList() size = ${usbManager().deviceList.size}")
    val d: Device = getLastConnectDeviceInfo()
    for (device in usbDeviceList().values) {
        if (TextUtils.equals(d.serial, device.serialNumber)) {
            return device;
        }
    }
    return null
}

fun Context.usbManager(): UsbManager {
    return getSystemService(UsbManager::class.java)
}

fun Context.usbDeviceList(): HashMap<String, UsbDevice> {
    return usbManager().deviceList
}

fun Context.queryUsbDevice(serial: String?): UsbDevice? {
    Log.d(TAG, "query attached usb device list")
    usbDeviceList().values.forEach {
        if (Objects.equals(it.serialNumber, serial)) return it
    }
    return null
}

