package io.github.tomuius.cartilou.data.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Où en est la connexion, du point de vue des écrans. */
sealed interface AccountState {
    /** Au lancement, le temps de relire la session gardée sur le téléphone. */
    data object Loading : AccountState

    /** Personne n'est connecté. */
    data object SignedOut : AccountState

    /** Connecté. [displayName] : le nom choisi dans Canapilou, sinon l'e-mail. */
    data class SignedIn(val displayName: String) : AccountState
}

/**
 * Le compte : connexion, déconnexion, et état de la connexion.
 *
 * C'est le même compte que Canapilou (même projet Supabase) : on se connecte
 * avec le même e-mail et le même mot de passe. Pas d'inscription dans
 * l'appli, comme dans Canapilou.
 */
object Account {

    private val auth get() = Supabase.client.auth

    /**
     * L'état de la connexion, qui change dans le temps (connexion,
     * déconnexion, session relue au lancement…).
     *
     * Autre cas (« RefreshFailure ») : la session a expiré et n'a pas pu être
     * renouvelée, souvent faute de réseau. On reste considéré comme connecté :
     * la session sera renouvelée au retour du réseau.
     */
    val state: Flow<AccountState> = auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AccountState.SignedIn(displayName(status.session.user))
            is SessionStatus.NotAuthenticated -> AccountState.SignedOut
            SessionStatus.Initializing -> AccountState.Loading
            else -> auth.currentUserOrNull()?.let { AccountState.SignedIn(displayName(it)) }
                ?: AccountState.SignedOut
        }
    }

    /**
     * Connexion par e-mail et mot de passe. En cas d'échec, lève une
     * [DisplayableException] avec un message en français (voir Errors.kt).
     */
    suspend fun signIn(email: String, password: String) = withFrenchErrors {
        auth.signInWith(Email) {
            // À gauche, les champs du formulaire de Supabase ; à droite, les valeurs reçues.
            this.email = email
            this.password = password
        }
    }

    /** Déconnexion (sur ce téléphone). */
    suspend fun signOut() = withFrenchErrors {
        auth.signOut()
    }

    /**
     * L'identifiant Supabase de la personne connectée, ou null.
     * C'est la valeur de la colonne « utilisateur_id » de ses cartes.
     */
    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    /**
     * Le nom affiché : celui choisi dans les paramètres de Canapilou
     * (« nom_utilisateur », enregistré dans le compte Supabase lui-même),
     * sinon l'adresse e-mail.
     */
    private fun displayName(user: UserInfo?): String {
        val name = user?.userMetadata?.get("nom_utilisateur")?.jsonPrimitive?.contentOrNull
        return name?.takeIf { it.isNotBlank() } ?: user?.email ?: "Compte sans e-mail"
    }
}
