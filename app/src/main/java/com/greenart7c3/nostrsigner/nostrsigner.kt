package com.greenart7c3.nostrsigner

import android.app.Application
import com.greenart7c3.nostrsigner.database.AppDatabase
import java.util.concurrent.ConcurrentHashMap

class nostrsigner : Application() {
    var databases = ConcurrentHashMap<String, AppDatabase>()
    override fun onCreate() {
        super.onCreate()
        instance = this

        LocalPreferences.allSavedAccounts().forEach {
            databases[it.npub] = AppDatabase.getDatabase(this, it.npub)
        }
    }

    companion object {
        lateinit var instance: nostrsigner
            private set
    }
}
