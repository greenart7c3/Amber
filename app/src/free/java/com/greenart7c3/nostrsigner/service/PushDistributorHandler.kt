/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

interface PushDistributorActions {
    fun getSavedDistributor(): String

    fun getInstalledDistributors(): List<String>

    fun saveDistributor(distributor: String)

    fun removeSavedDistributor()
}

object PushDistributorHandler : PushDistributorActions {
    private val appContext = NostrSigner.getInstance().applicationContext
    private val unifiedPush: UnifiedPush = UnifiedPush

    private var endpointInternal = NostrSigner.getInstance().settings.endpoint

    fun getSavedEndpoint() = endpointInternal

    @OptIn(DelicateCoroutinesApi::class)
    fun setEndpoint(newEndpoint: String) {
        endpointInternal = newEndpoint
        GlobalScope.launch(Dispatchers.IO) {
            NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(endpoint = newEndpoint)
            LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
        }
        Log.d("PushHandler", "New endpoint saved : $endpointInternal")
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun removeEndpoint() {
        endpointInternal = ""
        GlobalScope.launch(Dispatchers.IO) {
            NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(endpoint = "")
            LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
        }
    }

    override fun getSavedDistributor(): String {
        return unifiedPush.getSavedDistributor(appContext) ?: ""
    }

    fun savedDistributorExists(): Boolean = getSavedDistributor().isNotEmpty()

    override fun getInstalledDistributors(): List<String> {
        return unifiedPush.getDistributors(appContext)
    }

    fun formattedDistributorNames(): List<String> {
        val distributorsArray = getInstalledDistributors().toTypedArray()
        val distributorsNameArray =
            distributorsArray
                .map {
                    try {
                        val ai =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                appContext.packageManager.getApplicationInfo(
                                    it,
                                    PackageManager.ApplicationInfoFlags.of(
                                        PackageManager.GET_META_DATA.toLong(),
                                    ),
                                )
                            } else {
                                appContext.packageManager.getApplicationInfo(it, 0)
                            }
                        appContext.packageManager.getApplicationLabel(ai)
                    } catch (e: PackageManager.NameNotFoundException) {
                        it
                    }
                        as String
                }
                .toTypedArray()
        return distributorsNameArray.toList()
    }

    override fun saveDistributor(distributor: String) {
        unifiedPush.saveDistributor(appContext, distributor)
        unifiedPush.registerApp(appContext)
    }

    override fun removeSavedDistributor() {
        unifiedPush.safeRemoveDistributor(appContext)
    }

    fun forceRemoveDistributor(context: Context) {
        unifiedPush.forceRemoveDistributor(context)
    }
}
