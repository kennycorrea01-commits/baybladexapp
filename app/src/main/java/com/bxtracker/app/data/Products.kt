package com.bxtracker.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLDecoder

/** One stock part of a product, pointing at a catalog entry by kind + name. */
@Serializable
data class ProductPart(val kind: PartKind, val name: String, val code: String = "")

/** A Bey as it comes in the box. Sets and random boosters have several. */
@Serializable
data class StockBuild(
    val combo: String,
    val shape: ComboShape = ComboShape.BASIC,
    val type: BeyType? = null,
    val parts: List<ProductPart> = emptyList(),
    /** Hasbro's name for the same stock combo, when the catalog has one. */
    val hasbro: String = "",
    /** Other names search accepts: [hasbro] and the combo with each part's Hasbro name swapped in. Filled by [Products.parse]. */
    @Transient val aliases: List<String> = emptyList(),
)

/** A Takara Tomy product (BX-01, UX-…, CX-…) from the bundled catalog. */
@Serializable
data class Product(
    val code: String,
    val name: String,
    val released: String? = null,
    val kind: String = "OTHER",
    val builds: List<StockBuild> = emptyList(),
) {
    /** "BX-01 Starter Dran Sword 3-60F", stored in Part.source when parts are added from it. */
    val label: String get() = "$code $name"

    /** BX-00, UX-00, CX-00: limited, event, prize and collab releases (metal coats, multipacks). */
    val limited: Boolean get() = code.substringAfter('-').trimStart('0').isEmpty()
}

/** A search hit: the product, and which build matched (null when the product itself matched). */
data class ProductMatch(val product: Product, val build: StockBuild?)

/** One stock part and the matching part in my collection, if any. */
data class PartOwnership(val stock: ProductPart, val owned: Part?) {
    val owns: Boolean get() = owned != null && owned.copies > 0
}

object Products {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Which product to name when several have the same Bey: regular releases before limited ones (BX-00), then
     * oldest first. So Dran Sword 3-60F "comes in BX-01", not in a later reissue or a gold metal coat.
     */
    val order: Comparator<Product> =
        compareBy<Product> { it.limited }.thenBy { it.released ?: "9999" }.thenBy { it.code }

    fun load(context: Context): List<Product> = runCatching {
        val root = json.parseToJsonElement(context.assets.open("catalog.json").bufferedReader().use { it.readText() }).jsonObject
        parse(root)
    }.getOrDefault(emptyList())

    internal fun parse(root: JsonObject): List<Product> {
        // Hasbro names of parts, by kind, so "Cursed Mummy 7-55W" finds Takara Tomy's "Mummy Curse 7-55W".
        val hasbro: Map<PartKind, Map<String, String>> = Catalog.parse(root).mapValues { (_, list) ->
            list.filter { it.hasbro.isNotBlank() }.associate { it.name.lowercase() to it.hasbro }
        }
        return (root["PRODUCTS"] as? JsonArray).orEmpty().mapNotNull { el ->
            runCatching { json.decodeFromJsonElement(Product.serializer(), el) }.getOrNull()
        }.filter { it.code.isNotBlank() && it.builds.isNotEmpty() }
            .map { p -> p.copy(builds = p.builds.map { b -> b.copy(aliases = aliasesFor(b, hasbro)) }) }
    }

    private fun aliasesFor(b: StockBuild, hasbro: Map<PartKind, Map<String, String>>): List<String> {
        var swapped = b.combo
        b.parts.forEach { p ->
            val alias = hasbro[p.kind]?.get(p.name.lowercase()) ?: return@forEach
            swapped = swapped.replace(p.name, alias, ignoreCase = true)
        }
        return listOf(b.hasbro, swapped).filter { it.isNotBlank() && !it.equals(b.combo, ignoreCase = true) }.distinct()
    }

