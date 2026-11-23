package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.fromHex

/** The person icon of an account without a picture, ringed in the account's color; [modifier] comes after the ring. */
@Composable
fun AccountPlaceholderIcon(
    account: Account,
    contentDescription: String,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
) {
    Icon(
        Icons.Outlined.Person,
        contentDescription,
        modifier = Modifier
            .border(borderWidth, Color.fromHex(account.hexKey.slice(0..5)), CircleShape)
            .then(modifier),
    )
}

@Composable
fun ProfilePictureIcon(account: Account, modifier: Modifier = Modifier) {
    ProfileSubscriptionEffect(account)
    val profileUrl by account.picture.collectAsStateWithLifecycle()
    if (profileUrl.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
        AsyncImage(
            profileUrl,
            Route.Accounts.route,
            modifier
                .clip(
                    RoundedCornerShape(50),
                )
                .height(40.dp)
                .width(40.dp),
        )
    } else {
        Icon(
            Icons.Outlined.Person,
            Route.Accounts.route,
            modifier = modifier
                .border(
                    2.dp,
                    Color.fromHex(account.hexKey.slice(0..5)),
                    CircleShape,
                )
                .height(40.dp)
                .width(40.dp),
        )
    }
}
