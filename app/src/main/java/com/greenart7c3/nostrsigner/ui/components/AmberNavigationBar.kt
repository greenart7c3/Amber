package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.isAmberDarkTheme

@Composable
fun AmberNavigationBar(
    items: List<Route>,
    destinationRoute: String,
    onClick: (Route) -> Unit,
    profileUrl: String?,
    account: Account,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach {
                val selected = destinationRoute == it.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        onClick(it)
                    },
                    icon = {
                        if (it.route == Route.Accounts.route) {
                            AccountTabIcon(account, profileUrl, it.route)
                        } else {
                            Icon(
                                painterResource(it.icon),
                                it.route,
                                tint = if (selected || !isAmberDarkTheme()) Color.Black else Color.White,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountTabIcon(
    account: Account,
    profileUrl: String?,
    route: String,
) {
    if (!profileUrl.isNullOrBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
        SubcomposeAsyncImage(
            profileUrl,
            route,
            Modifier
                .clip(RoundedCornerShape(50))
                .height(28.dp)
                .width(28.dp),
            loading = { CenterCircularProgressIndicator(Modifier) },
            error = { AccountPlaceholderIcon(account, route) },
        )
    } else {
        AccountPlaceholderIcon(account, route)
    }
}