    /** Letters and digits only, lowercase: "BX-01" and "bx01" compare equal, so do "Shatter_Horus 9-65GB" and "shatterhorus965gb". */
    internal fun squash(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Turns what the user typed or pasted into a search text. A wiki link like
     * https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB becomes "Shatter Horus 9-65GB".
     */
    fun queryText(input: String): String {
        val t = input.trim()
        val page = if ("/wiki/" in t) t.substringAfterLast("/wiki/").substringBefore('?').substringBefore('#') else t
        return runCatching { URLDecoder.decode(page, "UTF-8") }.getOrDefault(page).replace('_', ' ').trim()
    }

    /**
     * Products matching a product code, product name or stock combo name, best first. Matching ignores case,
     * spaces and dashes; every word of the query has to appear somewhere.
     */
    fun search(products: List<Product>, input: String, limit: Int = 20): List<ProductMatch> {
        val q = queryText(input)
        val hits = searchText(products, q, limit)
        if (hits.isNotEmpty()) return hits
        // Nothing for the full text (a Hasbro release whose stock ratchet/bit differ, like "Shatter Horus 9-65GB"):
        // try again with just the name words, which finds the Takara Tomy products with that Bey.
        val nameOnly = q.split(Regex("\\s+")).filter { w -> w.none { it.isDigit() } }.joinToString(" ")
        return if (nameOnly.isNotBlank() && nameOnly != q) searchText(products, nameOnly, limit) else emptyList()
    }

    /**
     * True when [word] is in [text], allowing a small ending difference for longer words, so "cursed" matches
     * "curse" (Hasbro's Curse Mummy) and "beys" matches "bey". Both are already squashed.
     */
    internal fun has(text: String, word: String): Boolean =
        word in text || (word.length >= 5 && word.dropLast(1) in text) || (word.length >= 6 && word.dropLast(2) in text)

    private fun searchText(products: List<Product>, q: String, limit: Int): List<ProductMatch> {
        val sq = squash(q)
        if (sq.isEmpty()) return emptyList()
        val words = q.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }.map(::squash)
        data class Hit(val match: ProductMatch, val score: Int)
        val hits = products.flatMap { p ->
            val code = squash(p.code)
            val name = squash(p.name)
            val productScore = when {
                code == sq -> 100
                name == sq -> 90
                code.startsWith(sq) -> 70
                name.startsWith(sq) -> 60
                words.all { has(name, it) || has(code, it) } -> 40
                else -> 0
            }
            val buildHits = p.builds.mapNotNull { b ->
                // The Takara Tomy combo name counts a little more than a Hasbro alias.
                val s = (listOf(b.combo) + b.aliases).mapIndexed { i, name ->
                    val combo = squash(name)
                    val base = when {
                        combo == sq -> 95
                        combo.startsWith(sq) -> 65
                        words.all { has(combo, it) } -> 45
                        else -> 0
                    }
                    if (base > 0 && i > 0) base - 2 else base
                }.max()
                if (s > 0) Hit(ProductMatch(p, b), s) else null
            }
            when {
                // A single-Bey product is one result whichever way it matched.
                p.builds.size == 1 && (productScore > 0 || buildHits.isNotEmpty()) ->
                    listOf(Hit(ProductMatch(p, p.builds.first()), maxOf(productScore, buildHits.maxOfOrNull { it.score } ?: 0)))
                // Sets and random boosters: the product itself (pick a build later), plus any build whose combo matched.
                else -> listOfNotNull(Hit(ProductMatch(p, null), productScore).takeIf { productScore > 0 }) + buildHits
            }
        }
        return hits.sortedWith(compareByDescending<Hit> { it.score }.thenBy(order) { it.match.product })
            .map { it.match }.distinct().take(limit)
    }

    /**
     * The product for a [Product.label] (what screens pass around: codes alone aren't unique, 40+ limited
     * releases are all "BX-00"). A bare code also works and gives the first product with it.
     */
    fun byKey(products: List<Product>, key: String): Product? =
        products.firstOrNull { it.label == key } ?: products.firstOrNull { squash(it.code) == squash(key) }

    private fun same(stock: ProductPart, part: Part) =
        stock.kind == part.kind && stock.name.trim().equals(part.name.trim(), ignoreCase = true)

    /** Each stock part of [build] with the part I own of the same kind and name (if any). */
    fun ownership(build: StockBuild, parts: List<Part>): List<PartOwnership> =
        build.parts.map { s -> PartOwnership(s, parts.firstOrNull { same(s, it) }) }

    /** Every (product, build) that came with [part], matched by kind and name, in [order]. */
    fun cameWith(part: Part, products: List<Product>): List<Pair<Product, StockBuild>> =
        products.sortedWith(order)
            .flatMap { p -> p.builds.filter { b -> b.parts.any { same(it, part) } }.map { p to it } }
            .distinctBy { it.first.code to it.first.name }

    /** The products that include a part of this kind and name (for "comes in BX-xx"), in [order]. */
    fun productsWith(kind: PartKind, name: String, products: List<Product>): List<Product> =
        products.filter { p -> p.builds.any { b -> b.parts.any { it.kind == kind && it.name.equals(name, ignoreCase = true) } } }
            .sortedWith(order)
}

/**
 * The rows of the manual "Add parts" form (screen 20). Every row has its own stable [ManualRow.id], and every
 * edit goes through [update] with that id, so typing in one row never changes another.
 */
@Serializable
data class ManualRow(
    val id: Long,
    val kind: PartKind = PartKind.BLADE,
    val name: String = "",
    val code: String = "",
    val type: BeyType? = null,
    val copies: Int = 1,
) {
    val ready: Boolean get() = name.isNotBlank()
}

/** Immutable, so a Compose `mutableStateOf(ManualRows())` recomposes on every change. */
@Serializable
data class ManualRows(val rows: List<ManualRow> = listOf(ManualRow(1)), val nextId: Long = 2) {
    /** Adds an empty row of the same kind as the last one. */
    fun add(kind: PartKind = rows.lastOrNull()?.kind ?: PartKind.BLADE): ManualRows =
        copy(rows = rows + ManualRow(nextId, kind), nextId = nextId + 1)

    /** Changes only the row with this id. */
    fun update(id: Long, change: (ManualRow) -> ManualRow): ManualRows =
        copy(rows = rows.map { if (it.id == id) change(it).copy(id = id) else it })

    fun remove(id: Long): ManualRows {
        val left = rows.filter { it.id != id }
        return if (left.isEmpty()) copy(rows = listOf(ManualRow(nextId)), nextId = nextId + 1) else copy(rows = left)
    }

    /** Rows with a name, ready to save. */
    val ready: List<ManualRow> get() = rows.filter { it.ready }
}
