package com.bxtracker.app.data

import androidx.annotation.StringRes
import com.bxtracker.app.R

/** Rules and calculations shared by the screens. Everything is derived from stored parts, combos and points. */

data class Record(val wins: Int = 0, val losses: Int = 0) {
    val total: Int get() = wins + losses
    val rate: Float get() = if (total == 0) 0f else wins.toFloat() / total
    val percent: Int get() = (rate * 100).toInt()
}

data class Score(val me: Int, val opponent: Int)

fun Combo.isCx(): Boolean = shape() != ComboShape.BASIC

fun Combo.shape(): ComboShape = when {
    metalBladeId != null || overBladeId != null -> ComboShape.CX_METAL
    bladeId == null && (lockChipId != null || mainBladeId != null || assistBladeId != null) -> ComboShape.CX
    else -> ComboShape.BASIC
}

fun Combo.partIds(): List<Long> =
    listOfNotNull(bladeId, lockChipId, mainBladeId, metalBladeId, overBladeId, assistBladeId, ratchetId, bitId)

/** The part this combo uses in [kind]'s slot, if any. */
fun Combo.partFor(kind: PartKind): Long? = when (kind) {
    PartKind.BLADE -> bladeId
    PartKind.LOCK_CHIP -> lockChipId
    PartKind.MAIN_BLADE -> mainBladeId
    PartKind.METAL_BLADE -> metalBladeId
    PartKind.OVER_BLADE -> overBladeId
    PartKind.ASSIST_BLADE -> assistBladeId
    PartKind.RATCHET -> ratchetId
    PartKind.BIT -> bitId
}

fun Combo.withPartFor(kind: PartKind, id: Long?): Combo = when (kind) {
    PartKind.BLADE -> copy(bladeId = id)
    PartKind.LOCK_CHIP -> copy(lockChipId = id)
    PartKind.MAIN_BLADE -> copy(mainBladeId = id)
    PartKind.METAL_BLADE -> copy(metalBladeId = id)
    PartKind.OVER_BLADE -> copy(overBladeId = id)
    PartKind.ASSIST_BLADE -> copy(assistBladeId = id)
    PartKind.RATCHET -> copy(ratchetId = id)
    PartKind.BIT -> copy(bitId = id)
}

/** Every slot of a shape, in build order. */
fun ComboShape.slots(): List<PartKind> = when (this) {
    ComboShape.BASIC -> listOf(PartKind.BLADE, PartKind.RATCHET, PartKind.BIT)
    ComboShape.CX -> listOf(PartKind.LOCK_CHIP, PartKind.MAIN_BLADE, PartKind.ASSIST_BLADE, PartKind.RATCHET, PartKind.BIT)
    ComboShape.CX_METAL -> listOf(PartKind.LOCK_CHIP, PartKind.METAL_BLADE, PartKind.OVER_BLADE, PartKind.ASSIST_BLADE, PartKind.RATCHET, PartKind.BIT)
}

/**
 * The slots this combo still needs: the shape's slots minus the ratchet when the blade has one built in,
 * and minus the bit when the ratchet has one built in (Turbo, Operate).
 */
fun neededSlots(shape: ComboShape, combo: Combo, parts: Map<Long, Part>): List<PartKind> {
    val blade = combo.bladeId?.let { parts[it] }
    val ratchet = combo.ratchetId?.let { parts[it] }
    return shape.slots().filter { kind ->
        when (kind) {
            PartKind.RATCHET -> !(shape == ComboShape.BASIC && blade?.builtInRatchet == true)
            PartKind.BIT -> ratchet?.builtInBit != true
            else -> true
        }
    }
}

/** True when every needed slot has a part. */
fun Combo.isComplete(shape: ComboShape, parts: Map<Long, Part>): Boolean =
    neededSlots(shape, this, parts).all { partFor(it) != null }

/** Clears parts that don't belong to [shape] or to a slot that's no longer needed. */
fun Combo.normalized(shape: ComboShape, parts: Map<Long, Part>): Combo {
    val needed = neededSlots(shape, this, parts).toSet()
    return PartKind.entries.fold(this) { c, kind -> if (kind in needed) c else c.withPartFor(kind, null) }
}

