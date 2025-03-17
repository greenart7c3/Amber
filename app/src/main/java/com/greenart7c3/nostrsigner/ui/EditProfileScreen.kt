package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route

@Composable
fun EditProfileScreen(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    npub: String,
) {
    val context = LocalContext.current
    val name = LocalPreferences.getAccountName(context, npub)
    var textFieldvalue by remember {
        mutableStateOf(TextFieldValue(name))
    }

    Column(
        modifier,
    ) {
        OutlinedTextField(
            value = textFieldvalue.text,
            onValueChange = {
                textFieldvalue = TextFieldValue(it)
            },
            label = {
                Text(stringResource(R.string.nickname))
            },
        )
        AmberButton(
            modifier = Modifier.padding(vertical = 40.dp),
            onClick = {
                LocalPreferences.setAccountName(context, npub, textFieldvalue.text)
                accountStateViewModel.switchUser(account.npub, Route.Settings.route)
            },
            text = stringResource(R.string.save),
        )
    }
}
