package com.greenart7c3.nostrsigner

import android.app.Application
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.service.RelayDisconnectService
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.service.HttpClientManager
import java.util.concurrent.ConcurrentHashMap

class NostrSigner : Application() {
    private var databases = ConcurrentHashMap<String, AppDatabase>()

    override fun onCreate() {
        super.onCreate()
        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")
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
        savedRelays.forEach {
            if (RelayPool.getRelay(it) == null) {
                RelayPool.addRelay(
                    Relay(
                        it,
                        read = true,
                        write = true,
                        activeTypes = COMMON_FEED_TYPES,
                    ),
                )
            }
        }

        RelayPool.getAll().forEach { relay ->
            savedRelays.any { relay.url == it }.let {
                if (!it) {
                    relay.disconnect()
                    RelayPool.removeRelay(relay)
                }
            }
        }
        if (LocalPreferences.getNotificationType() == NotificationType.DIRECT && BuildConfig.FLAVOR != "offline") {
            Client.reconnect(
                RelayPool.getAll().map { RelaySetupInfo(it.url, read = true, write = true, feedTypes = COMMON_FEED_TYPES) }.toTypedArray(),
                true,
            )
        }
    }

    fun checkIfRelaysAreConnected() {
        Log.d("NostrSigner", "Checking if relays are connected")
        RelayPool.getAll().forEach { relay ->
            if (!relay.isConnected()) {
                relay.connectAndRun {
                    val builder = OneTimeWorkRequest.Builder(RelayDisconnectService::class.java)
                    val inputData = Data.Builder()
                    inputData.putString("relay", relay.url)
                    builder.setInputData(inputData.build())
                    WorkManager.getInstance(NostrSigner.instance).enqueue(builder.build())
                }
            }
        }
        var count = 0
        while (RelayPool.getAll().any { !it.isConnected() } && count < 10) {
            count++
            Thread.sleep(1000)
        }
    }

    companion object {
        lateinit var instance: NostrSigner
            private set
    }
}
