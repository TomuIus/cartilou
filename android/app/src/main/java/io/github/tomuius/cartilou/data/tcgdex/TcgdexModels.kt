package io.github.tomuius.cartilou.data.tcgdex

import kotlinx.serialization.Serializable

// Les données renvoyées par TCGdex, sous forme d'objets Kotlin.
//
// @Serializable : le plugin de sérialisation génère le code qui remplit ces
// objets à partir du JSON. Chaque nom de champ doit être identique à celui du
// JSON (par exemple « localId »). On ne déclare que les champs utiles :
// les autres sont ignorés (réglage ignoreUnknownKeys dans Tcgdex.kt).
//
// Un champ « String? = null » peut être absent ou vide dans la réponse (par
// exemple une image manquante) sans faire échouer la lecture.
//
// « Set » est déjà un mot de Kotlin (un ensemble), d'où le nom CardSet pour
// une extension.

/** Une série (un « bloc », par exemple Écarlate et Violet), telle qu'elle apparaît dans la liste. */
@Serializable
data class SerieBrief(
    val id: String,
    val name: String,
    val logo: String? = null,
)

/** Une série complète, avec la liste de ses extensions. */
@Serializable
data class Serie(
    val id: String,
    val name: String,
    val logo: String? = null,
    val sets: List<CardSetBrief> = emptyList(),
)

/** Nombre de cartes d'une extension. */
@Serializable
data class CardCount(
    /** Toutes les cartes, y compris les cartes « secrètes » numérotées au-delà du total imprimé. */
    val total: Int? = null,
    /** Le total imprimé sur les cartes (le « 198 » de « 025/198 »). */
    val official: Int? = null,
)

/** Une extension, telle qu'elle apparaît dans la liste d'une série. */
@Serializable
data class CardSetBrief(
    val id: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null,
    val cardCount: CardCount? = null,
)

/** Une extension complète, avec la liste de ses cartes. */
@Serializable
data class CardSet(
    val id: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null,
    /** Date de sortie au format aaaa-mm-jj. */
    val releaseDate: String? = null,
    val serie: SerieBrief? = null,
    val cardCount: CardCount? = null,
    val cards: List<CardBrief> = emptyList(),
)

/** Une carte, telle qu'elle apparaît dans la liste d'une extension. */
@Serializable
data class CardBrief(
    /** Identifiant unique, identique dans toutes les langues (par exemple « swsh3-136 »). */
    val id: String,
    /** Numéro de la carte dans son extension (par exemple « 136 »). */
    val localId: String,
    val name: String,
    /** Adresse de base de l'image, sans qualité ni format (voir cardImageUrl). */
    val image: String? = null,
)
