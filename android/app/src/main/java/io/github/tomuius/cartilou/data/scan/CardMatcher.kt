package io.github.tomuius.cartilou.data.scan

import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import java.text.Normalizer

/**
 * Une ligne lue dans le cadre-guide, avec sa hauteur sur la carte :
 * 0 = haut de la carte, 1 = bas.
 */
data class CardLine(val text: String, val position: Float)

/** Ce que la lecture d'une image a donné. */
sealed interface MatchResult {
    /** Rien d'exploitable : ni numéro ni nom de carte reconnaissable. */
    data object NoClue : MatchResult

    /** Une seule carte correspond au nom et au numéro lus. */
    data class One(val card: CardBrief) : MatchResult

    /**
     * Plusieurs cartes possibles, à départager par l'image : rééditions (même
     * nom, même numéro), ou cartes trouvées par le nom seul ou par le numéro
     * seul. [clue] dit ce qui a été lu, pour l'écran.
     */
    data class Candidates(val cards: List<CardBrief>, val clue: String) : MatchResult

    /** Trop de cartes possibles pour comparer leurs images (nom très courant, par exemple). */
    data class TooMany(val count: Int, val clue: String) : MatchResult

    /** Une extension est choisie, et la carte lue n'en fait pas partie (elle serait reconnue ailleurs). */
    data object NotInSet : MatchResult
}

/**
 * Retrouve une carte d'après le texte lu sur la photo.
 *
 * Principe (décisions du 27/11/2026) :
 * 1. le nom et le numéro : chercher un numéro du type « 136/189 » (numéro de
 *    la carte / nombre de cartes imprimé de l'extension), prendre les cartes
 *    TCGdex de ce numéro, garder celles dont le nom apparaît dans le texte et
 *    dont l'extension compte bien 189 cartes (si on le sait). Une seule carte :
 *    trouvée ; plusieurs (rééditions) : à départager par l'image ;
 * 2. sinon, le nom seul et le numéro seul, réunis : toutes les cartes dont le
 *    nom est écrit sur la carte, plus toutes celles de ce numéro et de ce total.
 *    L'image départagera (voir CardImageHash). C'est ce qui permet de lire les
 *    cartes dont le numéro est trop petit ou trop abîmé (Set de Base, par exemple).
 *
 * Construit une fois à partir de la liste complète des cartes et des
 * extensions (Tcgdex.allCards / allSets) ; ensuite, chaque lecture ne fait
 * que des recherches en mémoire, sans réseau.
 */
class CardMatcher(cards: List<CardBrief>, sets: List<CardSetBrief>) {

    private class Entry(val card: CardBrief, val normalizedName: String, val official: Int?)

    /** Un mot du texte lu : en minuscules et sans accents, avec sa hauteur sur la carte et sa majuscule éventuelle. */
    private class Word(val text: String, val capitalized: Boolean, val position: Float)

    /** Un nom de carte découpé en mots (« m », « mime »), et toutes les cartes qui le portent. */
    private class Name(val words: List<String>, val cards: List<CardBrief>) {
        val joined = words.joinToString("")
    }

    /** Les cartes rangées par numéro (normalisé : « 036 » et « 36 » sont rangés ensemble). */
    private val byNumber: Map<String, List<Entry>>

    /** Les noms de cartes, rangés par leur premier mot (pour la recherche par le nom seul). */
    private val namesByFirstWord: Map<String, List<Name>>

    /** Nom de chaque extension, d'après son identifiant. */
    private val setNames: Map<String, String> = sets.associate { it.id to it.name }

    init {
        val officialBySet = sets.associate { it.id to it.cardCount?.official }
        byNumber = cards.groupBy { normalizeNumber(it.localId) }.mapValues { (_, group) ->
            group.map { card -> Entry(card, normalizeText(card.name), officialBySet[setIdOf(card)]) }
        }
        namesByFirstWord = cards.groupBy { wordsOf(it.name) }
            .filterKeys { words -> words.joinToString("").length >= MIN_NAME_LENGTH }
            .map { (words, group) -> Name(words, group) }
            .groupBy { it.words.first() }
    }

