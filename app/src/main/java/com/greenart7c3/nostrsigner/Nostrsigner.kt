package com.greenart7c3.Nostrsigner

import android.app.Application

class Nostrsigner : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Nostrsigner
            private set
    }
}
