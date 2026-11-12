package com.bxtracker.app.data

/**
 * Combo suggestions for the builder's Suggest sheet (screen 23). Only two sources are used: stock builds from
 * the bundled product catalog, and my own battle records. No meta or tier lists.
 */

/** A part of a suggestion that's already in one of my decks. It's still suggested, just marked. */
data class BusyPart(val part: Part, val decks: List<String>)

/** "You can build these": I own every part (copies > 0). */
data class BuildSuggestion(
    val name: String,
    val shape: ComboShape,
    val type: BeyType?,
    val parts: List<Part>,
    /** My saved combo with these parts, if I've built it. */
    val combo: Combo?,
    /** The stock build these parts come from, if it is one. */
    val product: Product?,
    val build: StockBuild?,
    /** My battle record with the saved combo (null if never battled). */
    val record: Record?,
    val busy: List<BusyPart>,
)

/** "Best with <blade>": a ratchet or bit I own, ranked by my records with that blade. */
data class PairingSuggestion(
    val part: Part,
    /** My battles with the picked blade and this part. Null when this comes from a stock pairing. */
    val record: Record?,
    /** The product whose stock build pairs the blade with this part (the fallback when I have no records). */
    val stockProduct: Product?,
    val busy: BusyPart?,
)

/** "Missing one part": a stock build where I own everything but [need]. */
data class MissingOneSuggestion(
    val product: Product,
    val build: StockBuild,
    val need: ProductPart,
    val owned: List<Part>,
    /** My battle record across saved combos that use the owned parts (null if none battled). */
    val record: Record?,
)

object Suggestions {

    private fun key(kind: PartKind, name: String) = "$kind:${name.trim().lowercase()}"
    private fun key(p: Part) = key(p.kind, p.name)

    /** Every (product, build), in [Products.order], so where several products have the same Bey the first one wins. */
    private fun stockBuilds(products: List<Product>) =
        products.sortedWith(Products.order).flatMap { p -> p.builds.map { p to it } }

    /** Owned parts (copies > 0) by kind + name. */
    private fun ownedByKey(parts: List<Part>) = parts.filter { it.copies > 0 }.associateBy(::key)

    /** Which decks each part (by kind + name) is in. */
    fun busyParts(parts: List<Part>, combos: List<Combo>, decks: List<Deck>): Map<String, List<String>> {
        val partMap = parts.associateBy { it.id }
        val comboMap = combos.associateBy { it.id }
        val out = linkedMapOf<String, MutableList<String>>()
        decks.forEach { d ->
            d.comboIds().mapNotNull { id -> id?.let { comboMap[it] } }
                .flatMap { it.partIds() }.mapNotNull { partMap[it] }.map(::key).distinct()
                .forEach { k -> out.getOrPut(k) { mutableListOf() }.apply { if (d.name !in this) add(d.name) } }
        }
        return out
    }

    private fun busyFor(parts: List<Part>, busy: Map<String, List<String>>) =
        parts.mapNotNull { p -> busy[key(p)]?.let { BusyPart(p, it) } }

    /** Builds a combo row (not saved) from a stock build's parts, if I own them all. */
    private fun comboFrom(build: StockBuild, owned: List<Part>): Combo? {
        val byKind = owned.associateBy { it.kind }
        val ratchet = byKind[PartKind.RATCHET]
        val c = Combo(
            bladeId = byKind[PartKind.BLADE]?.id, lockChipId = byKind[PartKind.LOCK_CHIP]?.id,
            mainBladeId = byKind[PartKind.MAIN_BLADE]?.id, metalBladeId = byKind[PartKind.METAL_BLADE]?.id,
            overBladeId = byKind[PartKind.OVER_BLADE]?.id, assistBladeId = byKind[PartKind.ASSIST_BLADE]?.id,
            ratchetId = ratchet?.id, bitId = byKind[PartKind.BIT]?.id, type = build.type ?: BeyType.BALANCE,
        )
        return c.takeIf { owned.size == build.parts.size }
    }

    /**
     * Everything I can build right now: my saved combos where I own every part, and stock builds where I own
     * every part. A stock build I already saved shows once, as my combo. Best record first, then stock builds,
     * then combos I haven't battled with.
     */
    fun canBuild(
        products: List<Product>,
        parts: List<Part>,
        combos: List<Combo>,
        decks: List<Deck>,
        points: List<BattlePoint>,
    ): List<BuildSuggestion> {
        val owned = ownedByKey(parts)
        val partMap = parts.associateBy { it.id }
        val records = comboRecords(points)
        val busy = busyParts(parts, combos, decks)

        val mine = combos.mapNotNull { c ->
            val ps = c.partIds().mapNotNull { partMap[it] }
            if (ps.size != c.partIds().size || ps.any { it.copies <= 0 }) return@mapNotNull null
            BuildSuggestion(comboName(c, partMap), c.shape(), c.type, ps, c, null, null, records[c.id]?.takeIf { it.total > 0 }, busyFor(ps, busy))
        }
        val mineKeys = mine.map { s -> s.parts.map(::key).sorted() }.toSet()

        val stock = stockBuilds(products).mapNotNull { (p, b) ->
            val ps = b.parts.map { owned[key(it.kind, it.name)] ?: return@mapNotNull null }
            val k = ps.map(::key).sorted()
            if (k in mineKeys) return@mapNotNull null
            BuildSuggestion(b.combo, b.shape, b.type, ps, comboFrom(b, ps), p, b, null, busyFor(ps, busy))
        }.distinctBy { it.parts.map(::key).sorted() }

        // Stock builds I saved get their product attached, so the sheet can say "stock build of BX-01".
        val stockByKey = stockBuilds(products)
            .groupBy { (_, b) -> b.parts.map { key(it.kind, it.name) }.sorted() }
            .mapValues { it.value.first() }
        val mineWithStock = mine.map { s ->
            stockByKey[s.parts.map(::key).sorted()]?.let { (p, b) -> s.copy(product = p, build = b) } ?: s
        }

        val battled = mineWithStock.filter { it.record != null }
            .sortedWith(compareByDescending<BuildSuggestion> { it.record!!.rate }.thenByDescending { it.record!!.total })
        val unbattled = mineWithStock.filter { it.record == null }
        // stock is already in product order.
        return battled + stock + unbattled.sortedBy { it.name }
    }

