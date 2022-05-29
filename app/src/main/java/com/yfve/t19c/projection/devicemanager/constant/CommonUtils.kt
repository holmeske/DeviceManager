package com.yfve.t19c.projection.devicemanager.constant

import android.text.TextUtils
import com.google.gson.Gson
import com.yfve.t19c.projection.devicelist.Device

fun toJson(src: Any?): String? {
    return Gson().toJson(src)
}

fun contains(list: List<Device>, serial: String): Boolean {
    return list.any {
        it.serial == serial
    }
}

fun isEmpty(value: String): Boolean {
    return TextUtils.isEmpty(value) || TextUtils.equals("null", value)
}