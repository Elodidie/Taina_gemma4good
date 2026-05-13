package com.example.gemma

/**
 * Minimal local species dictionary for the Amazonian forest demo.
 * Keys are lowercase common-name fragments; values are accepted scientific names.
 * Used as a fallback when Gemma reports no scientific name — returns "no data"
 * rather than hallucinating.
 */
object SpeciesLookup {

    private val dictionary: Map<String, String> = mapOf(
        // ── Demo trio ──────────────────────────────────────────────────────────
        "capybara"      to "Hydrochoerus hydrochaeris",
        "toucan"        to "Ramphastos toco",
        "frog"          to "Dendropsophus minutus",

        // ── Extra Amazonian species ────────────────────────────────────────────
        "macaw"         to "Ara ararauna",
        "blue macaw"    to "Anodorhynchus hyacinthinus",
        "jaguar"        to "Panthera onca",
        "anaconda"      to "Eunectes murinus",
        "piranha"       to "Pygocentrus nattereri",
        "sloth"         to "Bradypus tridactylus",
        "tapir"         to "Tapirus terrestris",
        "caiman"        to "Caiman crocodilus",
        "poison dart frog" to "Dendrobates tinctorius",
        "tree frog"     to "Phyllomedusa bicolor",
        "harpy eagle"   to "Harpia harpyja",
        "giant otter"   to "Pteronura brasiliensis",
        "peccary"       to "Tayassu pecari",
        "howler monkey" to "Alouatta seniculus",
        "spider monkey" to "Ateles chamek",
    )

    /**
     * Returns the scientific name for [commonName] using a case-insensitive
     * substring search across the dictionary keys.
     * Returns null when no entry matches — callers should then store "no data".
     */
    fun lookup(commonName: String): String? {
        val query = commonName.trim().lowercase()
        // Exact key match first
        dictionary[query]?.let { return it }
        // Substring: query contains a key OR a key contains the query
        return dictionary.entries
            .firstOrNull { (key, _) -> query.contains(key) || key.contains(query) }
            ?.value
    }
}