private fun Part.shortCode(): String = code.ifBlank { name }
private fun Part.letter(): String = code.ifBlank { name.take(1) }

/**
 * Standard Beyblade X naming. Basic "Phoenix Wing 9-60GF", built-in ratchet "Bullet Griffon H",
 * CX "Dran Brave S6-60V", CX with Turbo "Pegasus Blast ATr", CX Metal "Bahamut Blitz BK1-50I".
 * A needed part that isn't picked yet shows as "?". A bit without a code uses its name after a space.
 */
fun comboName(c: Combo, parts: Map<Long, Part>, shape: ComboShape = c.shape()): String {
    val needed = neededSlots(shape, c, parts)
    fun part(kind: PartKind) = c.partFor(kind)?.let { parts[it] }
    fun nameOf(kind: PartKind) = part(kind)?.name ?: "?"
    fun letterOf(kind: PartKind) = if (kind in needed) part(kind)?.letter() ?: "?" else ""
    val ratchet = if (PartKind.RATCHET in needed) part(PartKind.RATCHET)?.shortCode() ?: "?" else ""
    val bitPart = part(PartKind.BIT)
    val bit = when {
        PartKind.BIT !in needed -> ""
        bitPart == null -> "?"
        bitPart.code.isNotBlank() -> bitPart.code
        else -> " " + bitPart.name
    }
    val head = when (shape) {
        ComboShape.BASIC -> nameOf(PartKind.BLADE)
        ComboShape.CX -> "${nameOf(PartKind.LOCK_CHIP)} ${nameOf(PartKind.MAIN_BLADE)}"
        ComboShape.CX_METAL -> "${nameOf(PartKind.LOCK_CHIP)} ${nameOf(PartKind.METAL_BLADE)}"
    }
    val letters = when (shape) {
        ComboShape.BASIC -> ""
        ComboShape.CX -> letterOf(PartKind.ASSIST_BLADE)
        ComboShape.CX_METAL -> letterOf(PartKind.OVER_BLADE) + letterOf(PartKind.ASSIST_BLADE)
    }
    return "$head ${(letters + ratchet + bit).trimStart()}".trim()
}

fun Deck.comboIds(): List<Long?> = listOf(combo1Id, combo2Id, combo3Id)

fun Deck.withSlot(slot: Int, comboId: Long?): Deck = when (slot) {
    0 -> copy(combo1Id = comboId)
    1 -> copy(combo2Id = comboId)
    else -> copy(combo3Id = comboId)
}



/** Battle record per combo, counting each battle (not each match). */
fun comboRecords(points: List<BattlePoint>): Map<Long, Record> =
    points.filter { it.myComboId != null && it.scorer != Scorer.DRAW }
        .groupBy { it.myComboId!! }
        .mapValues { (_, list) -> Record(list.count { it.scorer == Scorer.ME }, list.count { it.scorer == Scorer.OPPONENT }) }

fun matchScore(matchId: Long, points: List<BattlePoint>): Score {
    val list = points.filter { it.matchId == matchId }
    return Score(
        me = list.filter { it.scorer == Scorer.ME }.sumOf { it.points },
        opponent = list.filter { it.scorer == Scorer.OPPONENT }.sumOf { it.points },
    )
}

/** true = won, false = lost, null = still playing. */
fun matchOutcome(match: Match, score: Score): Boolean? = when {
    score.me >= match.pointsToWin -> true
    score.opponent >= match.pointsToWin -> false
    else -> null
}

fun usageCounts(combos: List<Combo>): Map<Long, Int> =
    combos.flatMap { it.partIds() }.groupingBy { it }.eachCount()

internal fun partKey(p: Part) = "${p.kind}:${p.name.trim().lowercase()}"

data class DeckConflict(val part: Part, val slots: List<Int>)

