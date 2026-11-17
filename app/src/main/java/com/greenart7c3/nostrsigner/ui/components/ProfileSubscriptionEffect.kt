package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.models.Account
import kotlinx.coroutines.launch

/**
 * Drives the per-account profile metadata fetch from whichever composable is displaying
 * [account]. Starts (or refreshes) the throttled, one-shot subscription when the composable
 * enters composition and closes it on dispose, replacing the old app-wide, current-account-only
 * subscription.
 */
@Composable
fun ProfileSubscriptionEffect(account: Account) {
    // No relay subscriptions in previews: the preview renderer has no Application.
    if (LocalInspectionMode.current) return
    val amber = LocalContext.current.amber
    DisposableEffect(account.hexKey) {
        val sub = amber.profileSubscription
        amber.applicationIOScope.launch { sub.updateFilter(account) }
        onDispose { sub.closeSub(account) }
    }
}
