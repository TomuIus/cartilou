package io.github.tomuius.cartilou.data.tcgdex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Cache des réponses de TCGdex, dans des fichiers sur le téléphone.
 *
 * Chaque réponse (le texte JSON brut) est enregistrée dans un fichier dont le
 * nom vient de la requête, par exemple « fr_sets_swsh3.json ». La date de
 * modification du fichier sert de date d'enregistrement.
 *
 * Règles :
 * - copie de moins de [maxAge] : on la réutilise, sans requête ;
 * - sinon on redemande à TCGdex, et on remplace la copie ;
 * - si la requête échoue (pas de réseau, serveur en panne) et qu'une copie
 *   existe, on la ressert même trop ancienne : mieux vaut des données d'il y
 *   a quelques jours que rien.
 *
 * Le dossier est dans le « cache » de l'appli : Android peut le vider s'il
 * manque de place, et « Vider le cache » dans les réglages de l'appli l'efface.
 * Rien de grave : les données sont simplement redemandées.
 */
class TcgdexCache(
    private val dir: File,
    /** Durée de validité d'une copie : 7 jours, à revoir après des tests. */
    private val maxAge: Duration = 7.days,
) {

    /** Renvoie la copie valide de [key], ou appelle [fetch] et enregistre le résultat. */
    suspend fun getOrFetch(key: String, fetch: suspend () -> String): String =
        // Dispatchers.IO : les lectures et écritures de fichiers se font hors
        // du fil principal, pour ne pas figer l'écran.
        withContext(Dispatchers.IO) {
            val file = fileFor(key)
            val age = if (file.exists()) System.currentTimeMillis() - file.lastModified() else null

            if (age != null && age < maxAge.inWholeMilliseconds) {
                return@withContext file.readText()
            }
            try {
                val fresh = fetch()
                save(file, fresh)
                fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Échec de la requête : l'ancienne copie s'il y en a une, sinon l'erreur.
                if (file.exists()) file.readText() else throw e
            }
        }

    /** Efface la copie de [key] (par exemple si elle est illisible). */
    suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }

    /** Le fichier d'une clé : seuls lettres, chiffres, « . », « _ » et « - » sont gardés dans le nom. */
    private fun fileFor(key: String): File =
        File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    /**
     * Écrit d'abord dans un fichier temporaire, puis le renomme : si l'appli
     * est fermée pendant l'écriture, l'ancienne copie reste intacte.
     */
    private fun save(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.delete()
        }
    }
}
