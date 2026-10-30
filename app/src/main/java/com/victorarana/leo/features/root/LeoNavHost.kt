package com.victorarana.leo.features.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * The graph of the onboarded app: Home, with History pushed over it. Back from History pops (iOS: the
 * `NavigationStack`). The placeholders are replaced in phases 6 and 7.
 */
@Composable
fun LeoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = LeoRoute.Home, modifier = modifier) {
        composable<LeoRoute.Home> {
            HomePlaceholder(onHistory = { navController.navigate(LeoRoute.History) })
        }
        composable<LeoRoute.History> { HistoryPlaceholder() }
    }
}
