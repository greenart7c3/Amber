package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.automatically_sign_this_for
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.rememberTypeDisplayOrder
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import org.jetbrains.compose.resources.stringResource

@Composable
fun LabeledBorderBox(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(
                    width = 1.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(4.dp),
        ) {
            content()
        }

        Text(
            text = label,
            modifier = Modifier
                .padding(start = 16.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
fun RememberMyChoiceToggles(
    selected: RememberType,
    onSelect: (RememberType) -> Unit,
    label: String = stringResource(Res.string.automatically_sign_this_for),
) {
    LabeledBorderBox(
        label = label,
    ) {
        AmberToggles(
            selected = selected,
            options = rememberTypeDisplayOrder,
            onSelect = onSelect,
            label = { stringResource(it.shortResourceId) },
        )
    }
}

@Composable
fun RememberMyChoice(
    shouldRunAcceptOrReject: Boolean?,
    packageName: String?,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    onChange: (RememberType) -> Unit,
    alwaysShow: Boolean = false,
) {
    var selected by remember { mutableStateOf(RememberType.NEVER) }
    // The effect below runs once; it must call the latest callbacks, not the first ones.
    val currentOnAccept by rememberUpdatedState(onAccept)
    val currentOnReject by rememberUpdatedState(onReject)
    if (shouldRunAcceptOrReject != null) {
        LaunchedEffect(Unit) {
            if (shouldRunAcceptOrReject) {
                currentOnAccept(selected)
            } else {
                currentOnReject(selected)
            }
        }
    }
    if (packageName != null || alwaysShow) {
        RememberMyChoiceToggles(
            selected = selected,
            onSelect = {
                selected = it
                onChange(it)
            },
        )
    }
}

@ThemePreviews
@Composable
private fun LabeledBorderBoxPreview() {
    AmberPreview {
        LabeledBorderBox(
            label = "Label",
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text = "Content",
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun RememberMyChoicePreview() {
    AmberPreview {
        RememberMyChoice(
            shouldRunAcceptOrReject = null,
            packageName = null,
            alwaysShow = true,
            onAccept = {},
            onReject = {},
            onChange = {},
        )
    }
}
