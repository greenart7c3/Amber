package com.greenart7c3.nostrsigner.service

import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.Amber.Companion.TAG
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.RelaySetupInfoToConnect
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileFetcherService {
    fun fetchProfileData(
        account: Account,
        onPictureFound: (String) -> Unit,
    ) {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == "offline") {
            return
        }

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(Amber.instance)
                .crossfade(true)
                .logger(DebugLogger())
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                HttpClientManager.getHttpClient(Amber.instance.settings.useProxy)
                            },
                        ),
                    )
                }
                .build()
        }

        val lastMetaData = LocalPreferences.getLastMetadataUpdate(Amber.instance, account.npub)
        val lastCheck = LocalPreferences.getLastCheck(Amber.instance, account.npub)
        val oneDayAgo = TimeUtils.oneDayAgo()
        val fifteenMinutesAgo = TimeUtils.fifteenMinutesAgo()
        if ((lastMetaData == 0L || oneDayAgo > lastMetaData) && (lastCheck == 0L || fifteenMinutesAgo > lastCheck)) {
            Log.d(TAG, "Fetching profile data for ${account.npub}")
            LocalPreferences.setLastCheck(Amber.instance, account.npub, TimeUtils.now())
            val relays = LocalPreferences.loadSettingsFromEncryptedStorage().defaultProfileRelays.map {
                RelaySetupInfoToConnect(
                    url = it.url,
                    read = it.read,
                    write = it.write,
                    feedTypes = it.feedTypes,
                    forceProxy = Amber.instance.settings.useProxy,
                )
            }.toTypedArray()
            val client = NostrClient(Amber.instance.factory)
            client.reconnect(relays)
            val profileDataSource = ProfileDataSource(
                client = client,
                account = account,
                onReceiveEvent = { event ->
                    Amber.instance.applicationIOScope.launch {
                        if (event.kind == MetadataEvent.KIND) {
                            (event as MetadataEvent).contactMetaData()?.let { metadata ->
                                metadata.name?.let { name ->
                                    account.name = name
                                    Amber.instance.applicationIOScope.launch {
                                        LocalPreferences.saveToEncryptedStorage(account = account, context = Amber.instance)
                                    }
                                }

                                metadata.profilePicture()?.let { url ->
                                    LocalPreferences.saveProfileUrlToEncryptedStorage(url, account.npub)
                                    LocalPreferences.setLastMetadataUpdate(Amber.instance, account.npub, TimeUtils.now())
                                    onPictureFound(url)
                                }
                            }
                        }
                    }
                },
            )

            profileDataSource.start()
            Amber.instance.applicationIOScope.launch {
                delay(60000)
                profileDataSource.stop()
                client.getAll().forEach {
                    Log.d(TAG, "disconnecting profile relay ${it.url}")
                    it.disconnect()
                }
            }
        } else {
            Log.d(TAG, "Using cached profile data for ${account.npub}")
            LocalPreferences.loadProfileUrlFromEncryptedStorage(account.npub)?.let {
                onPictureFound(it)
                return
            }
        }
    }
}
