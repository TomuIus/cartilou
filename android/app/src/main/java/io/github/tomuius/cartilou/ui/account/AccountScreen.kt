package io.github.tomuius.cartilou.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.AccountState
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.TilouImage
import kotlinx.coroutines.launch

/**
 * Le ViewModel de l'onglet Compte : lance la connexion et la déconnexion, et
 * garde ce qu'il faut afficher pendant ce temps (attente, message d'erreur).
 *
 * « mutableStateOf » : une valeur que Compose observe ; l'écran se redessine
 * dès qu'elle change.
 */
class AccountViewModel : ViewModel() {

    /** Vrai pendant une connexion ou une déconnexion en cours. */
    var busy by mutableStateOf(false)
        private set

    /** Message d'erreur à afficher, ou null. */
    var error by mutableStateOf<String?>(null)
        private set

    fun signIn(email: String, password: String) = launchAction { Account.signIn(email.trim(), password) }

    fun signOut() = launchAction { Account.signOut() }

    /** Lance [action] en tâche de fond, avec l'indicateur d'attente et la gestion d'erreur. */
    private fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
            } catch (e: DisplayableException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}

/** L'onglet Compte : formulaire de connexion, ou compte connecté avec le bouton de déconnexion. */
@Composable
fun AccountScreen() {
    val viewModel = viewModel { AccountViewModel() }
    val state by Account.state.collectAsStateWithLifecycle(initialValue = AccountState.Loading)

    Scaffold(topBar = { CartilouTopBar("Compte") }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Contenu centré, limité en largeur, qui défile si le clavier prend de la place.
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val current = state) {
                    AccountState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    AccountState.SignedOut -> SignInForm(
                        busy = viewModel.busy,
                        error = viewModel.error,
                        onSignIn = viewModel::signIn,
                    )
                    is AccountState.SignedIn -> SignedInPanel(
                        displayName = current.displayName,
                        busy = viewModel.busy,
                        error = viewModel.error,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
        }
    }
}

/** Le formulaire de connexion : e-mail, mot de passe, bouton. */
@Composable
private fun SignInForm(busy: Boolean, error: String?, onSignIn: (String, String) -> Unit) {
    // rememberSaveable : l'e-mail tapé survit à une rotation de l'écran.
    // Le mot de passe, lui, n'est gardé qu'en mémoire (remember), par prudence.
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = !busy && email.isNotBlank() && password.isNotEmpty()

    // Tilou et le nom de l'appli, centrés, comme sur l'écran de connexion de
    // Canapilou. Hauteur de Tilou : 180 dp, choix de Claude (à revoir après des tests).
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TilouImage(height = 180.dp)
        Text(
            text = "Cartilou",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text("Connexion", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Utilisez le même compte que Canapilou.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("E-mail") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Mot de passe") },
        singleLine = true,
        // Les caractères tapés sont remplacés par des points.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        // Touche « OK » du clavier : lance la connexion.
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSignIn(email, password) }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = { onSignIn(email, password) },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Se connecter")
        }
    }
}

/** Le compte connecté : nom affiché et bouton de déconnexion. */
@Composable
private fun SignedInPanel(displayName: String, busy: Boolean, error: String?, onSignOut: () -> Unit) {
    Text("Connecté", style = MaterialTheme.typography.headlineSmall)
    Text(displayName, style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Ce compte est commun à Canapilou et Cartilou.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Se déconnecter")
    }
}