    /**
     * Ratchets and bits to pair with a picked blade (or, for CX, the main or metal blade), ranked by my own
     * battle records with that blade. With no records, falls back to the stock pairings from the catalog.
     */
    fun bestWith(
        head: Part,
        products: List<Product>,
        parts: List<Part>,
        combos: List<Combo>,
        decks: List<Deck>,
        points: List<BattlePoint>,
    ): Pair<List<PairingSuggestion>, List<PairingSuggestion>> {
        val owned = parts.filter { it.copies > 0 }
        val partMap = parts.associateBy { it.id }
        val records = comboRecords(points)
        val busy = busyParts(parts, combos, decks)
        val headKey = key(head)

        fun rank(kind: PartKind): List<PairingSuggestion> {
            val withHead = combos.filter { c -> c.partIds().mapNotNull { partMap[it] }.any { key(it) == headKey } }
            val perPart = linkedMapOf<String, Record>()
            withHead.forEach { c ->
                val r = records[c.id] ?: return@forEach
                val p = c.partFor(kind)?.let { partMap[it] } ?: return@forEach
                val k = key(p)
                val prev = perPart[k] ?: Record()
                perPart[k] = Record(prev.wins + r.wins, prev.losses + r.losses)
            }
            val fromRecords = owned.filter { it.kind == kind && (perPart[key(it)]?.total ?: 0) > 0 }
                .map { PairingSuggestion(it, perPart.getValue(key(it)), null, busy[key(it)]?.let { d -> BusyPart(it, d) }) }
                .sortedWith(compareByDescending<PairingSuggestion> { it.record!!.rate }.thenByDescending { it.record!!.total })
            if (fromRecords.isNotEmpty()) return fromRecords

            // Fallback: what the blade came with in its stock builds, if I own that part.
            return stockBuilds(products)
                .filter { (_, b) -> b.parts.any { key(it.kind, it.name) == headKey } }
                .mapNotNull { (p, b) ->
                    val sp = b.parts.firstOrNull { it.kind == kind } ?: return@mapNotNull null
                    val mine = owned.firstOrNull { key(it) == key(sp.kind, sp.name) } ?: return@mapNotNull null
                    PairingSuggestion(mine, null, p, busy[key(mine)]?.let { d -> BusyPart(mine, d) })
                }
                .distinctBy { key(it.part) }
        }
        return rank(PartKind.RATCHET) to rank(PartKind.BIT)
    }

    /**
     * Stock builds where I own every part but one: "worth getting". Sorted by how many of the other parts I
     * own, then by my record with combos that use those parts. Builds with fewer than two owned parts are left
     * out (owning a single ratchet isn't a near-complete build).
     */
    fun missingOne(
        products: List<Product>,
        parts: List<Part>,
        combos: List<Combo>,
        points: List<BattlePoint>,
    ): List<MissingOneSuggestion> {
        val owned = ownedByKey(parts)
        val partMap = parts.associateBy { it.id }
        val records = comboRecords(points)
        fun recordWith(ps: List<Part>): Record? {
            val keys = ps.map(::key).toSet()
            var w = 0; var l = 0
            combos.filter { c -> c.partIds().mapNotNull { partMap[it] }.any { key(it) in keys } }
                .forEach { c -> records[c.id]?.let { w += it.wins; l += it.losses } }
            return Record(w, l).takeIf { it.total > 0 }
        }
        return stockBuilds(products).mapNotNull { (p, b) ->
            val missing = b.parts.filter { owned[key(it.kind, it.name)] == null }
            if (missing.size != 1) return@mapNotNull null
            val have = b.parts.mapNotNull { owned[key(it.kind, it.name)] }
            if (have.size < 2) return@mapNotNull null
            MissingOneSuggestion(p, b, missing.single(), have, recordWith(have))
        }
            .distinctBy { it.build.combo.lowercase() }
            .sortedWith(
                compareByDescending<MissingOneSuggestion> { it.owned.size }
                    .thenByDescending { it.record?.rate ?: -1f }
                    .thenByDescending { it.record?.total ?: 0 }
                    .thenBy(Products.order) { it.product }
            )
    }
}
