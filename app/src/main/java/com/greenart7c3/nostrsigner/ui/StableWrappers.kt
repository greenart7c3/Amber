package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

@Immutable
class IntentWrapper(val intent: Intent?)

@Immutable
class NavHostControllerWrapper(val navController: NavHostController)

@Immutable
class NavBackStackEntryWrapper(val navBackStackEntry: NavBackStackEntry?)
