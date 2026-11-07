package com.bxtracker.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private var nextId = 1L
    private fun part(kind: PartKind, name: String, code: String = "", copies: Int = 1, type: BeyType? = null) =
        Part(id = nextId++, kind = kind, name = name, code = if (kind == PartKind.RATCHET) name else code, copies = copies, type = type)

    @Test fun basicComboName() {
        val blade = part(PartKind.BLADE, "Phoenix Wing")
        val ratchet = part(PartKind.RATCHET, "9-60")
        val bit = part(PartKind.BIT, "Gear Flat", "GF")
        val parts = listOf(blade, ratchet, bit).associateBy { it.id }
        val c = Combo(bladeId = blade.id, ratchetId = ratchet.id, bitId = bit.id, type = BeyType.ATTACK)
        assertEquals("Phoenix Wing 9-60GF", comboName(c, parts))
    }

    @Test fun cxComboNameAndBitWithoutCode() {
        val lock = part(PartKind.LOCK_CHIP, "Dran")
        val main = part(PartKind.MAIN_BLADE, "Brave")
        val assist = part(PartKind.ASSIST_BLADE, "Slash", "S")
        val ratchet = part(PartKind.RATCHET, "6-60")
        val bit = part(PartKind.BIT, "Vortex", "V")
        val noCode = part(PartKind.BIT, "Mystery")
        val parts = listOf(lock, main, assist, ratchet, bit, noCode).associateBy { it.id }
        val c = Combo(lockChipId = lock.id, mainBladeId = main.id, assistBladeId = assist.id, ratchetId = ratchet.id, bitId = bit.id, type = BeyType.ATTACK)
        assertEquals("Dran Brave S6-60V", comboName(c, parts))
        assertEquals("Dran Brave S6-60 Mystery", comboName(c.copy(bitId = noCode.id), parts))
    }

    @Test fun builtInRatchetAndTurboNames() {
        val griffon = part(PartKind.BLADE, "Bullet Griffon").copy(builtInRatchet = true)
        val hexa = part(PartKind.BIT, "Hexa", "H")
        val r960 = part(PartKind.RATCHET, "9-60")
        val lock = part(PartKind.LOCK_CHIP, "Pegasus")
        val main = part(PartKind.MAIN_BLADE, "Blast")
        val assist = part(PartKind.ASSIST_BLADE, "Assault", "A")
        val turbo = part(PartKind.RATCHET, "Turbo").copy(code = "Tr", builtInBit = true)
        val parts = listOf(griffon, hexa, r960, lock, main, assist, turbo).associateBy { it.id }

        val g = Combo(bladeId = griffon.id, bitId = hexa.id, type = BeyType.ATTACK)
        assertEquals("Bullet Griffon H", comboName(g, parts))
        assertTrue(g.isComplete(ComboShape.BASIC, parts))
        assertEquals(listOf(PartKind.BLADE, PartKind.BIT), neededSlots(ComboShape.BASIC, g, parts))
        // A ratchet picked earlier is dropped on save once the blade has one built in.
        assertNull(g.copy(ratchetId = r960.id).normalized(ComboShape.BASIC, parts).ratchetId)

        val p = Combo(lockChipId = lock.id, mainBladeId = main.id, assistBladeId = assist.id, ratchetId = turbo.id, type = BeyType.ATTACK)
        assertEquals("Pegasus Blast ATr", comboName(p, parts))
        assertTrue(p.isComplete(ComboShape.CX, parts))
        assertEquals(ComboShape.CX, p.shape())
    }

    @Test fun cxMetalNameAndShape() {
        val lock = part(PartKind.LOCK_CHIP, "Bahamut")
        val metal = part(PartKind.METAL_BLADE, "Blitz")
        val over = part(PartKind.OVER_BLADE, "Break", "B")
        val assist = part(PartKind.ASSIST_BLADE, "Knuckle", "K")
        val ratchet = part(PartKind.RATCHET, "1-50")
        val bit = part(PartKind.BIT, "Ignition", "I")
        val parts = listOf(lock, metal, over, assist, ratchet, bit).associateBy { it.id }
        val c = Combo(lockChipId = lock.id, metalBladeId = metal.id, overBladeId = over.id, assistBladeId = assist.id,
            ratchetId = ratchet.id, bitId = bit.id, type = BeyType.ATTACK)
        assertEquals(ComboShape.CX_METAL, c.shape())
        assertEquals("Bahamut Blitz BK1-50I", comboName(c, parts))
        assertEquals(6, c.partIds().size)
        // Preview while building: missing parts show as "?".
        assertEquals("Bahamut Blitz ?K1-50I", comboName(c.copy(overBladeId = null), parts, ComboShape.CX_METAL))
        assertEquals(false, c.copy(overBladeId = null).isComplete(ComboShape.CX_METAL, parts))
    }

    @Test fun roundLabels() {
        val swissOnly = Tournament(name = "a", swissRounds = 3, topCut = 0)
        assertEquals(listOf("R1", "R2", "R3", "R4"), (0..3).map { swissOnly.roundLabel(it) })
        val top8 = Tournament(name = "b", swissRounds = 4, topCut = 8)
        assertEquals(listOf("R1", "R2", "R3", "R4", "QF", "SF", "F"), (0..6).map { top8.roundLabel(it) })
        assertEquals(7, top8.totalRounds())
        assertEquals(MatchStage.TOP_CUT, top8.stageFor(4))
        val top4 = Tournament(name = "c", swissRounds = 2, topCut = 4)
        assertEquals(listOf("R1", "R2", "SF", "F"), (0..3).map { top4.roundLabel(it) })
        val top16 = Tournament(name = "d", swissRounds = 5, topCut = 16)
        assertEquals(listOf("T16", "QF", "SF", "F"), (5..8).map { top16.roundLabel(it) })
    }

    @Test fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101).map(::ordinal))
        assertEquals("3rd of 24", Tournament(name = "x", placement = 3, playerCount = 24).placingText())
        assertNull(Tournament(name = "x").placingText())
    }

    @Test fun conflictsAndSuggestedFix() {
        val wizard = part(PartKind.BLADE, "Wizard Rod")
        val phoenix = part(PartKind.BLADE, "Phoenix Wing")
        val r460 = part(PartKind.RATCHET, "4-60")
        val r360 = part(PartKind.RATCHET, "3-60", copies = 2)
        val hexa = part(PartKind.BIT, "Hexa", "H")
        val flat = part(PartKind.BIT, "Flat", "F")
        val parts = listOf(wizard, phoenix, r460, r360, hexa, flat)
        val map = parts.associateBy { it.id }
        val c1 = Combo(id = 1, bladeId = phoenix.id, ratchetId = r460.id, bitId = flat.id, type = BeyType.ATTACK)
        val c2 = Combo(id = 2, bladeId = wizard.id, ratchetId = r460.id, bitId = hexa.id, type = BeyType.STAMINA)
        val slots = listOf(c1, c2, null)

        val conflicts = deckConflicts(slots, map)
        assertEquals(1, conflicts.size)
        assertEquals("4-60", conflicts[0].part.name)
        assertEquals(listOf(1, 2), conflicts[0].slots)

        val fixes = deckFixes(slots, parts, listOf(c1, c2), emptyMap())
        assertEquals(1, fixes.size)
        val fix = fixes[0]
        assertEquals(2, fix.slot)
        assertEquals("3-60", fix.with.name)
        assertEquals(2, fix.freeCopies)
        assertNull(fix.existing)
        assertEquals("Wizard Rod 3-60H", comboName(fix.result(), map))
        assertTrue(deckConflicts(listOf(c1, fix.result(), null), map).isEmpty())

        // An already-built variant is reused.
        val built = Combo(id = 3, bladeId = wizard.id, ratchetId = r360.id, bitId = hexa.id, type = BeyType.STAMINA)
        val again = deckFixes(slots, parts, listOf(c1, c2, built), emptyMap())
        assertEquals(3L, again[0].existing?.id)
    }

    @Test fun missingPartsAndMix() {
        val blade = part(PartKind.BLADE, "Phoenix Wing", copies = 0)
        val ratchet = part(PartKind.RATCHET, "9-60")
        val bit = part(PartKind.BIT, "Gear Flat", "GF")
        val map = listOf(blade, ratchet, bit).associateBy { it.id }
        val c = Combo(id = 1, bladeId = blade.id, ratchetId = ratchet.id, bitId = bit.id, type = BeyType.ATTACK)
        assertEquals(listOf(blade), missingParts(listOf(c, null, null), map))
        assertEquals(mapOf(BeyType.ATTACK to 1), deckMix(listOf(c, null, null)))
    }

    @Test fun recordsAndOpponentType() {
        val pts = listOf(
            BattlePoint(matchId = 1, myComboId = 5, scorer = Scorer.ME, finish = Finish.XTREME, points = 3, opponentType = BeyType.ATTACK),
            BattlePoint(matchId = 1, myComboId = 5, scorer = Scorer.OPPONENT, finish = Finish.SPIN, points = 1, opponentType = BeyType.ATTACK),
            BattlePoint(matchId = 1, myComboId = 5, scorer = Scorer.DRAW),
            BattlePoint(matchId = 1, myComboId = 6, scorer = Scorer.ME, finish = Finish.OVER, points = 2, opponentType = BeyType.STAMINA),
        )
        assertEquals(Record(1, 1), comboRecords(pts)[5L])
        assertEquals(Record(1, 1), recordVsType(pts)[BeyType.ATTACK])
        assertEquals(Score(5, 1), matchScore(1, pts))
        assertEquals(true, matchOutcome(Match(id = 1, opponent = "x", pointsToWin = 4), matchScore(1, pts)))
    }

    /** Guards hand edits to the bundled catalog: it must parse and every kind must have entries with names. */
    @Test fun bundledCatalogParses() {
        val file = java.io.File("src/main/assets/catalog.json")
        val catalog = Catalog.parse(Json.parseToJsonElement(file.readText()).jsonObject)
        PartKind.entries.forEach { kind -> assertTrue("$kind is empty", catalog[kind].orEmpty().isNotEmpty()) }
        catalog.values.flatten().forEach { assertTrue(it.name.isNotBlank()) }
        catalog.forEach { (kind, list) ->
            assertEquals("$kind has duplicates", list.size, list.map { it.name.lowercase() }.distinct().size)
        }
        assertEquals("GF", Catalog.suggest(catalog, PartKind.BIT, "Gear Flat").firstOrNull()?.code)
    }

    @Test fun catalogParsingAndSuggest() {
        val root = Json.parseToJsonElement(
            """{"_readme":"x","BIT":[{"name":"Gear Flat","code":"GF"},{"name":"Flat","code":"F"},{"name":""}],"BLADE":[{"name":"Wing","type":"ATTACK"}]}"""
        ).jsonObject
        val catalog = Catalog.parse(root)
        assertEquals(2, catalog[PartKind.BIT]?.size)
        assertEquals(BeyType.ATTACK, catalog[PartKind.BLADE]?.first()?.type)
        assertEquals(listOf("Flat", "Gear Flat"), Catalog.suggest(catalog, PartKind.BIT, "fl").map { it.name })
        assertEquals("Gear Flat", Catalog.suggest(catalog, PartKind.BIT, "gf").first().name)
    }
}
