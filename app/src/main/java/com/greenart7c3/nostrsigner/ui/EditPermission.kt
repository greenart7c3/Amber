package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.actions.RemoveAllPermissionsDialog
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberToggles
import com.greenart7c3.nostrsigner.ui.components.ToggleOption
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditPermission(
    modifier: Modifier,
    account: Account,
    selectedPackage: String,
    navController: NavController,
) {
    val clipboardManager = LocalClipboard.current
    val permissions = remember {
        mutableStateListOf<ApplicationPermissionsEntity>()
    }
    var applicationData by remember {
        mutableStateOf(ApplicationEntity.empty())
    }

    var wantsToRemovePermissions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var checked by remember {
        mutableStateOf(applicationData.useSecret)
    }
    val secret = if (checked) "&secret=${applicationData.secret}" else ""
    var bunkerUri by remember {
        val relayString = Amber.instance.settings.defaultRelays.joinToString(separator = "&") { "relay=${it.url}" }
        mutableStateOf("bunker://${account.hexKey}?$relayString$secret")
    }

    LaunchedEffect(selectedPackage) {
        val result = withContext(Dispatchers.IO) {
            val dao = Amber.instance.getDatabase(account.npub).dao()
            dao.updateExpiredPermissions(TimeUtils.now())
            val perms = dao.getAllByKey(selectedPackage)
                .sortedBy { "${it.type}-${it.kind}" }

            val app = dao.getByKey(selectedPackage)?.application

            perms to app
        }

        val (perms, app) = result
        if (app == null) return@LaunchedEffect

        permissions.clear()
        permissions.addAll(perms)

        applicationData = app
        checked = app.useSecret

        val relays = app.relays.joinToString("&") { "relay=${it.url}" }
        val localSecret = if (checked) "&secret=${app.secret}" else ""
        bunkerUri = "bunker://${account.hexKey}?$relays$localSecret"
    }

    if (wantsToRemovePermissions) {
        RemoveAllPermissionsDialog(
            onCancel = {
                wantsToRemovePermissions = false
            },
        ) {
            scope.launch(Dispatchers.IO) {
                Amber.instance.getDatabase(account.npub)
                    .dao()
                    .deletePermissions(applicationData.key)

                withContext(Dispatchers.Main) {
                    permissions.clear()
                    wantsToRemovePermissions = false
                }
            }
        }
    }

    Column(
        modifier = modifier,
    ) {
        if (applicationData.relays.isNotEmpty()) {
            if (applicationData.isConnected) {
                Text(
                    stringResource(R.string.connected_app_warning),
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                bunkerUri,
                Modifier
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Start,
                fontSize = 18.sp,
            )

            AmberButton(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText("", bunkerUri),
                            ),
                        )
                    }
                },
                text = stringResource(R.string.copy_to_clipboard),
            )

            Spacer(Modifier.height(12.dp))
        }

        AmberButton(
            modifier = Modifier.padding(top = 20.dp),
            onClick = {
                navController.navigate("Activity/${applicationData.key}")
            },
            text = stringResource(R.string.activity),
        )

        AmberButton(
            modifier = Modifier.padding(bottom = 20.dp),
            onClick = {
                navController.navigate("EditConfiguration/${applicationData.key}")
            },
            text = stringResource(R.string.edit_configuration),
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            text = stringResource(R.string.edit_permissions_description),
        )

        permissions.forEach { permission ->
            PermissionRow(
                permission = permission,
                onToggle = { updated ->
                    val index = permissions.indexOfFirst { it.id == updated.id }
                    if (index != -1) {
                        permissions[index] = updated
                    }

                    scope.launch(Dispatchers.IO) {
                        Amber.instance
                            .getDatabase(account.npub)
                            .dao()
                            .insertPermissions(listOf(updated))
                    }
                },
                onDelete = { deleted ->
                    permissions.remove(deleted)

                    scope.launch(Dispatchers.IO) {
                        Amber.instance
                            .getDatabase(account.npub)
                            .dao()
                            .deletePermission(deleted)
                    }
                },
            )
        }

        if (permissions.isNotEmpty()) {
            AmberButton(
                modifier = Modifier.padding(top = 60.dp, bottom = 60.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = orange,
                ),
                onClick = {
                    wantsToRemovePermissions = true
                },
                text = stringResource(R.string.remove_all_permissions),
                textColor = Color.White,
            )
        }
    }
}

fun rememberTypeIndexToRememberType(rememberTypeIndex: Int): RememberType = when (rememberTypeIndex) {
    0 -> RememberType.ALWAYS
    1 -> RememberType.ONE_MINUTE
    2 -> RememberType.FIVE_MINUTES
    3 -> RememberType.TEN_MINUTES
    else -> RememberType.NEVER
}

fun rememberTypeToIndex(rememberType: RememberType): Int = when (rememberType) {
    RememberType.ALWAYS -> 0
    RememberType.ONE_MINUTE -> 1
    RememberType.FIVE_MINUTES -> 2
    RememberType.TEN_MINUTES -> 3
    else -> 0
}

