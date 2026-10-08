package pl.cardioscp.rehab.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.cardioscp.rehab.ui.screens.BrandSplashScreen
import pl.cardioscp.rehab.ui.screens.HomeScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
}

@Composable
fun CardioRehabNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            BrandSplashScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
