package com.yfve.t19c.projection.devicemanager

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.yfve.t19c.projection.devicemanager.constant.toHexString


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DeviceListHelper(this).test(this)

        //DeviceManagerService.filteredAliveDeviceList()
        Log.d(TAG, "onCreate: " + toHexString("156:227:63:124:4:126"))

        //stopService(Intent(this, DeviceManagerService::class.java))
//        startForegroundService(Intent(this, DeviceManagerService::class.java))

//        Car.createCar(this, null, 10000) { car, b ->
//            Log.d(TAG, "car == null : ${car == null}")
//            Log.d(TAG, "b :$b")
//        }

        //Log.i(TAG, "MainActivity Thread == " + Thread.currentThread().id)

        /*getUsbDeviceList().forEach {
            Log.d(TAG, "${it.serialNumber}")
            val mUsbManager = getSystemService(USB_SERVICE) as UsbManager
            if (DeviceHandlerResolver(this, mUsbManager).isDeviceAoapPossible(it)) {
                Log.d(TAG, "onCreate: support aa")
            } else {
                Log.d(TAG, "onCreate:  not support aa")
            }
        }*/

        /*Thread {
            LocationHelper(this, 0x01).start()
        }.start()*/

        //firstUsbDevice()

//        val job = GlobalScope.launch {
//            println("Hello")
//        }

        //db()
    }

    fun onClick(view: android.view.View) {
        when (view.id) {
            R.id.tv -> {
                Log.d(TAG, "onClick: ")
            }
        }
    }
}