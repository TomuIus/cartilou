package io.github.tomuius.cartilou.data.tcgdex

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Le client TCGdex : toutes les requêtes vers l'API passent par ici.
 *
 * « object » : il n'en existe qu'un seul exemplaire dans toute l'appli. Il doit
 * être préparé une fois au démarrage avec [init] (fait dans CartilouApplication).
 *
 * Les fonctions sont « suspend » : elles attendent la réponse du réseau sans
 * bloquer l'écran (c'est le principe des coroutines). On les appelle depuis
 * un ViewModel.
 *
 * Chaque réponse passe par le cache (TcgdexCache.kt) : une donnée déjà
 * chargée depuis moins de 7 jours n'est pas redemandée.
 */
object Tcgdex {

    /** Langue des données. Elle fait partie de l'adresse et du nom des fichiers du cache. */
    private const val LANG = "fr"

    /** Adresse de l'API dans cette langue. */
    private const val API = "https://api.tcgdex.net/v2/$LANG"

    private val client = HttpClient(Android) {
        // Une réponse d'erreur (404, 500…) déclenche une exception, que
        // l'écran affiche comme une erreur.
        expectSuccess = true
    }

    /**
     * Lecture du JSON. On reçoit d'abord le texte brut (pour pouvoir
     * l'enregistrer tel quel dans le cache), puis on le transforme en objets
     * Kotlin ici.
     */
    private val json = Json {
        // Ignore les champs qu'on n'a pas déclarés dans TcgdexModels.kt.
        ignoreUnknownKeys = true
        // D'après la documentation, le numéro d'une carte (localId) peut
        // être un texte ou un nombre : on accepte les deux.
        isLenient = true
    }

    private lateinit var cache: TcgdexCache

    /** À appeler une fois au démarrage de l'appli, avec le dossier cache d'Android. */
    fun init(cacheDir: File) {
        cache = TcgdexCache(File(cacheDir, "tcgdex"))
    }

    /** Toutes les séries (GET /v2/fr/series). */
    suspend fun series(): List<SerieBrief> = getCached("series")

    /** Une série et ses extensions (GET /v2/fr/series/{id}). */
    suspend fun serie(id: String): Serie = getCached("series", id)

    /** Une extension et ses cartes (GET /v2/fr/sets/{id}). */
    suspend fun set(id: String): CardSet = getCached("sets", id)

    /** Une carte complète (GET /v2/fr/cards/{id}, par exemple swsh3-136). */
    suspend fun card(id: String): Card = getCached("cards", id)

    /**
     * Toutes les cartes, en bref : identifiant, numéro, nom, image
     * (GET /v2/fr/cards). Sert au scan, qui retrouve une carte d'après son
     * nom et son numéro sans rien demander au réseau à chaque image.
     * D'après la documentation de TCGdex, la pagination est désactivée par
     * défaut : on reçoit toute la liste d'un coup (taille non vérifiée,
     * probablement quelques Mo ; gardée 7 jours dans le cache comme le reste).
     */
    suspend fun allCards(): List<CardBrief> = getCached("cards")

    /** Toutes les extensions, en bref, avec leur nombre de cartes (GET /v2/fr/sets). */
    suspend fun allSets(): List<CardSetBrief> = getCached("sets")

    /**
     * Demande une adresse (par exemple « sets », « swsh3 ») en passant par le
     * cache, puis transforme le JSON en objet du type attendu (T).
     *
     * « inline » + « reified » : Kotlin garde le type T connu à l'exécution,
     * ce qui permet à la lecture du JSON de savoir quel objet construire.
     */
    private suspend inline fun <reified T> getCached(vararg path: String): T {
        // Clé du cache, par exemple « fr_sets_swsh3 ».
        val key = (listOf(LANG) + path).joinToString("_")
        val text = cache.getOrFetch(key) { fetchText(*path) }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            // Copie illisible (abîmée, ou d'une forme ancienne) : on l'efface et
            // on redemande une seule fois à TCGdex.
            cache.remove(key)
            json.decodeFromString<T>(cache.getOrFetch(key) { fetchText(*path) })
        }
    }

    /** La requête elle-même : renvoie le texte JSON brut. */
    private suspend fun fetchText(vararg path: String): String =
        client.get(API) { url { appendPathSegments(*path) } }.bodyAsText()
}

// Adresses des images. TCGdex donne une adresse de base, à compléter :
// - cartes : « /low.webp » (245 × 337) ou « /high.webp » (600 × 825) ;
// - logos et symboles : « .webp ».
// Le format webp est le plus compact ; le png existe aussi.
// (Les images ne passent pas par ce cache : Coil a le sien.)
//
// Chaque fonction renvoie la liste des adresses à essayer dans l'ordre (voir
// ui/common/FallbackImage.kt). Liste vide = TCGdex ne donne pas d'image.

/**
 * Image d'une carte, en petite (« low ») ou grande (« high ») qualité :
 * en webp, puis en png. Toujours dans la langue de la carte (pas d'image
 * anglaise, pour ne pas montrer une carte différente de la carte française).
 */
fun cardImageUrls(image: String?, quality: String = "low"): List<String> =
    if (image == null) emptyList() else listOf("$image/$quality.webp", "$image/$quality.png")

/**
 * Logo ou symbole d'une série ou d'une extension : en français (webp, puis
 * png), puis en anglais en dernier recours (un logo reste reconnaissable).
 * Les symboles sont communs à toutes les langues (« /univ/ » dans l'adresse) :
 * dans ce cas, pas de version anglaise à essayer.
 */
fun assetUrls(asset: String?): List<String> {
    if (asset == null) return emptyList()
    val english = asset.replace("/fr/", "/en/")
    return buildList {
        add("$asset.webp")
        add("$asset.png")
        if (english != asset) add("$english.webp")
    }
}
