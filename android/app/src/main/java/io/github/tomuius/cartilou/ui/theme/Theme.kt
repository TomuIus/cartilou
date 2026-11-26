package io.github.tomuius.cartilou.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Le thème de l'appli : les couleurs « ilou » (voir Color.kt) placées dans les
// « rôles » de Material 3, exactement comme dans Canapilou :
// - primary : l'accent (boutons, badge « ×2 », onglet choisi…) ;
// - secondaryContainer : le fond teinté (pastilles, bouton « + », filtre choisi) ;
// - background : le fond de l'écran ; surface et surfaceContainer… : cartes,
//   barres du haut et du bas, listes ;
// - onSurfaceVariant : le texte secondaire (gris).
//
// Les couleurs dynamiques d'Android 12+ (tirées du fond d'écran du
// téléphone), activées par le modèle d'Android Studio, ne sont plus
// utilisées : l'appli garde l'identité de la famille « ilou ».

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightTint,
    onPrimaryContainer = LightLogoStart,
    secondary = LightLogoStart,
    onSecondary = Color.White,
    secondaryContainer = LightTint,
    onSecondaryContainer = LightLogoStart,
    tertiary = LightAccent,
    onTertiary = LightOnAccent,
    background = LightBackground,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightSoftText,
    surfaceContainerLowest = LightCard,
    surfaceContainerLow = LightCard,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightCard,
    surfaceContainerHighest = LightCard,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightError,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkTint,
    onPrimaryContainer = DarkLogoStart,
    secondary = DarkLogoStart,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkTint,
    onSecondaryContainer = DarkLogoStart,
    tertiary = DarkAccent,
    onTertiary = DarkOnAccent,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = DarkSoftText,
    surfaceContainerLowest = DarkCard,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkCard,
    surfaceContainerHighest = DarkCard,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkError,
    onError = DarkOnAccent,
)

/**
 * Le thème de Cartilou. [darkTheme] : clair ou sombre, d'après le réglage du
 * téléphone (un réglage dans l'appli, comme dans Canapilou, pourra venir plus tard).
 */
@Composable
fun CartilouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
