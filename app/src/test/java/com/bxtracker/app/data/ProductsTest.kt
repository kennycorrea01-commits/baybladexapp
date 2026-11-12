package com.bxtracker.app.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Product matching (search, ownership, "came with"), suggestion ranking, and the manual rows form. */
class ProductsTest {
    private fun pp(kind: PartKind, name: String, code: String = "") = ProductPart(kind, name, code)

    private val dranSword = Product("BX-01", "Starter Dran Sword 3-60F", "2023-07-15", "STARTER", listOf(
        StockBuild("Dran Sword 3-60F", ComboShape.BASIC, BeyType.ATTACK,
            listOf(pp(PartKind.BLADE, "Dran Sword"), pp(PartKind.RATCHET, "3-60"), pp(PartKind.BIT, "Flat", "F"))),
    ))
    private val hellsScythe = Product("BX-02", "Starter Hells Scythe 4-60T", "2023-07-15", "STARTER", listOf(
        StockBuild("Hells Scythe 4-60T", ComboShape.BASIC, BeyType.BALANCE,
            listOf(pp(PartKind.BLADE, "Hells Scythe"), pp(PartKind.RATCHET, "4-60"), pp(PartKind.BIT, "Taper", "T"))),
    ))
    private val shatter = Product("UX-99", "Booster Shatter Horus 9-65GB", "2025-01-01", "BOOSTER", listOf(
        StockBuild("Shatter Horus 9-65GB", ComboShape.BASIC, BeyType.ATTACK,
            listOf(pp(PartKind.BLADE, "Shatter Horus"), pp(PartKind.RATCHET, "9-65"), pp(PartKind.BIT, "Gear Ball", "GB"))),
    ))
    private val randomBooster = Product("BX-14", "Random Booster Vol. 1", "2023-10-01", "RANDOM_BOOSTER", listOf(
        StockBuild("Sharkedge 3-60LF", ComboShape.BASIC, BeyType.ATTACK,
            listOf(pp(PartKind.BLADE, "Sharkedge"), pp(PartKind.RATCHET, "3-60"), pp(PartKind.BIT, "Low Flat", "LF"))),
        StockBuild("Dran Sword 4-60T", ComboShape.BASIC, BeyType.ATTACK,
            listOf(pp(PartKind.BLADE, "Dran Sword"), pp(PartKind.RATCHET, "4-60"), pp(PartKind.BIT, "Taper", "T"))),
    ))
    private val products = listOf(dranSword, hellsScythe, shatter, randomBooster)

    private var nextId = 1L
    private fun part(kind: PartKind, name: String, copies: Int = 1, code: String = "") =
        Part(id = nextId++, kind = kind, name = name, code = if (kind == PartKind.RATCHET) name else code, copies = copies)

    // Search

    @Test fun searchByCodeNameAndCombo() {
        assertEquals("BX-01", Products.search(products, "BX-01").first().product.code)
        assertEquals("BX-01", Products.search(products, "bx01").first().product.code)
        assertEquals("BX-02", Products.search(products, "hells scythe").first().product.code)
        // Stock combo name, ignoring spaces and dashes.
        assertEquals("UX-99", Products.search(products, "shatter horus 965gb").first().product.code)
        // Words in any order.
        assertEquals("UX-99", Products.search(products, "horus shatter").first().product.code)
        assertTrue(Products.search(products, "cursed mummy").isEmpty())
        assertTrue(Products.search(products, "  ").isEmpty())
    }

    @Test fun pastedWikiLinkFindsTheProduct() {
        assertEquals("Shatter Horus 9-65GB", Products.queryText("https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB"))
        assertEquals("UX-99", Products.search(products, "https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB").first().product.code)
    }

    @Test fun randomBoosterMatchesTheRightBuild() {
        val hits = Products.search(products, "Dran Sword 4-60T")
        assertEquals("BX-14", hits.first().product.code)
        assertEquals("Dran Sword 4-60T", hits.first().build?.combo)
        // Searching the product itself returns it without picking a build.
        val byName = Products.search(products, "Random Booster Vol. 1").first()
        assertEquals("BX-14", byName.product.code)
        assertNull(byName.build)
    }

