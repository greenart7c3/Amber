package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.theme.fromHex

@Composable
fun SigningAs(account: Account, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.signing_as),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val profileUrl by account.picture.collectAsStateWithLifecycle()
            if (profileUrl.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
                SubcomposeAsyncImage(
                    model = profileUrl,
                    contentDescription = stringResource(R.string.account_picture),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .height(30.dp)
                        .width(30.dp),
                    error = {
                        Icon(
                            Icons.Outlined.Person,
                            stringResource(R.string.account_picture),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    Color.fromHex(account.hexKey.slice(0..5)),
                                    CircleShape,
                                )
                                .height(30.dp)
                                .width(30.dp)
                        )
                    },
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    stringResource(R.string.account_picture),
                    modifier = Modifier
                        .border(
                            1.dp,
                            Color.fromHex(account.hexKey.slice(0..5)),
                            CircleShape,
                        )
                        .height(30.dp)
                        .width(30.dp)
                )
            }

            val name by account.name.collectAsStateWithLifecycle()
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = name.ifBlank { account.npub.toShortenHex() },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
