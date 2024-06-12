package com.greenart7c3.nostrsigner

import android.app.Application
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.Relay
import java.util.concurrent.ConcurrentHashMap

class NostrSigner : Application() {
    private var databases = ConcurrentHashMap<String, AppDatabase>()

    override fun onCreate() {
        super.onCreate()
        instance = this

        LocalPreferences.allSavedAccounts().forEach {
            databases[it.npub] = AppDatabase.getDatabase(this, it.npub)
        }
    }

    fun getDatabase(npub: String): AppDatabase {
        if (!databases.containsKey(npub)) {
            databases[npub] = AppDatabase.getDatabase(this, npub)
        }
        return databases[npub]!!
    }

    fun checkForNewRelays() {
        val savedRelays = mutableSetOf<String>()
        LocalPreferences.allSavedAccounts().forEach { accountInfo ->
            val database = getDatabase(accountInfo.npub)
            database.applicationDao().getAllApplications().forEach {
                it.application.relays.forEach { url ->
                    if (url.isNotBlank()) {
                        savedRelays.add(url)
                    }
                }
            }
        }
        Client.addRelays(savedRelays.map { Relay(it) }.toTypedArray())
    }

    companion object {
        lateinit var instance: NostrSigner
            private set
    }
}
