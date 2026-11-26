package com.greenart7c3.nostrsigner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One remembered permission of an app, as the permissions screen shows and edits it. */
data class PermissionGrant(
    // The stored row; null for one not saved yet.
    val id: Int?,
    val type: String,
    val kind: Int?,
    val acceptable: Boolean,
    val rememberType: Int,
    val acceptUntil: Long,
    val rejectUntil: Long,
    val relay: String = "",
)

/** What the permissions screen shows of the app itself: its key and its bunker:// connection. */
data class PermissionsApp(
    val key: String = "",
    val isConnected: Boolean = false,
    val relays: List<NormalizedRelayUrl> = emptyList(),
    val useSecret: Boolean = false,
    val secret: String = "",
    // The connection's own key, or "" when it signs with the account key.
    val localPubKey: String = "",
)

private fun ApplicationPermissionsEntity.toGrant() = PermissionGrant(id, type, kind, acceptable, rememberType, acceptUntil, rejectUntil, relay)

private fun PermissionGrant.toEntity(appKey: String) = ApplicationPermissionsEntity(id, appKey, type, kind, acceptable, rememberType, acceptUntil, rejectUntil, relay)

/** Loads an app's remembered permissions and saves the changes made on its permissions screen. */
class EditPermissionViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    /**
     * Expires the grants whose time has passed, then the app saved under [key] with its
     * grants (sorted by type and kind); null when no app is saved under it.
     */
    suspend fun load(npub: String, key: String): Pair<List<PermissionGrant>, PermissionsApp>? = withContext(Dispatchers.IO) {
        val daos = accountRepository.daos(npub)
        daos.permissionMaintenanceDao.updateExpiredPermissions(TimeUtils.now())
        val grants = daos.permissionLookupDao.getAllByKey(key).sortedBy { "${it.type}-${it.kind}" }.map { it.toGrant() }
        val app = daos.applicationDao.getByKey(key)?.application ?: return@withContext null
        grants to PermissionsApp(app.key, app.isConnected, app.relays, app.useSecret, app.secret, app.localPubKey)
    }

    /** Saves new grants or changed ones for the app [appKey]. */
    fun save(npub: String, appKey: String, grants: List<PermissionGrant>) {
        viewModelScope.launch(Dispatchers.IO) { accountRepository.daos(npub).permissionWriteDao.insertPermissions(grants.map { it.toEntity(appKey) }) }
    }

    fun removeAll(npub: String, appKey: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository.daos(npub).permissionWriteDao.deletePermissions(appKey)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditPermissionViewModel(accountRepository) }
        }
    }
}
