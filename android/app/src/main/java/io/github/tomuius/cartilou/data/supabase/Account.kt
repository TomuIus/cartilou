package io.github.tomuius.cartilou.data.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

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

    /** Connexion par e-mail et mot de passe. En cas d'échec, lève une erreur avec un message en français. */
    suspend fun signIn(email: String, password: String) {
        try {
            auth.signInWith(Email) {
                // À gauche, les champs du formulaire de Supabase ; à droite, les valeurs reçues.
                this.email = email
                this.password = password
            }
        } catch (e: CancellationException) {
            throw e // annulation normale (écran fermé) : on la laisse passer
        } catch (e: Exception) {
            throw AccountException(frenchMessage(e), e)
        }
    }

    /** Déconnexion (sur ce téléphone). */
    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AccountException(frenchMessage(e), e)
        }
    }

    /**
     * Le nom affiché : celui choisi dans les paramètres de Canapilou
     * (« nom_utilisateur », enregistré dans le compte Supabase lui-même),
     * sinon l'adresse e-mail.
     */
    private fun displayName(user: UserInfo?): String {
        val name = user?.userMetadata?.get("nom_utilisateur")?.jsonPrimitive?.contentOrNull
        return name?.takeIf { it.isNotBlank() } ?: user?.email ?: "Compte sans e-mail"
    }

    /**
     * Les mêmes messages que Canapilou (suivi-series/js/base.js), plus un
     * message pour l'absence de réseau. « error » est le code brut renvoyé
     * par Supabase (par exemple « invalid_credentials »).
     */
    private fun frenchMessage(e: Exception): String = when {
        e is AuthRestException -> when (e.error) {
            "invalid_credentials" -> "E-mail ou mot de passe incorrect."
            "email_not_confirmed" -> "Cette adresse e-mail n'a pas encore été confirmée."
            "over_request_rate_limit" -> "Trop de tentatives. Patientez quelques minutes avant de réessayer."
            else -> "Erreur : ${e.errorDescription}"
        }
        // Une erreur réseau se trouve souvent « à l'intérieur » d'une autre :
        // on regarde la chaîne des causes.
        generateSequence<Throwable>(e) { it.cause }.any { it is IOException } ->
            "Impossible de joindre le serveur. Vérifiez la connexion à Internet."
        else -> "Erreur : ${e.message ?: e}"
    }
}

/** Erreur de connexion ou de déconnexion, avec un message prêt à afficher. */
class AccountException(message: String, cause: Throwable) : Exception(message, cause)
