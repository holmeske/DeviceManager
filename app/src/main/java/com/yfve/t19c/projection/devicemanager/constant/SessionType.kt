package com.yfve.t19c.projection.devicemanager.constant

object SessionType {
    const val USB_AA = 1
    const val WIFI_AA = 2
    const val USB_CP = 3
    const val WIFI_CP = 4
}

data class Phone(var serial: String? = "", var mac: String? = "")