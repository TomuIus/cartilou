package io.github.tomuius.cartilou.data.tcgdex

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Le client TCGdex : toutes les requêtes vers l'API passent par ici.
 *
 * « object » : il n'en existe qu'un seul exemplaire dans toute l'appli, créé à
 * la première utilisation. Les écrans l'appellent directement.
 *
 * Les fonctions sont « suspend » : elles attendent la réponse du réseau sans
 * bloquer l'écran (c'est le principe des coroutines). On les appelle depuis
 * un ViewModel.
 */
object Tcgdex {

    /** Adresse de l'API, en français. La langue fait partie de l'adresse. */
    private const val API = "https://api.tcgdex.net/v2/fr"

    private val client = HttpClient(Android) {
        // Une réponse d'erreur (404, 500…) déclenche une exception, que
        // l'écran affiche comme une erreur.
        expectSuccess = true
        // Transforme automatiquement le JSON reçu en objets Kotlin.
        install(ContentNegotiation) {
            json(
                Json {
                    // Ignore les champs qu'on n'a pas déclarés dans TcgdexModels.kt.
                    ignoreUnknownKeys = true
                    // D'après la documentation, le numéro d'une carte (localId) peut
                    // être un texte ou un nombre : on accepte les deux.
                    isLenient = true
                }
            )
        }
    }

    /** Toutes les séries (GET /v2/fr/series). */
    suspend fun series(): List<SerieBrief> =
        client.get(API) { url { appendPathSegments("series") } }.body()

    /** Une série et ses extensions (GET /v2/fr/series/{id}). */
    suspend fun serie(id: String): Serie =
        client.get(API) { url { appendPathSegments("series", id) } }.body()

    /** Une extension et ses cartes (GET /v2/fr/sets/{id}). */
    suspend fun set(id: String): CardSet =
        client.get(API) { url { appendPathSegments("sets", id) } }.body()
}

// Adresses des images. TCGdex donne une adresse de base, à compléter :
// - cartes : « /low.webp » (245 × 337) ou « /high.webp » (600 × 825) ;
// - logos et symboles : « .webp ».
// Le format webp est le plus compact. Les fonctions renvoient null s'il n'y a
// pas d'image.

/** Image d'une carte, en petite (« low ») ou grande (« high ») qualité. */
fun cardImageUrl(image: String?, quality: String = "low"): String? =
    image?.let { "$it/$quality.webp" }

/** Logo ou symbole d'une série ou d'une extension. */
fun assetUrl(asset: String?): String? =
    asset?.let { "$it.webp" }
