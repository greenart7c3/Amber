package com.greenart7c3.nostrsigner.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.last_24_hours
import com.greenart7c3.nostrsigner.commonsui.last_30_days
import com.greenart7c3.nostrsigner.commonsui.last_7_days
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** How many requests were accepted and rejected in one time window. */
data class WindowStats(
    val labelRes: StringResource,
    val accepted: Long,
    val rejected: Long,
) {
    val total: Long get() = accepted + rejected
}

/**
 * The accepted/rejected counts the statistics card shows, for one account and,
 * with a `pkKey`, one app. Loaded once, the first time the card is opened, and
 * forgotten when it leaves the screen, as the card's own state was.
 */
class ActivityStatsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _stats = MutableStateFlow<List<WindowStats>?>(null)
    val stats: StateFlow<List<WindowStats>?> = _stats.asStateFlow()

    private var job: Job? = null

    fun load(npub: String, pkKey: String?) {
        if (job != null) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _stats.value = loadStats(accountRepository.getHistoryDatabase(npub).dao(), pkKey)
        }
    }

    /** Drops the counts when the card leaves the screen, so coming back shows fresh ones. */
    fun forget() {
        job?.cancel()
        job = null
        _stats.value = null
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ActivityStatsViewModel(accountRepository) }
        }
    }
}

private suspend fun loadStats(dao: HistoryDao, pkKey: String?): List<WindowStats> {
    val now = TimeUtils.now()
    val day = 24L * 60 * 60
    val windows = listOf(
        Res.string.last_24_hours to day,
        Res.string.last_7_days to 7 * day,
        Res.string.last_30_days to 30 * day,
    )
    return windows.map { (labelRes, windowSeconds) ->
        val since = now - windowSeconds
        WindowStats(
            labelRes = labelRes,
            accepted = if (pkKey == null) dao.countAcceptedSince(since) else dao.countAcceptedSince(pkKey, since),
            rejected = if (pkKey == null) dao.countRejectedSince(since) else dao.countRejectedSince(pkKey, since),
        )
    }
}