    /** Le nom de l'extension d'une carte (pour départager des cartes), si connu. */
    fun setNameOf(card: CardBrief): String? = setNames[setIdOf(card)]

    /**
     * [lines] : les lignes lues dans le cadre, du haut vers le bas de la carte.
     * [onlyCards] : si non null, seules ces cartes sont cherchées (les cartes de
     * l'extension choisie dans l'onglet Scanner, décision du 28/11/2026) ; une
     * carte qui serait reconnue parmi toutes les autres donne alors
     * [MatchResult.NotInSet].
     */
    fun match(lines: List<CardLine>, onlyCards: Set<String>? = null): MatchResult {
        if (onlyCards == null) return matchAmong(lines) { true }
        val inSet = matchAmong(lines) { it.id in onlyCards }
        if (inSet != MatchResult.NoClue) return inSet
        return if (matchAmong(lines) { true } == MatchResult.NoClue) MatchResult.NoClue else MatchResult.NotInSet
    }

    /** La recherche elle-même, limitée aux cartes pour lesquelles [keep] répond oui. */
    private fun matchAmong(lines: List<CardLine>, keep: (CardBrief) -> Boolean): MatchResult {
        val text = lines.joinToString("\n") { it.text }
        val numbers = NUMBER_REGEX.findAll(text).toList()

        // 1. Le nom et le numéro.
        val normalizedText = normalizeText(text)
        val byBoth = linkedMapOf<String, CardBrief>()
        for (number in numbers) {
            val (local, total) = number.destructured
            val totalNumber = total.toIntOrNull()
            byNumber[normalizeNumber(local)].orEmpty()
                .filter { it.normalizedName.length >= MIN_NAME_LENGTH && normalizedText.contains(it.normalizedName) }
                // Total imprimé : vérifié seulement s'il est connu des deux côtés.
                .filter { totalNumber == null || it.official == null || it.official == totalNumber }
                .filter { keep(it.card) }
                .forEach { byBoth[it.card.id] = it.card }
        }
        if (byBoth.size == 1) return MatchResult.One(byBoth.values.first())
        if (byBoth.size > 1) return MatchResult.Candidates(byBoth.values.toList(), "Nom et numéro lus")

        // 2. Le nom seul et le numéro seul, réunis.
        val found = linkedMapOf<String, CardBrief>()
        val clues = mutableListOf<String>()
        // Chaque nom trouvé, avec celles de ses cartes qui sont cherchées.
        val names = namesIn(lines).map { name -> name.cards.filter(keep) }.filter { it.isNotEmpty() }
        if (names.isNotEmpty()) {
            names.forEach { cards -> cards.forEach { found[it.id] = it } }
            clues += "nom lu : " + names.joinToString(", ") { it.first().name }
        }
        for (number in numbers) {
            val (local, total) = number.destructured
            val totalNumber = total.toIntOrNull() ?: continue
            // Ici, le total doit être connu et correspondre : sans le nom, c'est le
            // seul moyen de ne pas proposer toutes les cartes n° 8 de toutes les extensions.
            val sameNumber = byNumber[normalizeNumber(local)].orEmpty()
                .filter { it.official == totalNumber && keep(it.card) }
            if (sameNumber.isNotEmpty()) {
                sameNumber.forEach { found[it.card.id] = it.card }
                clues += "numéro lu : " + number.value.replace(" ", "")
            }
        }
        val clue = clues.joinToString(" ; ").replaceFirstChar { it.uppercase() }
        return when {
            found.isEmpty() -> MatchResult.NoClue
            found.size > MAX_CANDIDATES -> MatchResult.TooMany(found.size, clue)
            else -> MatchResult.Candidates(found.values.toList(), clue)
        }
    }

