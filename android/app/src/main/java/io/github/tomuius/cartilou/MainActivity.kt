package io.github.tomuius.cartilou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.tomuius.cartilou.ui.navigation.CartilouNavigation
import io.github.tomuius.cartilou.ui.theme.CartilouTheme

/**
 * Point d'entrée de l'appli : applique le thème, puis laisse la navigation
 * afficher les écrans (voir ui/navigation/CartilouNavigation.kt).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CartilouTheme {
                CartilouNavigation()
            }
        }
    }
}
