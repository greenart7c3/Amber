/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.PushDistributorHandler
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun LoadDistributors(onInner: @Composable (String, ImmutableList<String>, ImmutableList<TitleExplainer>) -> Unit) {
    val currentDistributor = PushDistributorHandler.getSavedDistributor().ifBlank { null } ?: "None"

    val list =
        remember {
            PushDistributorHandler.getInstalledDistributors().plus("None").toImmutableList()
        }

    val readableListWithExplainer =
        PushDistributorHandler
            .formattedDistributorNames()
            .mapIndexed { index, name ->
                TitleExplainer(
                    name,
                    stringResource(id = R.string.push_server_uses_app_explainer, list[index]),
                )
            }.plus(
                TitleExplainer(
                    stringResource(id = R.string.push_server_none),
                    stringResource(id = R.string.push_server_none_explainer),
                ),
            )
            .toImmutableList()

    onInner(
        currentDistributor,
        list,
        readableListWithExplainer,
    )
}

@Composable
fun PushNotificationSettingsRow() {
    val context = LocalContext.current
    LoadDistributors { currentDistributor, list, readableListWithExplainer ->
        SettingsRow(
            R.string.push_server_title,
            R.string.push_server_explainer,
            selectedItems = readableListWithExplainer,
            selectedIndex = list.indexOf(currentDistributor),
        ) { index ->
            if (list[index] == "None") {
                PushDistributorHandler.forceRemoveDistributor(context)
            } else {
                PushDistributorHandler.saveDistributor(list[index])
            }
        }
    }
}
