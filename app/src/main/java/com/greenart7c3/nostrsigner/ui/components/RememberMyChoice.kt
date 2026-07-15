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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.rememberTypeDisplayOrder
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews

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
fun RememberMyChoice(
    shouldRunAcceptOrReject: Boolean?,
    packageName: String?,
    alwaysShow: Boolean = false,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    onChanged: (RememberType) -> Unit,
) {
    var selected by remember { mutableStateOf(RememberType.NEVER) }
    if (shouldRunAcceptOrReject != null) {
        LaunchedEffect(Unit) {
            if (shouldRunAcceptOrReject) {
                onAccept(selected)
            } else {
                onReject(selected)
            }
        }
    }
    if (packageName != null || alwaysShow) {
        LabeledBorderBox(
            label = stringResource(R.string.automatically_sign_this_for),
        ) {
            AmberToggles(
                selected = selected,
                options = rememberTypeDisplayOrder,
                onSelected = {
                    selected = it
                    onChanged(it)
                },
                label = { stringResource(it.shortResourceId) },
            )
        }
    }
}

@ThemePreviews
@Composable
fun LabeledBorderBoxPreview() {
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
fun RememberMyChoicePreview() {
    AmberPreview {
        RememberMyChoice(
            shouldRunAcceptOrReject = null,
            packageName = null,
            alwaysShow = true,
            onAccept = {},
            onReject = {},
            onChanged = {},
        )
    }
}
