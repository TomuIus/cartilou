package io.github.tomuius.cartilou.data.scan

import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import java.text.Normalizer

/** Ce que la lecture d'une image a donné. */
sealed interface MatchResult {
    /** Rien d'exploitable : pas de numéro du type « 136/189 » lisible. */
    data object NoNumber : MatchResult

    /** Un numéro lu, mais aucune carte de ce numéro dont le nom figure dans le texte lu. */
    data class NumberOnly(val number: String) : MatchResult

    /** Une seule carte correspond. */
    data class One(val card: CardBrief) : MatchResult

    /** Plusieurs cartes correspondent (rééditions) : il faudra choisir. */
    data class Several(val cards: List<CardBrief>) : MatchResult
}

/**
 * Retrouve une carte d'après le texte lu sur la photo (décision du
 * 27/11/2026 : lecture du nom et du numéro, option A).
 *
 * Principe :
 * 1. chercher dans le texte un numéro du type « 136/189 » (numéro de la
 *    carte / nombre de cartes imprimé de l'extension) ;
 * 2. prendre toutes les cartes TCGdex qui portent ce numéro ;
 * 3. garder celles dont le nom apparaît dans le texte lu ;
 * 4. si possible, garder celles dont l'extension compte bien 189 cartes.
 *
 * Construit une fois à partir de la liste complète des cartes et des
 * extensions (Tcgdex.allCards / allSets) ; ensuite, chaque lecture ne fait
 * que des recherches en mémoire, sans réseau.
 */
class CardMatcher(cards: List<CardBrief>, sets: List<CardSetBrief>) {

    private class Entry(val card: CardBrief, val normalizedName: String, val official: Int?)

    /** Les cartes rangées par numéro (normalisé : « 036 » et « 36 » sont rangés ensemble). */
    private val byNumber: Map<String, List<Entry>>

    /** Nom de chaque extension, d'après son identifiant. */
    private val setNames: Map<String, String> = sets.associate { it.id to it.name }

    init {
        val officialBySet = sets.associate { it.id to it.cardCount?.official }
        byNumber = cards.groupBy { normalizeNumber(it.localId) }.mapValues { (_, group) ->
            group.map { card -> Entry(card, normalizeText(card.name), officialBySet[setIdOf(card)]) }
        }
    }

    /** Le nom de l'extension d'une carte (pour départager des rééditions), si connu. */
    fun setNameOf(card: CardBrief): String? = setNames[setIdOf(card)]

    fun match(text: String): MatchResult {
        val numbers = NUMBER_REGEX.findAll(text).toList()
        if (numbers.isEmpty()) return MatchResult.NoNumber
        val normalizedText = normalizeText(text)
        val found = linkedMapOf<String, CardBrief>()
        for (number in numbers) {
            val (local, total) = number.destructured
            val totalNumber = total.toIntOrNull()
            byNumber[normalizeNumber(local)].orEmpty()
                .filter { it.normalizedName.length >= 3 && normalizedText.contains(it.normalizedName) }
                // Total imprimé : vérifié seulement s'il est connu des deux côtés.
                .filter { totalNumber == null || it.official == null || it.official == totalNumber }
                .forEach { found[it.card.id] = it.card }
        }
        return when (found.size) {
            0 -> MatchResult.NumberOnly(numbers.first().value.replace(" ", ""))
            1 -> MatchResult.One(found.values.first())
            else -> MatchResult.Several(found.values.toList())
        }
    }

    private companion object {
        /**
         * « 136/189 », « 136 / 189 », « TG05/TG30 »… : jusqu'à 4 lettres puis
         * 1 à 3 chiffres, de part et d'autre de la barre. Les promos sans
         * barre (« SWSH045 ») ne sont pas reconnues pour l'instant.
         */
        val NUMBER_REGEX = Regex("""(?<![A-Za-z0-9])([A-Za-z]{0,4}\d{1,3})\s*/\s*([A-Za-z]{0,4}\d{1,3})(?![0-9])""")

        /** Minuscules, sans accents, seulement lettres et chiffres : « Dracaufeu-ex » → « dracaufeuex ». */
        fun normalizeText(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("""\p{Mn}+"""), "")
                .lowercase()
                .filter { it in 'a'..'z' || it in '0'..'9' }

        /** Majuscules et sans zéros en tête des chiffres : « 036 » → « 36 », « TG05 » → « TG5 ». */
        fun normalizeNumber(value: String): String =
            value.uppercase().replace(Regex("""(?<![0-9])0+(?=[0-9])"""), "")

        /**
         * L'extension d'une carte, d'après son identifiant : « swsh3-136 » →
         * « swsh3 ». Hypothèse (vérifiée sur les exemples de la documentation
         * TCGdex, pas sur toutes les cartes) : identifiant = extension + « - » +
         * numéro. Si elle est fausse pour une carte, seule la vérification du
         * total imprimé saute pour cette carte.
         */
        fun setIdOf(card: CardBrief): String = card.id.removeSuffix("-${card.localId}")
    }
}
