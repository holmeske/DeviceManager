package com.yfve.t19c.projection.devicemanager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
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

fun Context.getProjectionDevice(): UsbDevice? {
    Log.d(TAG, "UsbManager getDeviceList() size = ${usbManager().deviceList.size}")
    val deviceHandlerResolver = DeviceHandlerResolver(this)

    return usbDeviceList().values.stream()
        .filter { device ->
            device?.serialNumber?.contains(".") == false
                    && device.serialNumber?.contains("-") == false
                    && (deviceHandlerResolver.isSupportAOAP(device)
                    || deviceHandlerResolver.isDeviceCarPlayPossible(device)
                    )
        }.findAny().orElse(null)
}

fun Context.lastUsbDevice(): UsbDevice? {
    Log.d(TAG, "UsbManager getDeviceList() size = ${usbManager().deviceList.size}")
    val d: Device = getLastConnectDeviceInfo()
    Log.d(TAG, "LastConnectDeviceInfo = " + Gson().toJson(d))
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

fun Context.containsInAttachedUsbDeviceList(serial: String?): Boolean {
    var isAttached = false
    usbDeviceList().values.forEach {
        if (Objects.equals(it.serialNumber, serial)) isAttached = true
    }
    if (!isAttached) Log.d(TAG, "$serial, not contains in attached UsbDevice list")
    return isAttached
}

