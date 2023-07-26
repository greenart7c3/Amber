package com.greenart7c3.nostrsigner

import android.app.Application

class nostrsigner : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: nostrsigner
            private set
    }
}
