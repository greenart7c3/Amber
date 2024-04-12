package com.greenart7c3.nostrsigner

import android.app.Application
import com.greenart7c3.nostrsigner.database.AppDatabase

class nostrsigner : Application() {
    lateinit var database: AppDatabase
    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
    }

    companion object {
        lateinit var instance: nostrsigner
            private set
    }
}
