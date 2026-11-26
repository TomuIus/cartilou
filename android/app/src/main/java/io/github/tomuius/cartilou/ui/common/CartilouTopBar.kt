package io.github.tomuius.cartilou.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.tomuius.cartilou.R

/**
 * La barre du haut, commune à tous les écrans : un titre et, si « onBack »
 * est fourni, une flèche de retour.
 *
 * @OptIn : TopAppBar est marquée « expérimentale » dans Material 3 (son
 * fonctionnement peut encore changer) ; on accepte ce risque explicitement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartilouTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_retour), contentDescription = "Retour")
                }
            }
        },
    )
}
