package com.bxtracker.app.data

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.random.Random

/** A pairing with its score resolved from manual entry, my linked match, or the organizer's battles. */
data class PairingResult(
    val pairing: Pairing,
    val scoreA: Int,
    val scoreB: Int,
    val status: PairingStatus,
    val winnerId: Long?,
    /** Battles played so far (for "Live · battle N"). */
    val battles: Int = 0,
) {
    val isBye: Boolean get() = pairing.playerBId == null
    val loserId: Long? get() = when (winnerId) {
        null -> null
        pairing.playerAId -> pairing.playerBId
        else -> pairing.playerAId
    }
    fun involves(playerId: Long) = pairing.playerAId == playerId || pairing.playerBId == playerId
    fun opponentOf(playerId: Long): Long? = if (pairing.playerAId == playerId) pairing.playerBId else pairing.playerAId
    fun scoreFor(playerId: Long) = if (pairing.playerAId == playerId) scoreA else scoreB
    fun scoreAgainst(playerId: Long) = if (pairing.playerAId == playerId) scoreB else scoreA
}

/** One row of the standings table. */
data class Standing(
    val player: EventPlayer,
    val rank: Int,
    val wins: Int,
    val losses: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
    /** Opponents' match win %, each floored at 33%, byes excluded. 0..1. */
    val opponentWinPct: Double,
    val byes: Int,
) {
    val diff: Int get() = pointsFor - pointsAgainst
}

/** What the organizer can do next. */
sealed interface NextStep {
    /** Some matches in the current round aren't done yet. */
    data class Waiting(val remaining: Int) : NextStep
    data class PairRound(val round: Int) : NextStep
    data class StartBracket(val size: Int) : NextStep
    /** Pair the next bracket round, labeled like "F" or "SF". */
    data class NextBracketRound(val label: String) : NextStep
    data object Finish : NextStep
    data object Done : NextStep
}

/**
 * Pure tournament rules: Swiss pairing, byes, round robin, brackets, standings, tiebreakers and placings.
 * No Android or database code, so it's covered by plain unit tests.
 */
object TournamentEngine {

    /** ceil(log2(players)), at least 1. */
    fun suggestedSwissRounds(players: Int): Int =
        if (players <= 2) 1 else ceil(ln(players.toDouble()) / ln(2.0) - 1e-9).toInt()

    /** First to [pointsToWin] wins. With typed-in scores a higher score that reaches the target also wins. */
    fun winner(scoreA: Int, scoreB: Int, pointsToWin: Int, aId: Long, bId: Long?): Long? = when {
        bId == null -> aId
        scoreA >= pointsToWin && scoreA > scoreB -> aId
        scoreB >= pointsToWin && scoreB > scoreA -> bId
        else -> null
    }

    /**
     * Resolves a pairing's score. Byes win [Tournament.byeScore]–0. Manual scores win over everything,
     * then my linked match (its BattlePoints), then the organizer's battles.
     */
    fun resolve(
        p: Pairing,
        t: Tournament,
        battles: List<EventBattle>,
        match: Match?,
        matchPoints: List<BattlePoint>,
        meId: Long?,
    ): PairingResult {
        if (p.playerBId == null) return PairingResult(p, t.byeScore, 0, PairingStatus.DONE, p.playerAId)
        val (a, b, count) = when {
            p.manual -> Triple(p.scoreA, p.scoreB, 0)
            match != null -> {
                val mine = matchPoints.filter { it.matchId == match.id }
                val me = mine.filter { it.scorer == Scorer.ME }.sumOf { it.points }
                val them = mine.filter { it.scorer == Scorer.OPPONENT }.sumOf { it.points }
                if (p.playerAId == meId) Triple(me, them, mine.size) else Triple(them, me, mine.size)
            }
            else -> {
                val mine = battles.filter { it.pairingId == p.id }
                Triple(
                    mine.filter { it.scorer == BattleSide.A }.sumOf { it.points },
                    mine.filter { it.scorer == BattleSide.B }.sumOf { it.points },
                    mine.size,
                )
            }
        }
        val w = winner(a, b, t.pointsToWin, p.playerAId, p.playerBId)
        val status = when {
            w != null -> PairingStatus.DONE
            count > 0 || a > 0 || b > 0 -> PairingStatus.LIVE
            else -> PairingStatus.NOT_STARTED
        }
        return PairingResult(p, a, b, status, w, count)
    }

