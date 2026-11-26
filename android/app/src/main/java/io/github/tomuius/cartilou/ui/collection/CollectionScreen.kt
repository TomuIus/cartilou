package io.github.tomuius.cartilou.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.AccountState
import io.github.tomuius.cartilou.ui.common.CartilouTopBar

/**
 * L'onglet « Ma collection » — version provisoire : il ne fait pour l'instant
 * que vérifier la connexion. La vraie collection arrivera aux étapes
 * suivantes (ajout depuis la fiche d'une carte, puis liste par série).
 *
 * [onSignIn] : ouvre l'onglet Compte pour se connecter.
 */
@Composable
fun CollectionScreen(onSignIn: () -> Unit) {
    val state by Account.state.collectAsStateWithLifecycle(initialValue = AccountState.Loading)

    Scaffold(topBar = { CartilouTopBar("Ma collection") }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                AccountState.Loading -> CircularProgressIndicator()
                AccountState.SignedOut -> {
                    Text("Connectez-vous pour voir votre collection.", textAlign = TextAlign.Center)
                    Button(onClick = onSignIn) { Text("Se connecter") }
                }
                is AccountState.SignedIn -> Text(
                    text = "Votre collection s'affichera ici.\n" +
                        "Prochaine étape : ajouter des cartes depuis leur fiche.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
