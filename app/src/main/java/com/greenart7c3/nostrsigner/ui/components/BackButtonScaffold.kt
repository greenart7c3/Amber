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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.navigation.routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackButtonScaffold(
    navController: NavController,
    title: String,
    backButtonTitle: String? = null,
    onNav: () -> Unit = {
        navController.navigateUp()
    },
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            val localBackButtonTitle = remember {
                backButtonTitle ?: routes.find { it.route == navController.previousBackStackEntry?.destination?.route }?.title ?: ""
            }
            BottomAppBar {
                IconRow(
                    title = stringResource(R.string.back_to, localBackButtonTitle),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onNav,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(title)
                },
            )
        },
    ) { padding ->
        content(padding)
    }
}
