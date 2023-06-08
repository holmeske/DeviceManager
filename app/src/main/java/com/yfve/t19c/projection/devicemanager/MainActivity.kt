package com.yfve.t19c.projection.devicemanager

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onClick(view: android.view.View) {
        when (view.id) {
            R.id.tv -> {
                Log.d(TAG, "onClick: ")
            }
        }
    }
}