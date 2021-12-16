package com.yfve.t19c.projection.devicemanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startForegroundService(Intent(this, DeviceManagerService::class.java))

        Log.i(TAG, "MainActivity Thread == " + Thread.currentThread().id)

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

        //CarHelper(this)

    }

    fun onClick(view: android.view.View) {
        when (view.id) {
            R.id.tv -> {
            }
        }
    }

}