package com.yfve.t19c.projection.devicemanager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.*

private const val TAG = "USB"
fun Context.getUsbDeviceList(): MutableList<UsbDevice> {
    val deviceList: MutableList<UsbDevice> = ArrayList()
    val usbManager: UsbManager = getSystemService(UsbManager::class.java)
    usbManager.deviceList.forEach { (_, u) ->
        deviceList.add(u)
        //Log.d(TAG, "onCreate:  $t  $u")
    }
    return deviceList
}

fun Context.firstUsbDevice(): UsbDevice? {
    val usbManager: UsbManager = getSystemService(UsbManager::class.java)
    usbManager.deviceList?.let {
        if (it.isNotEmpty()) {
            return it.values.first()
        }
    }
    return null
}

fun Context.UsbManager(): UsbManager? {
    return getSystemService(UsbManager::class.java)
}

fun Context.UsbDeviceList(): HashMap<String, UsbDevice>? {
    return UsbManager()?.deviceList
}

fun Context.queryUsbDevice(serial: String): UsbDevice? {
    UsbDeviceList()?.values?.forEach {
        if (it.serialNumber.equals(serial)) {
            return it
        }
    }
    return null
}

