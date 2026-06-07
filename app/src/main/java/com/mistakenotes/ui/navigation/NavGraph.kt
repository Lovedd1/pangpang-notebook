package com.mistakenotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mistakenotes.ui.screens.*
import com.mistakenotes.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Import : Screen("import?mistakeId={mistakeId}")
    data object Review : Screen("review")
    data object Analysis : Screen("analysis")
    data object Browse : Screen("browse?favorites={favorites}&mastered={mastered}")
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToImport = { mistakeId ->
                    navController.navigate("import?mistakeId=${mistakeId ?: -1}")
                },
                onNavigateToReview = { navController.navigate(Screen.Review.route) },
                onNavigateToAnalysis = { navController.navigate(Screen.Analysis.route) },
                onNavigateToBrowse = { navController.navigate("browse?favorites=false&mastered=false") },
                onNavigateToFavorites = { navController.navigate("browse?favorites=true&mastered=false") },
                onNavigateToMastered = { navController.navigate("browse?favorites=false&mastered=true") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Import.route,
            arguments = listOf(
                navArgument("mistakeId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analysis.route) {
            AnalysisScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Browse.route,
            arguments = listOf(
                navArgument("favorites") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("mastered") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            BrowseScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReview = { navController.navigate(Screen.Review.route) },
                onNavigateToImport = { mistakeId ->
                    navController.navigate("import?mistakeId=$mistakeId")
                }
            )
        }
    }
}
