package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmberBottomBar(
    items: List<Route>,
    navController: NavHostControllerWrapper,
    destinationRoute: String,
    scope: CoroutineScope,
    sheetState: SheetState,
    onShouldShowBottomSheet: (Boolean) -> Unit,
    profileUrl: String?,
    account: Account,
) {
    if (destinationRoute in items.map { it.route }) {
        AmberNavigationBar(
            items = items,
            destinationRoute = destinationRoute,
            onClick = {
                if (it.route == Route.Accounts.route) {
                    scope.launch {
                        sheetState.show()
                        onShouldShowBottomSheet(true)
                    }
                } else {
                    navController.navController.navigate(it.route) {
                        popUpTo(0)
                    }
                }
            },
            profileUrl = profileUrl,
            account = account,
        )
    } else if (destinationRoute != "create" && destinationRoute != "loginPage") {
        val localBackButtonTitle = routes.find { it.route == navController.navController.previousBackStackEntry?.destination?.route }?.title ?: ""
        if (localBackButtonTitle.isNotBlank()) {
            BackButtonAppBar(
                destinationRoute = destinationRoute,
                localBackButtonTitle = localBackButtonTitle,
                onPressed = {
                    if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                        navController.navController.navigate(Route.Applications.route) {
                            popUpTo(0)
                        }
                    } else {
                        navController.navController.navigateUp()
                    }
                },
            )
        }
    }
}
