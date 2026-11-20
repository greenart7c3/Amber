package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.LocalBunkerRelayServer
import com.greenart7c3.nostrsigner.service.NotificationSubscription
import com.greenart7c3.nostrsigner.service.ProfileSubscription
import kotlinx.coroutines.CoroutineScope

// The app's dependencies for composables, provided once at each activity root
// (appDependencies) instead of threading them through every screen.
val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> { error("No SettingsStore provided") }
val LocalAccountRepository = staticCompositionLocalOf<AccountRepository> { error("No AccountRepository provided") }
val LocalRelayClient = staticCompositionLocalOf<RelayClient> { error("No RelayClient provided") }

/** Answers NIP-46 requests: what the bunker approval screens reply through. */
val LocalBunkerResponder = staticCompositionLocalOf<BunkerResponder> { error("No BunkerResponder provided") }

/** The local bunker relay on ws://127.0.0.1: its settings, and the relays a new bunker:// connection gets. */
val LocalBunkerRelay = staticCompositionLocalOf<LocalBunkerRelayServer> { error("No LocalBunkerRelayServer provided") }

/** The NIP-46 subscription: refreshed after an account is added so it listens for its keys. */
val LocalNotificationSubscription = staticCompositionLocalOf<NotificationSubscription> { error("No NotificationSubscription provided") }

/** The per-account profile metadata fetch, driven by the composables that show an account. */
val LocalProfileSubscription = staticCompositionLocalOf<ProfileSubscription> { error("No ProfileSubscription provided") }

/** For work that must outlive the screen that starts it (saving settings, relay updates). */
val LocalAppScope = staticCompositionLocalOf<CoroutineScope> { error("No app scope provided") }

fun appDependencies(amber: Amber): Array<ProvidedValue<*>> = arrayOf(
    LocalSettingsStore provides amber,
    LocalAccountRepository provides amber,
    LocalRelayClient provides amber,
    LocalAppScope provides amber.applicationIOScope,
    LocalBunkerResponder provides amber.bunkerResponder,
    LocalBunkerRelay provides amber.localBunkerRelay,
    LocalNotificationSubscription provides amber.notificationSubscription,
    LocalProfileSubscription provides amber.profileSubscription,
)
