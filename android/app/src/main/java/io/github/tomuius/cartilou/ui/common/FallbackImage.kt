package io.github.tomuius.cartilou.ui.common

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

private const val TAG = "Cartilou"

/**
 * Affiche une image en essayant plusieurs adresses dans l'ordre : si la
 * première ne se charge pas, on passe à la suivante, etc. Si aucune ne
 * marche (ou si la liste est vide), on affiche « onAllFailed » à la place,
 * dans la même zone.
 *
 * Chaque échec est noté dans Logcat (Android Studio), avec l'étiquette
 * « Cartilou », l'adresse et la cause : on peut ainsi ouvrir l'adresse dans un
 * navigateur pour comprendre ce qui se passe.
 */
@Composable
fun FallbackImage(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onAllFailed: @Composable () -> Unit = {},
) {
    // Position de l'adresse en cours d'essai. « remember(urls) » : on repart de
    // la première si la liste change (autre carte affichée à cet endroit).
    var index by remember(urls) { mutableIntStateOf(0) }

    if (index < urls.size) {
        AsyncImage(
            model = urls[index],
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            onError = { error ->
                Log.w(TAG, "Image non chargée : ${urls[index]} — ${error.result.throwable}")
                index++
            },
        )
    } else {
        Box(modifier) { onAllFailed() }
    }
}