/** A 3on3 deck can't use the same part twice. Returns every repeated part and the slots (1 to 3) using it. */
fun deckConflicts(slotCombos: List<Combo?>, parts: Map<Long, Part>): List<DeckConflict> {
    val uses = linkedMapOf<String, MutableList<Pair<Part, Int>>>()
    slotCombos.forEachIndexed { index, combo ->
        combo?.partIds()?.forEach { pid ->
            val part = parts[pid] ?: return@forEach
            uses.getOrPut(partKey(part)) { mutableListOf() }.add(part to index + 1)
        }
    }
    return uses.values
        .filter { list -> list.map { it.second }.distinct().size > 1 }
        .map { list -> DeckConflict(list.first().first, list.map { it.second }.distinct()) }
}

/** Parts of [candidate] that already appear in [others]. */
fun sharedParts(candidate: Combo, others: List<Combo>, parts: Map<Long, Part>): List<Part> {
    val taken = others.flatMap { it.partIds() }.mapNotNull { parts[it] }.map(::partKey).toSet()
    return candidate.partIds().mapNotNull { parts[it] }.filter { partKey(it) in taken }
}

/** Points scored by [who], split by finish type. */
fun finishBreakdown(points: List<BattlePoint>, who: Scorer): Map<Finish, Int> =
    points.filter { it.scorer == who && it.finish != null }
        .groupBy { it.finish!! }
        .mapValues { (_, list) -> list.sumOf { it.points } }

/** Battle record (draws ignored) for a set of points. */
fun battleRecord(points: List<BattlePoint>): Record =
    Record(points.count { it.scorer == Scorer.ME }, points.count { it.scorer == Scorer.OPPONENT })

/** Match record: finished matches only. */
fun matchRecord(matches: List<Match>, points: List<BattlePoint>): Record {
    val outcomes = matches.map { matchOutcome(it, matchScore(it.id, points)) }
    return Record(outcomes.count { it == true }, outcomes.count { it == false })
}

/** Time of the last battle each combo played. */
fun lastBattleAt(points: List<BattlePoint>): Map<Long, Long> =
    points.filter { it.myComboId != null }.groupBy { it.myComboId!! }.mapValues { (_, l) -> l.maxOf { it.createdAt } }

// Tournaments

/** Total rounds: Swiss rounds plus the top cut bracket (4 = SF + F, 8 = QF + SF + F, 16 adds a Top 16 round). */
fun Tournament.totalRounds(): Int = swissRounds + topCutRounds(topCut)

fun topCutRounds(topCut: Int): Int = when {
    topCut >= 16 -> 4
    topCut >= 8 -> 3
    topCut >= 4 -> 2
    topCut >= 2 -> 1
    else -> 0
}

/** Round label for the round at [index] (0-based): R1, R2… then T16, QF, SF, F. */
fun Tournament.roundLabel(index: Int): String {
    if (index < swissRounds) return "R${index + 1}"
    val cut = topCutRounds(topCut)
    val fromEnd = totalRounds() - index // 1 = final
    return when {
        index >= totalRounds() || cut == 0 -> "R${index + 1}"
        fromEnd == 1 -> "F"
        fromEnd == 2 -> "SF"
        fromEnd == 3 -> "QF"
        else -> "T16"
    }
}

fun Tournament.stageFor(index: Int): MatchStage = if (index < swissRounds) MatchStage.SWISS else MatchStage.TOP_CUT

/** 1 -> "1st", 2 -> "2nd", 23 -> "23rd". */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

fun Tournament.placingText(): String? = placement?.let { p ->
    if (playerCount > 0) "${ordinal(p)} of $playerCount" else ordinal(p)
}

fun Tournament.formatText(): String = buildList {
    add(format)
    if (swissRounds > 0) add("$swissRounds Swiss")
    if (topCut > 0) add("Top $topCut")
}.joinToString(" · ")

// Stats

enum class StatsPeriod(@StringRes val label: Int) { SEASON(R.string.period_season), TOURNAMENTS(R.string.period_tournaments), ALL(R.string.period_all) }

/** Matches that count for [period]. */
fun matchesFor(period: StatsPeriod, matches: List<Match>, seasonStart: Long?): List<Match> = when (period) {
    StatsPeriod.SEASON -> if (seasonStart == null) matches else matches.filter { it.startedAt >= utcDayToLocal(seasonStart) }
    StatsPeriod.TOURNAMENTS -> matches.filter { it.tournamentId != null }
    StatsPeriod.ALL -> matches
}

