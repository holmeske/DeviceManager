package com.yfve.t19c.projection.devicemanager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.*

private const val TAG = "USB"

fun Context.getProjectionDevice(): UsbDevice? {
    Log.d(TAG, "UsbManager DeviceList() size == ${usbManager().deviceList.size}")
    val resolver = DeviceHandlerResolver(this)
    return usbDeviceList().values.stream().filter { device ->
        UsbHostController.isAvailableUsbDevice(device) && (resolver.isSupportAOAP(device) || resolver.isDeviceCarPlayPossible(
            device
        ))
    }.findAny().orElse(null)
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
    Log.d(TAG, "no find attached usb device")
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

