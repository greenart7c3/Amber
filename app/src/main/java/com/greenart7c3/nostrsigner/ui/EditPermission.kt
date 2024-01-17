package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch

@Composable
fun EditPermission(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    selectedPackage: String,
    navController: NavController
) {
    val localAccount = LocalPreferences.loadFromEncryptedStorage(account.keyPair.pubKey.toNpub())!!
    val permissions = remember {
        val pairsList = buildList {
            for (key in localAccount.savedApps.keys.toList().sorted()) {
                add(Pair(key, localAccount.savedApps[key]))
            }
        }
        mutableStateMapOf(
            *pairsList.toTypedArray()
        )
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
    ) {
        Text(
            selectedPackage,
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )
        LazyColumn(
            Modifier.weight(1f)
        ) {
            itemsIndexed(permissions.keys.toList().sorted(), { index, _ -> index }) { _, permission ->
                val message = when (permission.replace("$selectedPackage-", "")) {
                    "NIP04_DECRYPT" -> stringResource(R.string.decrypt_nip_04_data)
                    "NIP44_DECRYPT" -> stringResource(R.string.decrypt_nip_44_data)
                    "NIP44_ENCRYPT" -> stringResource(R.string.encrypt_nip_44_data)
                    "NIP04_ENCRYPT" -> stringResource(R.string.encrypt_nip_04_data)
                    "DECRYPT_ZAP_EVENT" -> stringResource(R.string.decrypt_zap_data)
                    "GET_PUBLIC_KEY" -> stringResource(R.string.read_your_public_key)
                    else -> "Sign event kind ${permission.split("-").last()}"
                }
                Row(
                    modifier = Modifier
                        .padding(vertical = 15.dp, horizontal = 25.dp)
                        .fillMaxWidth()
                ) {
                    val localPermission = permissions[permission]!!
                    Icon(
                        if (localPermission) Icons.Default.Check else Icons.Default.Close,
                        null,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(end = 4.dp)
                            .clickable {
                                permissions[permission] = !permissions[permission]!!
                            },
                        tint = if (localPermission) Color.Green else Color.Red
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            fontSize = 18.sp
                        )
                    }
                    Icon(
                        Icons.Default.Delete,
                        null,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                permissions.remove(permission)
                            },
                        tint = Color.Red
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center
        ) {
            Button(
                onClick = {
                    navController.popBackStack()
                },
                Modifier.padding(6.dp)
            ) {
                Text(stringResource(id = R.string.cancel))
            }
            Button(
                onClick = {
                    scope.launch {
                        val localSaved = localAccount.savedApps.filter { !it.key.contains(selectedPackage) }.toMutableMap()
                        permissions.forEach {
                            localSaved[it.key] = it.value!!
                        }
                        localAccount.savedApps = localSaved
                        LocalPreferences.saveToEncryptedStorage(localAccount)
                        navController.popBackStack()
                        accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Permissions.route)
                    }
                },
                Modifier.padding(6.dp)
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        }
    }
}
