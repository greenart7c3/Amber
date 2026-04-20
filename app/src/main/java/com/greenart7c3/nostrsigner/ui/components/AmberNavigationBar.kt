package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.greenart7c3.nostrsigner.ui.theme.fromHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmberNavigationBar(
    items: List<Route>,
    destinationRoute: String,
    onClick: (Route) -> Unit,
    profileUrl: String?,
    account: Account,
) {
    NavigationBar(
        tonalElevation = 0.dp,
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
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    icon = {
                        if (it.route == Route.Accounts.route) {
                            if (!profileUrl.isNullOrBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
                                SubcomposeAsyncImage(
                                    profileUrl,
                                    it.route,
                                    Modifier
                                        .clip(
                                            RoundedCornerShape(50),
                                        )
                                        .height(28.dp)
                                        .width(28.dp),
                                    loading = {
                                        CenterCircularProgressIndicator(Modifier)
                                    },
                                    error = { error ->
                                        Icon(
                                            Icons.Outlined.Person,
                                            it.route,
                                            modifier = Modifier.border(
                                                2.dp,
                                                Color.fromHex(account.hexKey.slice(0..5)),
                                                CircleShape,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Person,
                                    it.route,
                                    modifier = Modifier.border(
                                        2.dp,
                                        Color.fromHex(account.hexKey.slice(0..5)),
                                        CircleShape,
                                    ),
                                )
                            }
                        } else {
                            Icon(
                                painterResource(it.icon),
                                it.route,
                            )
                        }
                    },
                )
            }
        }
    }
}
