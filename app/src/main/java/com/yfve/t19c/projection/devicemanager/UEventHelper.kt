package com.yfve.t19c.projection.devicemanager

import android.os.UEventObserver
import android.util.Log

private const val TAG = "UsbHelper"

class UsbHelper {

    //blue

    //{SUBSYSTEM=usb, MAJOR=189, BUSNUM=001, SEQNUM=2301, ACTION=add, DEVNAME=bus/usb/001/005, DEVTYPE=usb_device, PRODUCT=4e8/6860/400, MINOR=4,
    // DEVPATH=/devices/platform/soc/soc:usb3@31260000/31260000.dwc3/xhci-hcd.0.auto/usb1/1-1/1-1.1, TYPE=0/0/0, DEVNUM=005}

    //{SUBSYSTEM=usb, SEQNUM=2302, ACTION=add, INTERFACE=6/1/1, DEVTYPE=usb_interface, PRODUCT=4e8/6860/400, MODALIAS=usb:v04E8p6860d0400dc00dsc00dp00ic06isc01ip01in00,
    // DEVPATH=/devices/platform/soc/soc:usb3@31260000/31260000.dwc3/xhci-hcd.0.auto/usb1/1-1/1-1.1/1-1.1:1.0, TYPE=0/0/0}

    //black

    //{SUBSYSTEM=usb, MAJOR=189, BUSNUM=003, SEQNUM=2420, ACTION=add, DEVNAME=bus/usb/003/002, DEVTYPE=usb_device, PRODUCT=4e8/6860/400,
    // MINOR=257, DEVPATH=/devices/platform/soc/soc:usb3@31120000/31220000.dwc3/xhci-hcd.1.auto/usb3/3-1, TYPE=0/0/0, DEVNUM=002}

    //{SUBSYSTEM=usb, SEQNUM=2421, ACTION=add, INTERFACE=6/1/1, DEVTYPE=usb_interface, PRODUCT=4e8/6860/400, MODALIAS=usb:v04E8p6860d0400dc00dsc00dp00ic06isc01ip01in00,
    // DEVPATH=/devices/platform/soc/soc:usb3@31120000/31220000.dwc3/xhci-hcd.1.auto/usb3/3-1/3-1:1.0, TYPE=0/0/0}

    private val match = "DEVPATH="

    // 注册成功后，一旦udev返回的事件中包含"DEVPATH=/devices/platform/usb/usb1/1-1"，就调用下面的回调，把这个事件提供给应用
    private val uEventObserver = object : UEventObserver() {
        override fun onUEvent(uEvent: UEvent?) {
            //Log.d(TAG, "onUEvent() called with: uEvent = $uEvent")
            val action = uEvent?.get("ACTION") ?: return
            val devPath = uEvent.get("DEVPATH") ?: return

            if (devPath == "/devices/platform/soc/soc:usb3@31120000/31220000.dwc3/xhci-hcd.1.auto/usb3/3-1") {
                currentPort = 1
            } else if (devPath == "/devices/platform/soc/soc:usb3@31260000/31260000.dwc3/xhci-hcd.0.auto/usb1/1-1/1-1.1") {
                currentPort = 2
            }

            when (action) {
                "add" -> {
                    if (devPath == "/devices/platform/soc/soc:usb3@31120000/31220000.dwc3/xhci-hcd.1.auto/usb3/3-1") {
                        Log.d(TAG, "add  black  port")
                    } else if (devPath == "/devices/platform/soc/soc:usb3@31260000/31260000.dwc3/xhci-hcd.0.auto/usb1/1-1/1-1.1") {
                        Log.d(TAG, "add  blue  port")
                    }
                }
                "remove" -> {
                    if (devPath == "/devices/platform/soc/soc:usb3@31120000/31220000.dwc3/xhci-hcd.1.auto/usb3/3-1") {
                        Log.d(TAG, "remove  black  port")
                    } else if (devPath == "/devices/platform/soc/soc:usb3@31260000/31260000.dwc3/xhci-hcd.0.auto/usb1/1-1/1-1.1") {
                        Log.d(TAG, "remove  blue  port")
                    }

                }
            }
        }
    }

    fun isBluePort(): Boolean {
        return currentPort == 2
    }

    private var currentPort: Int = 0

    init {
        Log.d(TAG, "EventObserver.startObserving() called")
        uEventObserver.startObserving(match)
    }

}