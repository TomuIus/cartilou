package io.github.tomuius.cartilou.data.supabase

import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Une erreur dont le message est rédigé en français, prêt à être affiché
 * tel quel à l'écran.
 */
class DisplayableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exécute [action] et transforme toute erreur en [DisplayableException],
 * avec un message en français.
 *
 * Deux exceptions passent telles quelles :
 * - l'annulation (l'écran a été fermé, par exemple) : c'est un arrêt normal,
 *   pas une erreur à afficher ;
 * - une [DisplayableException] déjà rédigée.
 */
internal suspend fun <T> withFrenchErrors(action: suspend () -> T): T =
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DisplayableException) {
        throw e
    } catch (e: Exception) {
        throw DisplayableException(frenchMessage(e), e)
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
