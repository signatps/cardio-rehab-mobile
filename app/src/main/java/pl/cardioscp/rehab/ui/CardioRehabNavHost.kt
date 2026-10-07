package pl.cardioscp.rehab.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.cardioscp.rehab.ui.screens.HomeScreen

object Routes {
    const val HOME = "home"
}

@Composable
fun CardioRehabNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
