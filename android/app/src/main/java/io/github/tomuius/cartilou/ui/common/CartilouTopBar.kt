package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.layout.RowScope
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
 * est fourni, une flèche de retour. [actions] : des boutons à droite (aucun
 * par défaut).
 *
 * @OptIn : TopAppBar est marquée « expérimentale » dans Material 3 (son
 * fonctionnement peut encore changer) ; on accepte ce risque explicitement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartilouTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CartilouTopBar(onBack = onBack, actions = actions) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** La barre du haut avec le logo (Tilou + « Cartilou ») à la place du titre. */
@Composable
fun CartilouLogoTopBar() {
    CartilouTopBar(onBack = null, actions = {}) { CartilouLogo() }
}

/** La barre du haut, avec un contenu de titre quelconque. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartilouTopBar(
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    titleContent: @Composable () -> Unit,
) {
    TopAppBar(
        title = titleContent,
        actions = actions,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_retour), contentDescription = "Retour")
                }
            }
        },
    )
}
