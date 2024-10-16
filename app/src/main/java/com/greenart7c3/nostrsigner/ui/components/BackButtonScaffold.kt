package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackButtonScaffold(
    navController: NavController,
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    val title = remember {
        navController.previousBackStackEntry?.destination?.route?.let { context.getString(R.string.back_to, it) } ?: ""
    }
    Scaffold(
        bottomBar = {
            BottomAppBar {
                IconRow(
                    title = title,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = {
                        navController.navigateUp()
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.add_a_new_application))
                },
            )
        },
    ) { padding ->
        content(padding)
    }
}