    // Standings

    /**
     * Standings from finished round results (Swiss or round robin). Order: match wins, point difference,
     * opponents' win % (floored at 33%, byes excluded), head-to-head, points scored.
     */
    fun standings(players: List<EventPlayer>, results: List<PairingResult>): List<Standing> {
        val done = results.filter { it.status == PairingStatus.DONE && !it.pairing.thirdPlace }
        fun recordOf(id: Long): Pair<Int, Int> {
            val mine = done.filter { it.involves(id) }
            return mine.count { it.winnerId == id } to mine.count { it.winnerId != id }
        }
        fun ownPctExcludingByes(id: Long): Double {
            val real = done.filter { it.involves(id) && !it.isBye }
            if (real.isEmpty()) return 0.33
            return (real.count { it.winnerId == id }.toDouble() / real.size).coerceAtLeast(0.33)
        }
        val raw = players.map { pl ->
            val mine = done.filter { it.involves(pl.id) }
            val (w, l) = recordOf(pl.id)
            val opponents = mine.filter { !it.isBye }.mapNotNull { it.opponentOf(pl.id) }
            Standing(
                player = pl,
                rank = 0,
                wins = w,
                losses = l,
                pointsFor = mine.sumOf { it.scoreFor(pl.id) },
                pointsAgainst = mine.sumOf { it.scoreAgainst(pl.id) },
                opponentWinPct = if (opponents.isEmpty()) 0.0 else opponents.map(::ownPctExcludingByes).average(),
                byes = mine.count { it.isBye },
            )
        }
        val headToHead = Comparator<Standing> { x, y ->
            val game = done.firstOrNull { !it.isBye && it.involves(x.player.id) && it.involves(y.player.id) }
            when (game?.winnerId) {
                x.player.id -> -1
                y.player.id -> 1
                else -> 0
            }
        }
        val order = compareByDescending<Standing> { it.wins }
            .thenByDescending { it.diff }
            .thenByDescending { Math.round(it.opponentWinPct * 10000) }
            .then(headToHead)
            .thenByDescending { it.pointsFor }
            .thenBy { it.player.createdAt }
            .thenBy { it.player.id }
        return raw.sortedWith(order).mapIndexed { i, s -> s.copy(rank = i + 1) }
    }

    // Swiss

    private fun key(a: Long, b: Long) = if (a < b) "$a-$b" else "$b-$a"

    /**
     * Pairs a Swiss round. Round 1 is random. Later rounds take players in standings order and pair each one
     * with the closest-ranked player they haven't met (backtracking when needed), so an odd group's lowest
     * player floats down into the next group. With an odd count, the lowest-ranked player without a bye gets one.
     * Rematches are only allowed if no rematch-free pairing exists.
     *
     * @param ranked active (not dropped) player ids in standings order, best first.
     * @return pairs (a, b), b null for the bye, in table order.
     */
    fun pairSwissRound(
        ranked: List<Long>,
        previous: List<PairingResult>,
        round: Int,
        random: Random = Random.Default,
    ): List<Pair<Long, Long?>> {
        val played = previous.filter { !it.isBye }.map { key(it.pairing.playerAId, it.pairing.playerBId!!) }.toSet()
        val hadBye = previous.filter { it.isBye }.map { it.pairing.playerAId }.toSet()
        val order = if (round <= 1) ranked.shuffled(random) else ranked
        var pool = order
        var bye: Long? = null
        if (pool.size % 2 == 1) {
            bye = pool.lastOrNull { it !in hadBye } ?: pool.last()
            pool = pool - bye
        }
        val pairs = backtrack(pool, played) ?: backtrack(pool, emptySet()) ?: pool.chunked(2).map { it[0] to it[1] }
        return pairs + listOfNotNull(bye?.let { it to null as Long? })
    }

    private fun backtrack(remaining: List<Long>, played: Set<String>): List<Pair<Long, Long?>>? {
        if (remaining.isEmpty()) return emptyList()
        val p = remaining.first()
        for (q in remaining.drop(1)) {
            if (key(p, q) in played) continue
            val rest = backtrack(remaining.filter { it != p && it != q }, played) ?: continue
            return listOf<Pair<Long, Long?>>(p to q) + rest
        }
        return null
    }

    // Round robin

