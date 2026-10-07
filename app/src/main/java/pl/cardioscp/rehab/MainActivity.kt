package pl.cardioscp.rehab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pl.cardioscp.rehab.ui.CardioRehabNavHost
import pl.cardioscp.rehab.ui.theme.CardioRehabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardioRehabTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CardioRehabNavHost()
                }
            }
        }
    }
}