fun onSetPermission(optionIndex: Int, rememberTypeIndex: Int, permission: ApplicationPermissionsEntity, onToggle: (ApplicationPermissionsEntity) -> Unit) {
    val rememberType = rememberTypeIndexToRememberType(rememberTypeIndex)
    val time = when (rememberType) {
        RememberType.ALWAYS -> Long.MAX_VALUE / 1000
        RememberType.ONE_MINUTE -> TimeUtils.oneMinuteFromNow()
        RememberType.FIVE_MINUTES -> TimeUtils.now() + TimeUtils.FIVE_MINUTES
        RememberType.TEN_MINUTES -> TimeUtils.now() + TimeUtils.FIFTEEN_MINUTES
        RememberType.NEVER -> 0L
    }
    val isAcceptable = optionIndex == 0 || optionIndex == 2

    onToggle(
        permission.copy(
            acceptable = isAcceptable,
            acceptUntil = if (optionIndex == 2) {
                0L
            } else if (isAcceptable) {
                time
            } else {
                0L
            },
            rejectUntil = if (optionIndex == 2) {
                0L
            } else if (!isAcceptable) {
                time
            } else {
                0L
            },
            rememberType = rememberTypeIndexToRememberType(rememberTypeIndex).screenCode,
        ),
    )
}

@Composable
fun PermissionRow(
    permission: ApplicationPermissionsEntity,
    onToggle: (ApplicationPermissionsEntity) -> Unit,
    onDelete: (ApplicationPermissionsEntity) -> Unit,
) {
    val context = LocalContext.current
    val message = remember(permission.type, permission.kind, permission.acceptable) {
        val localPermission = Permission(
            permission.type.toLowerCase(Locale.current),
            permission.kind,
        )

        if (permission.type == "SIGN_EVENT" || permission.type == "NIP") {
            context.getString(
                R.string.sign,
                localPermission.toLocalizedString(context),
            )
        } else {
            localPermission.toLocalizedString(context)
        }
    }
    val fixedSegmentWidth = 55.dp

    var optionIndex by remember {
        if (permission.acceptUntil > 0) {
            mutableIntStateOf(0)
        } else if (permission.rejectUntil > 0) {
            mutableIntStateOf(1)
        } else {
            mutableIntStateOf(2)
        }
    }
    var rememberTypeIndex by remember {
        mutableIntStateOf(rememberTypeToIndex(parseRememberType(permission.rememberType)))
    }

    Column(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        AmberToggles(
            count = 3,
            selectedIndex = optionIndex,
        ) {
            ToggleOption(
                text = "Allow",
                isSelected = optionIndex == 0,
                modifier = Modifier.width(fixedSegmentWidth),
                onClick = {
                    optionIndex = 0

                    onSetPermission(
                        optionIndex,
                        rememberTypeIndex,
                        permission,
                        onToggle,
                    )
                },
            )
            ToggleOption(
                text = "Deny",
                isSelected = optionIndex == 1,
                modifier = Modifier.width(fixedSegmentWidth),
                onClick = {
                    optionIndex = 1

                    onSetPermission(
                        optionIndex,
                        rememberTypeIndex,
                        permission,
                        onToggle,
                    )
                },
            )
            ToggleOption(
                text = "Ask",
                isSelected = optionIndex == 2,
                modifier = Modifier.width(fixedSegmentWidth),
                onClick = {
                    optionIndex = 2

                    onSetPermission(
                        optionIndex,
                        rememberTypeIndex,
                        permission,
                        onToggle,
                    )
                },
            )
        }

        if (optionIndex != 2) {
            AmberToggles(
                selectedIndex = rememberTypeIndex,
                count = 4,
                content = {
                    ToggleOption(
                        text = "Always",
                        isSelected = rememberTypeIndex == 0,
                        modifier = Modifier.width(fixedSegmentWidth),
                        onClick = {
                            rememberTypeIndex = 0

                            onSetPermission(
                                optionIndex,
                                rememberTypeIndex,
                                permission,
                                onToggle,
                            )
                        },
                    )
                    ToggleOption(
                        text = "1m",
                        isSelected = rememberTypeIndex == 1,
                        modifier = Modifier.width(fixedSegmentWidth),
                        onClick = {
                            rememberTypeIndex = 1
                            onSetPermission(
                                optionIndex,
                                rememberTypeIndex,
                                permission,
                                onToggle,
                            )
                        },
                    )
                    ToggleOption(
                        text = "5m",
                        isSelected = rememberTypeIndex == 2,
                        modifier = Modifier.width(fixedSegmentWidth),
                        onClick = {
                            rememberTypeIndex = 2
                            onSetPermission(
                                optionIndex,
                                rememberTypeIndex,
                                permission,
                                onToggle,
                            )
                        },
                    )
                    ToggleOption(
                        text = "10m",
                        isSelected = rememberTypeIndex == 3,
                        modifier = Modifier.width(fixedSegmentWidth),
                        onClick = {
                            rememberTypeIndex = 3
                            onSetPermission(
                                optionIndex,
                                rememberTypeIndex,
                                permission,
                                onToggle,
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
fun RelayCard(
    modifier: Modifier = Modifier,
    relay: String,
    trustScore: Int? = null,
    isLoadingScore: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, Color.LightGray),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Row(
            modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                relay,
                Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            com.greenart7c3.nostrsigner.ui.components.TrustScoreBadge(
                score = trustScore,
                isLoading = isLoadingScore,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = onClick,
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.delete),
                    stringResource(R.string.delete),
                )
            }
        }
    }
}
