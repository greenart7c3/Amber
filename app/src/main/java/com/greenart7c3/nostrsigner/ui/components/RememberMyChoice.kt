package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.rememberTypeDisplayOrder
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RememberTypePicker(
    selected: RememberType,
    options: List<RememberType>,
    onSelected: (RememberType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(selected.resourceId),
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showSheet = false
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(option.resourceId),
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
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
            RememberTypePicker(
                selected = selected,
                options = rememberTypeDisplayOrder,
                onSelected = {
                    selected = it
                    onChanged(it)
                },
            )
        }
    }
}
