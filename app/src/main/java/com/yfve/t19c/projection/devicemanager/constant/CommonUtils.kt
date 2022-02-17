package com.yfve.t19c.projection.devicemanager.constant

import com.google.gson.Gson

fun toJson(src: Any?): String? {
    return Gson().toJson(src)
}