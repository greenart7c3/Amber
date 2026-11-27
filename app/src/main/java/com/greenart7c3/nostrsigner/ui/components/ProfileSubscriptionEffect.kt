package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalInspectionMode
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalProfileSubscription
import kotlinx.coroutines.launch

/**
 * Drives the per-account profile metadata fetch from whichever composable is displaying
 * [account]. Starts (or refreshes) the throttled, one-shot subscription when the composable
 * enters composition and closes it on dispose, replacing the old app-wide, current-account-only
 * subscription. Does nothing in previews: the preview renderer has no Application.
 */
@Composable
fun ProfileSubscriptionEffect(account: Account) {
    if (LocalInspectionMode.current) return
    val sub = LocalProfileSubscription.current
    val appScope = LocalAppScope.current
    DisposableEffect(account.hexKey) {
        appScope.launch { sub.updateFilter(account) }
        onDispose { sub.closeSub(account) }
    }
}
