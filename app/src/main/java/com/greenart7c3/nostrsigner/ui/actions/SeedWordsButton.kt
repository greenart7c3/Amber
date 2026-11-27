package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.show_seed_words
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeedWordsButton(
    account: Account,
    seedWords: String,
    onBackupFinish: () -> Unit,
) {
    val appScope = LocalAppScope.current
    val showSeedWords = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            showSeedWords.value = true
        }
    }

    if (showSeedWords.value) {
        ModalBottomSheet(
            modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
            onDismissRequest = { showSeedWords.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 1f),
                ) {
                    SeedWordsPage(
                        seedWords = seedWords.split(" ").toSet(),
                        showNextButton = false,
                    )
                }
            }
        }
    }

    val text = stringResource(Res.string.show_seed_words)

    BackupIconButton(
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
        text = text,
        colors = ButtonDefaults.outlinedButtonColors(),
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    appScope.launch {
                        account.didBackup = true
                        onBackupFinish()
                        showSeedWords.value = true
                    }
                },
                onError = { _, message ->
                    appScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    )
}
