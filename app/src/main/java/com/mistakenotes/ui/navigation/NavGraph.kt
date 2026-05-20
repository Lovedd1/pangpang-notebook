package com.mistakenotes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mistakenotes.ui.screens.*

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Import : Screen("import")
    data object Review : Screen("review")
    data object Analysis : Screen("analysis")
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
                onNavigateToImport = { navController.navigate(Screen.Import.route) },
                onNavigateToReview = { navController.navigate(Screen.Review.route) },
                onNavigateToAnalysis = { navController.navigate(Screen.Analysis.route) }
            )
        }

        composable(Screen.Import.route) {
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
    }
}