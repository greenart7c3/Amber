package com.greenart7c3.nostrsigner.desktop.ui.nav

/** The desktop bunker app's flat navigation destinations. */
sealed class Screen {
    data object Home : Screen()

    data object Connect : Screen()

    data object ConnectedApps : Screen()

    data class AppDetail(val appPubKey: String) : Screen()

    data object Activity : Screen()

    data object Settings : Screen()
}
