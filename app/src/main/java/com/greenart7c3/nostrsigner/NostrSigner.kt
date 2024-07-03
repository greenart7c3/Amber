package com.greenart7c3.nostrsigner

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.RelayDisconnectService
import com.greenart7c3.nostrsigner.ui.NotificationType
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

        LocalPreferences.allSavedAccounts(this).forEach {
            databases[it.npub] = AppDatabase.getDatabase(this, it.npub)
        }

        try {
            this.startForegroundService(
                Intent(
                    this,
                    ConnectivityService::class.java,
                ),
            )
        } catch (e: Exception) {
            Log.e("NostrSigner", "Failed to start ConnectivityService", e)
        }
    }

    fun getDatabase(npub: String): AppDatabase {
        if (!databases.containsKey(npub)) {
            databases[npub] = AppDatabase.getDatabase(this, npub)
        }
        return databases[npub]!!
    }

    fun getSavedRelays(): Set<RelaySetupInfo> {
        val savedRelays = mutableSetOf<RelaySetupInfo>()
        LocalPreferences.allSavedAccounts(this).forEach { accountInfo ->
            val database = getDatabase(accountInfo.npub)
            database.applicationDao().getAllApplications().forEach {
                it.application.relays.forEach { setupInfo ->
                    savedRelays.add(setupInfo)
                }
            }
        }
        return savedRelays
    }

    suspend fun checkForNewRelays(
        shouldReconnect: Boolean = false,
        newRelays: Set<RelaySetupInfo> = emptySet(),
    ) {
        val savedRelays = getSavedRelays() + newRelays

        if (LocalPreferences.getNotificationType(this) != NotificationType.DIRECT) {
            savedRelays.forEach { setupInfo ->
                if (RelayPool.getRelay(setupInfo.url) == null) {
                    RelayPool.addRelay(
                        Relay(
                            setupInfo.url,
                            setupInfo.read,
                            setupInfo.write,
                            setupInfo.feedTypes,
                        ),
                    )
                }
            }
        }

        if (shouldReconnect) {
            checkIfRelaysAreConnected()
        }
        @Suppress("KotlinConstantConditions")
        if (LocalPreferences.getNotificationType(this) == NotificationType.DIRECT && BuildConfig.FLAVOR != "offline") {
            Client.reconnect(
                savedRelays.toTypedArray(),
                true,
            )
        }
    }

    private fun checkIfRelaysAreConnected(tryAgain: Boolean = true) {
        Log.d("NostrSigner", "Checking if relays are connected")
        RelayPool.getAll().forEach { relay ->
            if (!relay.isConnected()) {
                relay.connectAndRun {
                    val builder = OneTimeWorkRequest.Builder(RelayDisconnectService::class.java)
                    val inputData = Data.Builder()
                    inputData.putString("relay", relay.url)
                    builder.setInputData(inputData.build())
                    WorkManager.getInstance(getInstance()).enqueue(builder.build())
                }
            }
        }
        var count = 0
        while (RelayPool.getAll().any { !it.isConnected() } && count < 10) {
            count++
            Thread.sleep(1000)
        }
        if (RelayPool.getAll().any { !it.isConnected() } && tryAgain) {
            checkIfRelaysAreConnected(false)
        }
    }

    companion object {
        @Volatile
        private var instance: NostrSigner? = null

        fun getInstance(): NostrSigner =
            instance ?: synchronized(this) {
                instance ?: NostrSigner().also { instance = it }
            }
    }
}
