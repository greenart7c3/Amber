package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.SettingsRow
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RememberMyChoice(
    shouldRunAcceptOrReject: Boolean?,
    packageName: String?,
    alwaysShow: Boolean = false,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    onChanged: (RememberType) -> Unit,
) {
    val items =
        persistentListOf(
            TitleExplainer(stringResource(RememberType.NEVER.resourceId)),
            TitleExplainer(stringResource(RememberType.ONE_MINUTE.resourceId)),
            TitleExplainer(stringResource(RememberType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(RememberType.TEN_MINUTES.resourceId)),
            TitleExplainer(stringResource(RememberType.ALWAYS.resourceId)),
        )
    var index by remember {
        mutableIntStateOf(0)
    }
    if (shouldRunAcceptOrReject != null) {
        LaunchedEffect(Unit) {
            if (shouldRunAcceptOrReject) {
                onAccept(RememberType.entries[index])
            } else {
                onReject(RememberType.entries[index])
            }
        }
    }
    if (packageName != null || alwaysShow) {
        SettingsRow(
            R.string.automatically_sign_this_for,
            null,
            items,
            index,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        ) {
            index = it
            onChanged(RememberType.entries[it])
        }
    }
}
