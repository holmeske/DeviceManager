package com.yfve.t19c.projection.devicemanager.constant

object SessionType {
    const val USB_ANDROID_AUTO = 1
    const val WIFI_ANDROID_AUTO = 2
    const val USB_CARPLAY = 3
    const val WIFI_CARPLAY = 4
}

data class Phone(var serial: String? = "", var mac: String? = "") {
    fun clear() {
        serial = ""
        mac = ""
    }

    fun update(serial: String? = "", mac: String? = "") {
        this.serial = serial
        this.mac = mac
    }
}
