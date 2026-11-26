package io.github.tomuius.cartilou.ui.theme

import androidx.compose.ui.graphics.Color

// Couleurs de la famille « ilou », reprises telles quelles de l'appli
// Canapilou (suivi-series/android, ui/theme/Color.kt, elles-mêmes reprises
// des variables css/style.css du site). Décision du 26/11/2026 : toute la
// palette de Canapilou, à l'identique (option A).
// Entre parenthèses : le nom de la variable correspondante dans Canapilou.

// --- Thème clair ---
val LightBackground = Color(0xFFF6F7F9)    // fond (FondClair)
val LightCard = Color(0xFFFFFFFF)          // cartes, barres (CarteClair)
val LightText = Color(0xFF1D2330)          // texte (TexteClair)
val LightSoftText = Color(0xFF5B6475)      // texte secondaire (TexteDouxClair)
val LightAccent = Color(0xFFC2185B)        // accent framboise, le « ilou » du logo (AccentClair)
val LightOnAccent = Color(0xFFFFFFFF)      // texte sur l'accent (AccentTexteClair)
val LightError = Color(0xFFB3261E)         // erreurs (ErreurClair)
val LightBorder = Color(0xFFD9DDE5)        // bordures, séparateurs (BordureClair)
val LightLogoStart = Color(0xFF7A1043)     // début du logo, « Canap » (LogoDebutClair)
val LightTint = Color(0xFFFCE4EC)          // fond teinté des pastilles (TeinteClair)

// --- Thème sombre ---
val DarkBackground = Color(0xFF14171C)     // FondSombre
val DarkCard = Color(0xFF1E232B)           // CarteSombre
val DarkText = Color(0xFFE8EBF0)           // TexteSombre
val DarkSoftText = Color(0xFFA3ABB8)       // TexteDouxSombre
val DarkAccent = Color(0xFFFF8AC4)         // rose (AccentSombre)
val DarkOnAccent = Color(0xFF0D1117)       // AccentTexteSombre
val DarkError = Color(0xFFF2A19B)          // ErreurSombre
val DarkBorder = Color(0xFF333A45)         // BordureSombre
val DarkLogoStart = Color(0xFFFFC9E0)      // LogoDebutSombre
val DarkTint = Color(0xFF4A1530)           // TeinteSombre
