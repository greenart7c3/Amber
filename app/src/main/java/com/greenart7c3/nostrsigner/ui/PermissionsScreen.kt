package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.vitorpamplona.quartz.encoders.toHexKey

@Composable
fun PermissionsScreen(
    modifier: Modifier,
    account: Account,
    navController: NavController,
    database: AppDatabase,
) {
    val applications = database.applicationDao().getAllFlow(account.signer.keyPair.pubKey.toHexKey()).collectAsStateWithLifecycle(emptyList())

    Column(
        modifier,
    ) {
        if (applications.value.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_permissions_granted))
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
//                item {
//                    Row(
//                        Modifier
//                            .fillMaxWidth()
//                            .padding(8.dp)
//                            .clickable {
//
//                            },
//                        horizontalArrangement = Arrangement.End,
//                    ) {
//                        Icon(
//                            Icons.AutoMirrored.Filled.Sort,
//                            null,
//                            modifier = Modifier.size(22.dp),
//                            tint = MaterialTheme.colorScheme.onBackground,
//                        )
//                        Text(
//                            modifier = Modifier.padding(start = 16.dp),
//                            text = "Sort by $sortBy",
//                            maxLines = 1,
//                            overflow = TextOverflow.Ellipsis,
//                        )
//                    }
//                }
                items(applications.value.size) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .clickable {
                                navController.navigate("Permission/${applications.value.elementAt(it).key}")
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val localPermission = applications.value.elementAt(it)
                            Text(
                                modifier = Modifier.padding(top = 16.dp),
                                text = localPermission.name.ifBlank { localPermission.key.toShortenHex() },
                                fontSize = 24.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                                text = localPermission.key,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(

                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