    /** Circle method: every player meets every other once. An odd count adds a bye each round. */
    fun roundRobinSchedule(ids: List<Long>): List<List<Pair<Long, Long?>>> {
        if (ids.size < 2) return emptyList()
        val list: MutableList<Long?> = ids.toMutableList<Long?>().apply { if (size % 2 == 1) add(null) }
        val n = list.size
        return (0 until n - 1).map {
            val round = (0 until n / 2).mapNotNull { i ->
                val a = list[i]; val b = list[n - 1 - i]
                when {
                    a == null && b == null -> null
                    a == null -> b!! to null
                    else -> a to b
                }
            }
            // Rotate everything but the first player.
            val last = list.removeAt(n - 1)
            list.add(1, last)
            round.sortedBy { it.second == null } // the bye last
        }
    }

    // Brackets

    /** Seed numbers in bracket order: 4 -> [1,4,2,3], 8 -> [1,8,4,5,2,7,3,6]. Size must be a power of 2. */
    fun bracketOrder(size: Int): List<Int> {
        var order = listOf(1)
        while (order.size < size) {
            val n = order.size * 2
            order = order.flatMap { listOf(it, n + 1 - it) }
        }
        return order
    }

    fun nextPowerOfTwo(n: Int): Int { var p = 1; while (p < n) p *= 2; return p }

    /** First bracket round from players in seed order (best first). Pads to a power of 2 with byes for the top seeds. */
    fun seedBracket(seeded: List<Long>): List<Pair<Long, Long?>> {
        val size = nextPowerOfTwo(seeded.size.coerceAtLeast(2))
        return bracketOrder(size).chunked(2).map { (s1, s2) ->
            val a = seeded.getOrNull(s1 - 1); val b = seeded.getOrNull(s2 - 1)
            when {
                a != null -> a to b
                b != null -> b to null
                else -> error("Empty bracket pair")
            }
        }
    }

    /** Next bracket round: winners of slots 2k and 2k+1 meet in slot k. */
    fun nextBracketRound(current: List<PairingResult>): List<Pair<Long, Long?>> =
        current.filter { !it.pairing.thirdPlace }.sortedBy { it.pairing.bracketSlot }.chunked(2).map { pair ->
            val a = pair[0].winnerId ?: error("Round not finished")
            val b = pair.getOrNull(1)?.let { it.winnerId ?: error("Round not finished") }
            a to b
        }

    /** Bracket round label by how many players are left: F, SF, QF, T16, else R1, R2… */
    fun bracketLabel(playersInRound: Int, round: Int): String = when (playersInRound) {
        2 -> "F"
        4 -> "SF"
        8 -> "QF"
        16 -> "T16"
        else -> "R$round"
    }

    // The whole event

    /** Everything a screen needs about an event, derived from the stored rows. */
    data class EventView(
        val tournament: Tournament,
        val players: List<EventPlayer>,
        val results: List<PairingResult>,
        val standings: List<Standing>,
        /** The round being played: (stage, round), or null before pairing. */
        val current: Pair<MatchStage, Int>?,
        val currentResults: List<PairingResult>,
        val next: NextStep,
        val totalRounds: Int,
    ) {
        val me: EventPlayer? get() = players.firstOrNull { it.isMe }
        val bracket: List<PairingResult> get() = results.filter { it.pairing.stage == MatchStage.TOP_CUT }
        fun player(id: Long?) = players.firstOrNull { it.id == id }
        /** W–L so far for a player (rounds and bracket). */
        fun record(id: Long): Pair<Int, Int> {
            val done = results.filter { it.status == PairingStatus.DONE && it.involves(id) }
            return done.count { it.winnerId == id } to done.count { it.winnerId != id }
        }
    }

