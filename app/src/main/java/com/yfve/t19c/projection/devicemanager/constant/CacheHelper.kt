package com.yfve.t19c.projection.devicemanager.constant

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.yfve.t19c.projection.devicemanager.Device
import java.util.*

private const val TAG = "CacheHelper"

fun contains(list: List<Phone>, mac: String): Boolean {
    return list.any {
        it.mac == mac
    }
}

fun find(list: List<Phone>, mac: String): Phone? {
    return list.find {
        it.mac == mac
    }
}

fun toHexString(mac: String?): String {
    if (TextUtils.isEmpty(mac)) {
        return ""
    }
    return try {
        //val mac = "160:59:227:164:74:196"
        val strings = mac?.split(":".toRegex())?.toTypedArray()
        val sb = StringBuilder()
        for (s in strings!!) {
            val sixteen = Integer.toHexString(s.toInt())
            println(sixteen.uppercase(Locale.getDefault()))
            sb.append(sixteen.uppercase(Locale.getDefault())).append(":")
        }
        Log.d(TAG, "toHexString: ${sb.substring(0, sb.length - 1)}")
        sb.substring(0, sb.length - 1)
    } catch (e: Exception) {
        Log.e(TAG, "toHexString: ", e)
        ""
    }
}

fun Context.saveLastConnectDeviceInfo(
    name: String,
    serial: String,
    mac: String,
    ability: Int
) {
    if (TextUtils.isEmpty(serial) || TextUtils.isEmpty(mac)) return
    getSharedPreferences("last_connect_device", Context.MODE_PRIVATE).edit()
        .putString("name", name).putString("serial", serial).putString("mac", mac)
        .putInt("ability", ability)
        .apply()
    //getSharedPreferences("last_connect_device", Context.MODE_PRIVATE).edit().clear().apply()
}

fun Context.getLastConnectDeviceInfo(): Device {
    val device: Device
    val sp = getSharedPreferences("last_connect_device", Context.MODE_PRIVATE)
    device = Device(
        sp.getString("name", ""),
        sp.getString("serial", ""),
        sp.getString("mac", ""),
        sp.getInt("ability", 0)
    )
    return device
}