/** Date pickers give UTC midnight; this is the same calendar day's local midnight. */
fun utcDayToLocal(millis: Long): Long = millis - java.util.TimeZone.getDefault().getOffset(millis)

/** My win rate per opponent type, from the type logged on each battle. */
fun recordVsType(points: List<BattlePoint>): Map<BeyType, Record> =
    points.filter { it.opponentType != null && it.scorer != Scorer.DRAW }
        .groupBy { it.opponentType!! }
        .mapValues { (_, list) -> battleRecord(list) }

// Decks

/** Deck mix: how many combos of each type. */
fun deckMix(slotCombos: List<Combo?>): Map<BeyType, Int> =
    slotCombos.filterNotNull().groupingBy { it.type }.eachCount()

/** A swap that fixes a conflict: replace [replace] with [with] in the combo in [slot] (1 to 3). */
data class DeckFix(
    val slot: Int,
    val original: Combo,
    val replace: Part,
    val with: Part,
    /** An existing combo that already matches the result, or null if a new one would be created. */
    val existing: Combo?,
    val freeCopies: Int,
) {
    fun result(): Combo = existing ?: original.withPart(replace.id, with.id).copy(id = 0, favorite = false, notes = "", createdAt = System.currentTimeMillis())
}

fun Combo.withPart(oldId: Long, newId: Long): Combo =
    PartKind.entries.fold(this) { c, kind -> if (c.partFor(kind) == oldId) c.withPartFor(kind, newId) else c }

/**
 * For every conflict, finds owned parts of the same kind that aren't used by the other deck slots and could
 * replace the repeated part in the last conflicting slot. Best first: parts with free copies, then variants
 * you already built, then the variant's win rate.
 */
fun deckFixes(
    slotCombos: List<Combo?>,
    parts: List<Part>,
    combos: List<Combo>,
    records: Map<Long, Record>,
    perConflict: Int = 2,
): List<DeckFix> {
    val partMap = parts.associateBy { it.id }
    val usage = usageCounts(combos)
    return deckConflicts(slotCombos, partMap).flatMap { conflict ->
        val slot = conflict.slots.last()
        val original = slotCombos[slot - 1] ?: return@flatMap emptyList()
        val repeated = original.partIds().mapNotNull { partMap[it] }.firstOrNull { partKey(it) == partKey(conflict.part) }
            ?: return@flatMap emptyList()
        val others = slotCombos.filterIndexed { i, c -> i != slot - 1 && c != null }.filterNotNull()
        val taken = others.flatMap { it.partIds() }.mapNotNull { partMap[it] }.map(::partKey).toSet()
        val ownKeys = original.partIds().filter { it != repeated.id }.mapNotNull { partMap[it] }.map(::partKey).toSet()
        parts.asSequence()
            .filter { it.kind == repeated.kind && it.copies > 0 && partKey(it) != partKey(repeated) }
            // Same structure only: a Turbo-style ratchet can't replace a plain one (it would drop the bit).
            .filter { it.builtInRatchet == repeated.builtInRatchet && it.builtInBit == repeated.builtInBit }
            .filter { partKey(it) !in taken && partKey(it) !in ownKeys }
            .map { candidate ->
                val variant = original.withPart(repeated.id, candidate.id)
                val existing = combos.firstOrNull { it.id != original.id && it.partIds().sorted() == variant.partIds().sorted() }
                DeckFix(slot, original, repeated, candidate, existing, (candidate.copies - (usage[candidate.id] ?: 0)).coerceAtLeast(0))
            }
            .sortedWith(
                compareByDescending<DeckFix> { it.freeCopies > 0 }
                    .thenByDescending { it.existing != null }
                    .thenByDescending { f -> f.existing?.let { records[it.id]?.rate } ?: -1f }
                    .thenByDescending { it.freeCopies }
            )
            .take(perConflict)
            .toList()
    }
}

/** Parts used by the deck that you own 0 copies of. */
fun missingParts(slotCombos: List<Combo?>, parts: Map<Long, Part>): List<Part> =
    slotCombos.filterNotNull().flatMap { it.partIds() }.distinct().mapNotNull { parts[it] }.filter { it.copies <= 0 }
