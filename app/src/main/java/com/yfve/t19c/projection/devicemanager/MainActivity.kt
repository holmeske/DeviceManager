package com.yfve.t19c.projection.devicemanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yfve.t19c.projection.devicemanager.database.DeviceDatabase


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        DeviceListHelper(this).test(this)
        startForegroundService(Intent(this, DeviceManagerService::class.java))

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


    private fun db() {

        val migration: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Device ADD COLUMN supportUsbAA Boolean")
            }
        }

        val db =
            Room.databaseBuilder(applicationContext, DeviceDatabase::class.java, "database-device")
                .addMigrations(migration)
                .build()

        Thread {

            db.deviceDao()?.let {
                //it.update(Device("0", "0", "0", true))

                /*it.all?.forEachIndexed { index, device ->
                    Log.d(TAG, "onCreate: $index , $device")
                    if (1==index){
                        it.delete(device)
                    }
                }*/

                it.all?.forEachIndexed { index, device ->
                    Log.d(TAG, "onCreate: $index , $device")
                }

            }

//            val list: MutableList<Device> = ArrayList()
//            list.add(Device("1", "1", "1"))
//            list.add(Device("2", "2", "2"))

        }.start()

    }

    fun onClick(view: android.view.View) {
        when (view.id) {
            R.id.tv -> {
                Log.d(TAG, "onClick: ")
                firstUsbDevice()
            }
        }
    }
}