    /**
     * Les noms de cartes écrits dans le texte lu, mot pour mot (« Mewtwo »,
     * « M. Mime »).
     * - Le nom doit commencer par une majuscule, comme les noms de Pokémon :
     *   « carte » dans « Défaussez 1 carte Énergie » n'est pas la carte nommée
     *   « Carte » (constaté en test le 27/11/2026).
     * - Un nom contenu dans un autre nom trouvé est écarté (« Mew » dans « Mewtwo »).
     * - Si certains noms sont dans le haut de la carte, là où le nom de la carte
     *   est imprimé, on ne garde qu'eux : un nom plus bas vient souvent d'un texte
     *   d'attaque. Sinon (nom du haut mal lu, comme « Mew.WO »), on les garde tous.
     */
    private fun namesIn(lines: List<CardLine>): List<Name> {
        // Les mots du texte, chacun avec la hauteur de sa ligne sur la carte.
        val words = lines.flatMap { line ->
            wordsWithCase(line.text).map { (text, capitalized) -> Word(text, capitalized, line.position) }
        }
        // Chaque nom trouvé, avec la plus haute place où il apparaît.
        val found = linkedMapOf<Name, Float>()
        for (start in words.indices) {
            val first = words[start]
            if (!first.capitalized) continue
            for (name in namesByFirstWord[first.text].orEmpty()) {
                val fits = start + name.words.size <= words.size &&
                    name.words.indices.all { words[start + it].text == name.words[it] }
                if (fits) found[name] = minOf(found[name] ?: first.position, first.position)
            }
        }
        val distinct = found.keys.filter { name ->
            found.keys.none { other -> other.joined.length > name.joined.length && other.joined.contains(name.joined) }
        }
        val inTitle = distinct.filter { found.getValue(it) <= TITLE_ZONE }
        return inTitle.ifEmpty { distinct }
    }

    private companion object {
        /**
         * « 136/189 », « 136 / 189 », « TG05/TG30 »… : jusqu'à 4 lettres puis
         * 1 à 3 chiffres, de part et d'autre de la barre. Les promos sans
         * barre (« SWSH045 ») ne sont pas reconnues pour l'instant.
         */
        val NUMBER_REGEX = Regex("""(?<![A-Za-z0-9])([A-Za-z]{0,4}\d{1,3})\s*/\s*([A-Za-z]{0,4}\d{1,3})(?![0-9])""")

        /** Les noms plus courts (2 lettres) se retrouveraient partout par hasard. */
        const val MIN_NAME_LENGTH = 3

        /** Au-delà, on ne télécharge pas les images pour comparer (à revoir après des tests). */
        const val MAX_CANDIDATES = 40

        /** Le haut de la carte, où le nom est imprimé : le premier quart (à revoir après des tests). */
        const val TITLE_ZONE = 0.25f

        /** Minuscules, sans accents, seulement lettres et chiffres : « Dracaufeu-ex » → « dracaufeuex ». */
        fun normalizeText(value: String): String =
            withoutAccents(value).filter { it in 'a'..'z' || it in '0'..'9' }

        /** Les mots, en minuscules et sans accents : « M. Mime » → « m », « mime ». */
        fun wordsOf(value: String): List<String> =
            withoutAccents(value).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }

        /**
         * Les mots, comme [wordsOf], chacun avec « commence par une majuscule
         * (ou un chiffre) ? » : « Mewtwo attaque » → (« mewtwo », oui), (« attaque », non).
         */
        fun wordsWithCase(value: String): List<Pair<String, Boolean>> =
            accentsRemoved(value).split(Regex("""[^A-Za-z0-9]+"""))
                .filter { it.isNotEmpty() }
                .map { it.lowercase() to (it.first().isUpperCase() || it.first().isDigit()) }

        fun withoutAccents(value: String): String = accentsRemoved(value).lowercase()

        /** Sans accents, majuscules gardées : « Évoli » → « Evoli ». */
        fun accentsRemoved(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

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
