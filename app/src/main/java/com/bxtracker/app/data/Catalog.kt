package com.bxtracker.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One known part from the bundled catalog: picking it in Add part fills in the code and type. */
@Serializable
data class CatalogEntry(
    val name: String,
    val code: String = "",
    val type: BeyType? = null,
    val leftSpin: Boolean = false,
    /** Blades: the ratchet is built in (combos skip the ratchet). */
    val builtInRatchet: Boolean = false,
    /** Ratchets: the bit is built in, like Turbo (combos skip the bit). */
    val builtInBit: Boolean = false,
    /** Hasbro's name for the same part, when it differs ("Cursed Mummy" for Mummy Curse). Search accepts it; the Takara Tomy name is what gets saved. */
    val hasbro: String = "",
)

/**
 * Reads app/src/main/assets/catalog.json. The file has one array per part kind
 * ("BLADE", "RATCHET", "BIT", "LOCK_CHIP", "MAIN_BLADE", "ASSIST_BLADE"). Other keys are ignored.
 * A missing or broken file just means no suggestions.
 */
object Catalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): Map<PartKind, List<CatalogEntry>> = runCatching {
        val root = json.parseToJsonElement(context.assets.open("catalog.json").bufferedReader().use { it.readText() }).jsonObject
        parse(root)
    }.getOrDefault(emptyMap())

    internal fun parse(root: JsonObject): Map<PartKind, List<CatalogEntry>> =
        PartKind.entries.associateWith { kind ->
            (root[kind.name] as? JsonArray).orEmpty().mapNotNull { el ->
                runCatching { json.decodeFromJsonElement(CatalogEntry.serializer(), el) }.getOrNull()
            }.filter { it.name.isNotBlank() }
        }

    /** Catalog entries of [kind] whose name or code starts with (or contains) [query], best matches first. */
    fun suggest(catalog: Map<PartKind, List<CatalogEntry>>, kind: PartKind, query: String, limit: Int = 6): List<CatalogEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val list = catalog[kind].orEmpty()
        val starts = list.filter { it.name.startsWith(q, true) || it.code.equals(q, true) || it.hasbro.startsWith(q, true) }
        val contains = list.filter { it !in starts && (it.name.contains(q, true) || it.hasbro.contains(q, true)) }
        return (starts + contains).filterNot { it.name.equals(q, true) && it.code.isBlank() }.take(limit)
    }
}
