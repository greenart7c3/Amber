package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SignPolicySettingsScreen(
    modifier: Modifier = Modifier,
    account: Account,
    navController: NavController,
) {
    val radioOptions = listOf(
        TitleExplainer(
            title = stringResource(R.string.sign_policy_basic),
            explainer = stringResource(R.string.sign_policy_basic_explainer),
        ),
        TitleExplainer(
            title = stringResource(R.string.sign_policy_manual),
            explainer = stringResource(R.string.sign_policy_manual_explainer),
        ),
    )
    var selectedOption by remember { mutableIntStateOf(account.signPolicy) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(radioOptions) { index, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedOption == index,
                            onClick = {
                                selectedOption = index
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (selectedOption == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedOption == index,
                        onClick = {
                            selectedOption = index
                        },
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = option.title,
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        option.explainer?.let {
                            Text(
                                text = it,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        AmberButton(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    account.signPolicy = selectedOption
                    LocalPreferences.saveToEncryptedStorage(context, account)
                    scope.launch(Dispatchers.Main) {
                        navController.navigateUp()
                    }
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.save),
                )
            },
        )
    }
}
