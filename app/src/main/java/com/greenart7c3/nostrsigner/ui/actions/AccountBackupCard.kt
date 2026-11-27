package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.make_backup_message
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.ProfileSubscriptionEffect
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AccountBackupCard(
    account: Account,
    password: String,
    seedWords: String,
    onLoading: (Boolean) -> Unit,
    onShowQrCode: (String) -> Unit,
) {
    ProfileSubscriptionEffect(account)
    var didBackup by remember { mutableStateOf(account.didBackup) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().outlineIfAny(amoledBorder(), CardDefaults.elevatedShape)) {
        Column(modifier = Modifier.padding(16.dp)) {
            AccountBackupHeader(account, didBackup)

            if (!didBackup) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.make_backup_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NSecCopyButton(
                    account = account,
                    password = password,
                    modifier = Modifier.weight(1f),
                    onLoading = onLoading,
                    onBackupFinish = { didBackup = true },
                )
                NSecQrButton(
                    account = account,
                    password = password,
                    modifier = Modifier.weight(1f),
                    onLoading = onLoading,
                    onBackupFinish = { didBackup = true },
                    onShowQrCode = onShowQrCode,
                )
            }

            if (Nip06().isValidMnemonic(seedWords)) {
                Spacer(modifier = Modifier.height(4.dp))
                SeedWordsButton(
                    account = account,
                    seedWords = seedWords,
                    onBackupFinish = { didBackup = true },
                )
            }
        }
    }
}

@Composable
private fun AccountBackupHeader(
    account: Account,
    didBackup: Boolean,
) {
    val profileUrl = account.picture.collectAsState()
    val name = account.name.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (profileUrl.value.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
            SubcomposeAsyncImage(
                model = profileUrl.value,
                contentDescription = "profile picture",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                loading = { CenterCircularProgressIndicator(Modifier) },
                error = {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "profile picture",
                        modifier = Modifier
                            .size(48.dp)
                            .border(2.dp, Color.fromHex(account.hexKey.slice(0..5)), CircleShape),
                    )
                },
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = "profile picture",
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, Color.fromHex(account.hexKey.slice(0..5)), CircleShape)
                    .clip(CircleShape),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.value.ifBlank { account.hexKey.toShortenHex() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (didBackup) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Backed up",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Not backed up",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun BackupIconButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors().copy(contentColor = Color.Black),
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, 1f),
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
