package com.example.gcadapter

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {

    // `external` = implemented in C (native-lib.c), resolved by JNI at runtime.
    private external fun nativeHello(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val msg = nativeHello()
        Log.i(TAG, msg)

        setContentView(TextView(this).apply {
            text = msg
            textSize = 20f
            setPadding(48, 48, 48, 48)
        })
    }

    companion object {
        private const val TAG = "GcAdapter"

        init {
            // Loads libgcadapter.so, which CMake builds from src/main/cpp.
            System.loadLibrary("gcadapter")
        }
    }
}
