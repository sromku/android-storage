package com.snatik.storage.app

import android.app.Application

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }

    companion object {

        var TAG = "MyApplication"
    }
}