    @Test fun hasbroNamesFindTakaraTomyProducts() {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"BLADE":[{"name":"Mummy Curse","hasbro":"Cursed Mummy"},{"name":"Horus Shatter","hasbro":"Shatter Horus"}],
               "PRODUCTS":[
                 {"code":"BX-90","name":"Booster Mummy Curse 7-55W","builds":[{"combo":"Mummy Curse 7-55W","parts":[{"kind":"BLADE","name":"Mummy Curse"},{"kind":"RATCHET","name":"7-55"},{"kind":"BIT","name":"Wedge","code":"W"}]}]},
                 {"code":"BX-91","name":"Booster Horus Shatter 3-85MO","builds":[{"combo":"Horus Shatter 3-85MO","parts":[{"kind":"BLADE","name":"Horus Shatter"},{"kind":"RATCHET","name":"3-85"},{"kind":"BIT","name":"Metal Orb","code":"MO"}]}]}
               ]}"""
        ).jsonObject
        val ps = Products.parse(root)
        assertEquals(listOf("Cursed Mummy 7-55W"), ps.first().builds.single().aliases)
        // Dave's searches use Hasbro names.
        assertEquals("BX-90", Products.search(ps, "Cursed Mummy").first().product.code)
        assertEquals("BX-90", Products.search(ps, "cursed mummy 7-55w").first().product.code)
        // A Hasbro wiki link whose stock ratchet/bit differ still finds the Bey by name.
        assertEquals("BX-91", Products.search(ps, "https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB").first().product.code)
        // The Takara Tomy name still works, and an unrelated name still finds nothing.
        assertEquals("BX-90", Products.search(ps, "Mummy Curse").first().product.code)
        assertTrue(Products.search(ps, "Phoenix Wing").isEmpty())
        // Name suggestions in Add part accept the Hasbro name and give back the Takara Tomy entry.
        assertEquals("Mummy Curse", Catalog.suggest(Catalog.parse(root), PartKind.BLADE, "Cursed").single().name)
    }

    /** Guards the bundled products list: it parses, and every stock part points at a catalog entry of its kind. */
    @Test fun bundledProductsMatchTheCatalog() {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(java.io.File("src/main/assets/catalog.json").readText()).jsonObject
        val catalog = Catalog.parse(root)
        val ps = Products.parse(root)
        assertTrue("expected the bundled products list", ps.size >= 100)
        val missing = ps.flatMap { p ->
            p.builds.flatMap { b ->
                b.parts.filter { sp -> catalog[sp.kind].orEmpty().none { it.name.equals(sp.name, ignoreCase = true) } }
                    .map { sp -> "${p.code} ${b.combo}: ${sp.kind} ${sp.name}" }
            }
        }
        assertEquals(emptyList<String>(), missing)
        assertEquals("labels must be unique, screens find products by label", ps.size, ps.map { it.label }.toSet().size)
        assertEquals("BX-01", Products.search(ps, "BX-01").first().product.code)
        // Dran Sword "comes in" BX-01, not one of its metal coat or later reissues.
        assertEquals("BX-01", Products.productsWith(PartKind.BLADE, "Dran Sword", ps).first().code)
        // Dave's two searches, on the real data: Hasbro "Curse Mummy" (he typed "Cursed") is Mummy Curse;
        // the Hasbro-only Shatter Horus 9-65GB finds Takara Tomy's Horus Shatter builds by name.
        val mummy = Products.search(ps, "Cursed Mummy")
        assertTrue(mummy.isNotEmpty())
        assertTrue(mummy.first().let { m -> (m.build ?: m.product.builds.first()).parts.any { it.name == "Mummy Curse" } })
        val horus = Products.search(ps, "https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB")
        assertTrue(horus.isNotEmpty())
        assertTrue(horus.all { m -> m.product.builds.any { b -> b.parts.any { it.name == "Horus Shatter" } } })
    }

    // Ownership and "came with"

    @Test fun ownershipMatchesByKindAndNameIgnoringCase() {
        val mine = listOf(part(PartKind.BLADE, "dran sword"), part(PartKind.RATCHET, "3-60", copies = 0), part(PartKind.BIT, "Taper", code = "T"))
        val o = Products.ownership(dranSword.builds.single(), mine)
        assertEquals(listOf(true, false, false), o.map { it.owns })
        assertEquals("dran sword", o[0].owned?.name)
        // Zero copies counts as not owned, but the row is still found (so adding gives it +1).
        assertEquals("3-60", o[1].owned?.name)
        assertNull(o[2].owned)
    }

    @Test fun cameWithListsEveryProductOldestFirst() {
        val came = Products.cameWith(part(PartKind.BLADE, "Dran Sword"), products)
        assertEquals(listOf("BX-01", "BX-14"), came.map { it.first.code })
        assertEquals("Dran Sword 4-60T", came[1].second.combo)
        // Same name, different kind: no match.
        assertTrue(Products.cameWith(part(PartKind.MAIN_BLADE, "Dran Sword"), products).isEmpty())
        assertEquals(listOf("BX-01", "BX-14"), Products.productsWith(PartKind.RATCHET, "3-60", products).map { it.code })
    }

    // Suggestions

    @Test fun canBuildIncludesStockBuildsAndMarksBusyParts() {
        val ds = part(PartKind.BLADE, "Dran Sword"); val r360 = part(PartKind.RATCHET, "3-60"); val flat = part(PartKind.BIT, "Flat", code = "F")
        val hs = part(PartKind.BLADE, "Hells Scythe"); val r460 = part(PartKind.RATCHET, "4-60"); val taper = part(PartKind.BIT, "Taper", code = "T")
        val parts = listOf(ds, r360, flat, hs, r460, taper)
        // My own combo Hells Scythe 3-60F, used in a deck, battled 3–1.
        val mine = Combo(id = 10, bladeId = hs.id, ratchetId = r360.id, bitId = flat.id, type = BeyType.BALANCE)
        val deck = Deck(id = 1, name = "Tournament deck", combo1Id = 10)
        val points = listOf(Scorer.ME, Scorer.ME, Scorer.ME, Scorer.OPPONENT).map { BattlePoint(matchId = 1, myComboId = 10, scorer = it, points = 1) }

        val s = Suggestions.canBuild(products, parts, listOf(mine), listOf(deck), points)
        // My battled combo first, then the stock builds I own every part of.
        assertEquals(listOf("Hells Scythe 3-60F", "Dran Sword 3-60F", "Hells Scythe 4-60T", "Dran Sword 4-60T"), s.map { it.name })
        assertEquals(Record(3, 1), s[0].record)
        // 3-60 and Flat are in the deck (through Hells Scythe 3-60F): Dran Sword 3-60F is still suggested, both marked.
        val dran = s.first { it.name == "Dran Sword 3-60F" }
        assertEquals(listOf("3-60" to listOf("Tournament deck"), "Flat" to listOf("Tournament deck")), dran.busy.map { it.part.name to it.decks })
        // Dran Sword 4-60T shares no part with the deck.
        assertTrue(s.first { it.name == "Dran Sword 4-60T" }.busy.isEmpty())
        assertEquals("BX-01", dran.product?.code)
        // Not owning a part (0 copies) removes the build.
        val noFlat = parts.map { if (it.id == flat.id) it.copy(copies = 0) else it }
        assertFalse(Suggestions.canBuild(products, noFlat, emptyList(), emptyList(), emptyList()).any { it.name == "Dran Sword 3-60F" })
    }

    @Test fun savedStockBuildShowsOnceAsMyCombo() {
        val ds = part(PartKind.BLADE, "Dran Sword"); val r360 = part(PartKind.RATCHET, "3-60"); val flat = part(PartKind.BIT, "Flat", code = "F")
        val saved = Combo(id = 5, bladeId = ds.id, ratchetId = r360.id, bitId = flat.id, type = BeyType.ATTACK)
        val s = Suggestions.canBuild(products, listOf(ds, r360, flat), listOf(saved), emptyList(), emptyList())
        assertEquals(1, s.size)
        assertEquals(5L, s.single().combo?.id)
        assertEquals("BX-01", s.single().product?.code)
    }

    @Test fun bestWithRanksByMyRecordsThenFallsBackToStock() {
        val ds = part(PartKind.BLADE, "Dran Sword")
        val r360 = part(PartKind.RATCHET, "3-60"); val r460 = part(PartKind.RATCHET, "4-60")
        val flat = part(PartKind.BIT, "Flat", code = "F"); val taper = part(PartKind.BIT, "Taper", code = "T")
        val parts = listOf(ds, r360, r460, flat, taper)
        val a = Combo(id = 1, bladeId = ds.id, ratchetId = r360.id, bitId = flat.id, type = BeyType.ATTACK) // 1–3
        val b = Combo(id = 2, bladeId = ds.id, ratchetId = r460.id, bitId = flat.id, type = BeyType.ATTACK) // 4–0
        fun pts(combo: Long, w: Int, l: Int) = List(w) { BattlePoint(matchId = 1, myComboId = combo, scorer = Scorer.ME) } +
            List(l) { BattlePoint(matchId = 1, myComboId = combo, scorer = Scorer.OPPONENT) }
        val (ratchets, bits) = Suggestions.bestWith(ds, products, parts, listOf(a, b), emptyList(), pts(1, 1, 3) + pts(2, 4, 0))
        assertEquals(listOf("4-60", "3-60"), ratchets.map { it.part.name })
        assertEquals(Record(4, 0), ratchets[0].record)
        assertEquals(listOf("Flat"), bits.map { it.part.name })
        assertEquals(Record(5, 3), bits[0].record)

        // No records: the stock pairings for Dran Sword (BX-01: 3-60 + Flat, BX-14: 4-60 + Taper).
        val (r2, b2) = Suggestions.bestWith(ds, products, parts, emptyList(), emptyList(), emptyList())
        assertEquals(listOf("3-60", "4-60"), r2.map { it.part.name })
        assertEquals(listOf("BX-01", "BX-14"), r2.map { it.stockProduct?.code })
        assertEquals(listOf("Flat", "Taper"), b2.map { it.part.name })
        assertNull(r2[0].record)
    }

    @Test fun missingOneFindsNearlyCompleteStockBuilds() {
        val ds = part(PartKind.BLADE, "Dran Sword"); val r360 = part(PartKind.RATCHET, "3-60")
        val hs = part(PartKind.BLADE, "Hells Scythe"); val r460 = part(PartKind.RATCHET, "4-60"); val taper = part(PartKind.BIT, "Taper", code = "T")
        val horusOnly = part(PartKind.BLADE, "Shatter Horus")
        val parts = listOf(ds, r360, hs, r460, taper, horusOnly)
        val m = Suggestions.missingOne(products, parts, emptyList(), emptyList())
        // Dran Sword 3-60F needs Flat; Sharkedge needs 2 parts (left out); Shatter Horus needs 2 (left out).
        val names = m.map { it.build.combo to it.need.name }
        assertTrue("Dran Sword 3-60F" to "Flat" in names)
        assertFalse(names.any { it.first.startsWith("Sharkedge") || it.first.startsWith("Shatter") })
        // Hells Scythe 4-60T and Dran Sword 4-60T are complete, so they aren't "missing one".
        assertFalse(names.any { it.first == "Hells Scythe 4-60T" || it.first == "Dran Sword 4-60T" })
        val flat = m.first { it.need.name == "Flat" }
        assertEquals("BX-01", flat.product.code)
        assertEquals(2, flat.owned.size)
    }

    @Test fun missingOneRanksByMyRecordsWithTheOwnedParts() {
        // Two builds missing one part each, same number of owned parts; my record with Dran Sword is better.
        val ds = part(PartKind.BLADE, "Dran Sword"); val r360 = part(PartKind.RATCHET, "3-60")
        val hs = part(PartKind.BLADE, "Hells Scythe"); val r460 = part(PartKind.RATCHET, "4-60")
        val other = part(PartKind.BIT, "Ball", code = "B")
        val parts = listOf(ds, r360, hs, r460, other)
        val good = Combo(id = 1, bladeId = ds.id, ratchetId = r360.id, bitId = other.id, type = BeyType.ATTACK)
        val bad = Combo(id = 2, bladeId = hs.id, ratchetId = r460.id, bitId = other.id, type = BeyType.BALANCE)
        val points = List(3) { BattlePoint(matchId = 1, myComboId = 1, scorer = Scorer.ME) } +
            List(3) { BattlePoint(matchId = 1, myComboId = 2, scorer = Scorer.OPPONENT) }
        val m = Suggestions.missingOne(products, parts, listOf(good, bad), points)
        assertEquals("Dran Sword 3-60F", m.first().build.combo)
        assertTrue(m.indexOfFirst { it.build.combo == "Hells Scythe 4-60T" } > 0)
    }

    @Test fun regularReleasesWinOverReissuesAndLimitedOnes() {
        // The same Bey in a gold metal coat (BX-00, listed first and "older") and in a later entry set (BX-22).
        fun dsBuild() = StockBuild("Dran Sword 3-60F", ComboShape.BASIC, BeyType.ATTACK,
            listOf(pp(PartKind.BLADE, "Dran Sword"), pp(PartKind.RATCHET, "3-60"), pp(PartKind.BIT, "Flat", "F")))
        val gold = Product("BX-00", "Dran Sword 3-60F (Metal Coat: Gold)", "2023-01-01", "BOOSTER", listOf(dsBuild()))
        val spiderMan = Product("BX-00", "Spider-Man and Venom Multipack Set", "2023-01-01", "SET", listOf(
            StockBuild("Spider-Man 3-60F", ComboShape.BASIC, BeyType.ATTACK,
                listOf(pp(PartKind.BLADE, "Spider-Man"), pp(PartKind.RATCHET, "3-60"), pp(PartKind.BIT, "Flat", "F")))))
        val entry = Product("BX-22", "Dran Sword 3-60F Entry Package", "2024-01-01", "SET", listOf(dsBuild()))
        val all = listOf(gold, spiderMan, entry) + products
        assertTrue(gold.limited); assertFalse(dranSword.limited); assertFalse(Product("BX-100", "x").limited)

        // My saved Dran Sword 3-60F is "stock build of BX-01".
        val ds = part(PartKind.BLADE, "Dran Sword"); val r360 = part(PartKind.RATCHET, "3-60"); val flat = part(PartKind.BIT, "Flat", code = "F")
        val saved = Combo(id = 5, bladeId = ds.id, ratchetId = r360.id, bitId = flat.id, type = BeyType.ATTACK)
        assertEquals("BX-01", Suggestions.canBuild(all, listOf(ds, r360, flat), listOf(saved), emptyList(), emptyList()).single().product?.code)
        assertEquals("BX-01", Suggestions.canBuild(all, listOf(ds, r360, flat), emptyList(), emptyList(), emptyList()).first().product?.code)

        // Missing one part: regular products before the BX-00 collab with the same 3-60F.
        val m = Suggestions.missingOne(all, listOf(r360, flat), emptyList(), emptyList())
        assertEquals("BX-01", m.first().product.code)
        assertEquals("Spider-Man 3-60F", m.last().build.combo)

        // Came with and search: BX-01 first, and every BX-00 product is listed separately.
        assertEquals(listOf("BX-01", "BX-14", "BX-22", "BX-00"), Products.cameWith(ds, all).map { it.first.code })
        assertEquals("BX-01", Products.search(all, "Dran Sword 3-60F").first().product.code)
        // Screens pass the label, which is unique where the code isn't.
        assertEquals(spiderMan, Products.byKey(all, spiderMan.label))
        assertEquals(gold, Products.byKey(all, gold.label))
        assertEquals(dranSword, Products.byKey(all, "bx01"))
    }

    // Manual rows (bug: one Name field was filling every row)

    @Test fun manualRowsKeepTheirOwnFields() {
        var rows = ManualRows()
        rows = rows.add(PartKind.RATCHET).add(PartKind.BIT)
        val (a, b, c) = rows.rows.map { it.id }
        rows = rows.update(a) { it.copy(name = "Dran Sword", copies = 2) }
            .update(b) { it.copy(name = "3-60") }
            .update(c) { it.copy(name = "Flat", code = "F", copies = 3) }
        assertEquals(listOf("Dran Sword", "3-60", "Flat"), rows.rows.map { it.name })
        assertEquals(listOf("", "", "F"), rows.rows.map { it.code })
        assertEquals(listOf(2, 1, 3), rows.rows.map { it.copies })
        assertEquals(3, rows.rows.map { it.id }.toSet().size)
        // Removing the middle row keeps the others' ids and values; a new row gets a fresh id.
        rows = rows.remove(b).add()
        assertEquals(listOf("Dran Sword", "Flat", ""), rows.rows.map { it.name })
        assertFalse(rows.rows.last().id in setOf(a, b, c))
        assertEquals(2, rows.ready.size)
    }
}