    fun view(
        t: Tournament,
        allPlayers: List<EventPlayer>,
        allPairings: List<Pairing>,
        battles: List<EventBattle>,
        matches: List<Match>,
        points: List<BattlePoint>,
    ): EventView {
        val players = allPlayers.filter { it.tournamentId == t.id }
        val meId = players.firstOrNull { it.isMe }?.id
        val matchMap = matches.associateBy { it.id }
        val results = allPairings.filter { it.tournamentId == t.id }.map { p ->
            resolve(p, t, battles, p.matchId?.let { matchMap[it] }, points, meId)
        }
        val roundResults = results.filter { it.pairing.stage == MatchStage.SWISS }
        val standings = standings(players, roundResults)
        val current = results.maxWithOrNull(compareBy<PairingResult> { it.pairing.stage.ordinal }.thenBy { it.pairing.round })
            ?.let { it.pairing.stage to it.pairing.round }
        val currentResults = current?.let { (s, r) -> results.filter { it.pairing.stage == s && it.pairing.round == r } }.orEmpty()
        val active = players.count { !it.dropped }
        val totalRounds = when (t.eventFormat) {
            EventFormat.ROUND_ROBIN -> (if (active % 2 == 0) active - 1 else active).coerceAtLeast(1)
            EventFormat.SINGLE_ELIM -> 0
            else -> t.swissRounds
        }
        return EventView(t, players, results, standings, current, currentResults, nextStep(t, players, current, currentResults, totalRounds), totalRounds)
    }

    private fun nextStep(
        t: Tournament,
        players: List<EventPlayer>,
        current: Pair<MatchStage, Int>?,
        currentResults: List<PairingResult>,
        totalRounds: Int,
    ): NextStep {
        if (t.stage == EventStage.DONE) return NextStep.Done
        val remaining = currentResults.count { it.status != PairingStatus.DONE }
        if (remaining > 0) return NextStep.Waiting(remaining)
        val active = players.count { !it.dropped }
        return when {
            current == null -> when (t.eventFormat) {
                EventFormat.SINGLE_ELIM -> NextStep.StartBracket(nextPowerOfTwo(active))
                else -> NextStep.PairRound(1)
            }
            current.first == MatchStage.SWISS -> when {
                current.second < totalRounds -> NextStep.PairRound(current.second + 1)
                t.eventFormat == EventFormat.SWISS_TOP_CUT && t.topCut >= 2 && active >= 2 ->
                    NextStep.StartBracket(minOf(t.topCut, nextPowerOfTwo(active).let { if (it > active) it / 2 else it }).coerceAtLeast(2))
                else -> NextStep.Finish
            }
            else -> {
                val main = currentResults.filter { !it.pairing.thirdPlace }
                if (main.size <= 1) NextStep.Finish
                else NextStep.NextBracketRound(bracketLabel(main.size, current.second + 1))
            }
        }
    }

    /**
     * Final placings. Bracket results first (champion, finalist, then by the round each player lost in; within
     * a round by Swiss standing or seed, or by the 3rd place match), then everyone else by standings.
     */
    fun finalPlacings(view: EventView): Map<Long, Int> {
        val placings = linkedMapOf<Long, Int>()
        val bracket = view.bracket
        // Elimination-only events have no rounds to rank by, so the seed decides.
        val bracketOnly = view.results.none { it.pairing.stage == MatchStage.SWISS }
        val standingRank = view.standings.associate { it.player.id to it.rank }
        val seedOf = view.players.associate { it.id to (it.seed ?: Int.MAX_VALUE) }
        fun rankKey(id: Long) = if (bracketOnly) seedOf[id] ?: Int.MAX_VALUE else standingRank[id] ?: Int.MAX_VALUE
        if (bracket.isNotEmpty()) {
            val rounds = bracket.filter { !it.pairing.thirdPlace }.groupBy { it.pairing.round }.toSortedMap()
            val finalRound = rounds.values.last()
            val final = finalRound.single()
            final.winnerId?.let { placings[it] = 1 }
            final.loserId?.let { placings[it] = 2 }
            var next = 3
            val third = bracket.firstOrNull { it.pairing.thirdPlace && it.winnerId != null }
            // Losers of each earlier round, latest round first.
            rounds.values.reversed().drop(1).forEach { round ->
                val losers = round.filter { !it.isBye }.mapNotNull { it.loserId }.filter { it !in placings }
                val ordered = if (third != null && round.size == 2) {
                    listOfNotNull(third.winnerId, third.loserId).filter { it in losers } + losers.filter { it != third.winnerId && it != third.loserId }
                } else losers.sortedBy(::rankKey)
                ordered.forEach { placings[it] = next++ }
            }
        }
        var next = (placings.values.maxOrNull() ?: 0) + 1
        view.standings.map { it.player.id }.filter { it !in placings }.forEach { placings[it] = next++ }
        view.players.map { it.id }.filter { it !in placings }.sortedBy(::rankKey).forEach { placings[it] = next++ }
        return placings
    }
}
