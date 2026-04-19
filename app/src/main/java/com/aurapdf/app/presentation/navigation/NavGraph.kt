package com.aurapdf.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aurapdf.app.presentation.home.HomeScreen
import com.aurapdf.app.presentation.viewer.PdfViewerScreen

private const val HOME_ROUTE = "home"
private const val VIEWER_ROUTE = "viewer/{documentId}"

@Composable
fun AuraPdfNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE
    ) {
        composable(HOME_ROUTE) {
            HomeScreen(
                onOpenDocument = { documentId ->
                    navController.navigate("viewer/$documentId")
                }
            )
        }

        composable(
            route = VIEWER_ROUTE,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType }
            )
        ) {
            PdfViewerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
