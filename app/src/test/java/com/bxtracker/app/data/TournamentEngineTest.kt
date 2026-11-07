package com.bxtracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TournamentEngineTest {
    private val t = Tournament(id = 1, name = "Cup", organizer = true, eventFormat = EventFormat.SWISS_TOP_CUT, swissRounds = 3, topCut = 4, pointsToWin = 4)

    private fun players(n: Int) = (1..n).map { EventPlayer(id = it.toLong(), tournamentId = 1, name = "P$it", createdAt = it.toLong()) }

    private var nextPairing = 1L

    /** A finished pairing where [winner] wins 4–[loserScore] (manual score). */
    private fun result(a: Long, b: Long?, winner: Long? = a, loserScore: Int = 0, round: Int = 1, stage: MatchStage = MatchStage.SWISS, slot: Int = 0, third: Boolean = false, tour: Tournament = t): PairingResult {
        val sa = if (winner == a) 4 else loserScore
        val sb = if (winner == b) 4 else loserScore
        val p = Pairing(id = nextPairing++, tournamentId = tour.id, stage = stage, round = round, table = 1, bracketSlot = slot, thirdPlace = third,
            playerAId = a, playerBId = b, scoreA = sa, scoreB = sb, manual = b != null)
        return TournamentEngine.resolve(p, tour, emptyList(), null, emptyList(), null)
    }

    private fun pairKey(a: Long, b: Long?) = listOf(a, b ?: -1).sorted().joinToString("-")

    @Test fun suggestedRounds() {
        assertEquals(listOf(1, 2, 2, 3, 3, 4, 4, 5), listOf(2, 3, 4, 5, 8, 9, 16, 24).map(TournamentEngine::suggestedSwissRounds))
    }

    @Test fun winnerRules() {
        assertEquals(1L, TournamentEngine.winner(4, 2, 4, 1, 2))
        assertEquals(2L, TournamentEngine.winner(3, 5, 4, 1, 2))
        assertNull(TournamentEngine.winner(3, 3, 4, 1, 2))
        assertEquals(1L, TournamentEngine.winner(0, 0, 4, 1, null)) // bye
    }

    @Test fun resolveSources() {
        val bye = TournamentEngine.resolve(Pairing(id = 1, tournamentId = 1, stage = MatchStage.SWISS, round = 1, table = 1, playerAId = 7), t, emptyList(), null, emptyList(), null)
        assertEquals(PairingStatus.DONE, bye.status); assertEquals(7L, bye.winnerId); assertEquals(4, bye.scoreA)
        // The bye's value comes from the event and feeds point difference.
        val custom = t.copy(byeScore = 2)
        val bye2 = TournamentEngine.resolve(Pairing(id = 3, tournamentId = 1, stage = MatchStage.SWISS, round = 1, table = 1, playerAId = 7), custom, emptyList(), null, emptyList(), null)
        assertEquals(2, bye2.scoreA)
        val st = TournamentEngine.standings(listOf(EventPlayer(id = 7, tournamentId = 1, name = "P7")), listOf(bye2)).single()
        assertEquals(2, st.diff); assertEquals(1, st.wins)

        val p = Pairing(id = 2, tournamentId = 1, stage = MatchStage.SWISS, round = 1, table = 1, playerAId = 1, playerBId = 2)
        val battles = listOf(
            EventBattle(pairingId = 2, scorer = BattleSide.A, finish = Finish.XTREME, points = 3),
            EventBattle(pairingId = 2, scorer = BattleSide.B, finish = Finish.SPIN, points = 1),
        )
        val live = TournamentEngine.resolve(p, t, battles, null, emptyList(), null)
        assertEquals(PairingStatus.LIVE, live.status); assertEquals(3, live.scoreA); assertEquals(1, live.scoreB); assertEquals(2, live.battles)
        val done = TournamentEngine.resolve(p, t, battles + EventBattle(pairingId = 2, scorer = BattleSide.A, finish = Finish.SPIN, points = 1), null, emptyList(), null)
        assertEquals(PairingStatus.DONE, done.status); assertEquals(1L, done.winnerId)

        // I'm player B: my match's points map onto side B.
        val m = Match(id = 9, opponent = "P1", pointsToWin = 4)
        val pts = listOf(BattlePoint(matchId = 9, scorer = Scorer.ME, points = 3), BattlePoint(matchId = 9, scorer = Scorer.ME, points = 2))
        val mine = TournamentEngine.resolve(p.copy(matchId = 9), t, emptyList(), m, pts, meId = 2)
        assertEquals(0, mine.scoreA); assertEquals(5, mine.scoreB); assertEquals(2L, mine.winnerId)

        // Manual scores win over battles.
        val manual = TournamentEngine.resolve(p.copy(manual = true, scoreA = 1, scoreB = 4), t, battles, null, emptyList(), null)
        assertEquals(2L, manual.winnerId)
    }

    @Test fun swissHasNoRematchesAndEveryoneIsPaired() {
        val ps = players(8)
        val all = mutableListOf<PairingResult>()
        val rnd = Random(42)
        repeat(3) { r ->
            val ranked = TournamentEngine.standings(ps, all).map { it.player.id }
            val pairs = TournamentEngine.pairSwissRound(ranked, all, r + 1, rnd)
            assertEquals(4, pairs.size)
            assertEquals(8, pairs.flatMap { listOfNotNull(it.first, it.second) }.toSet().size)
            pairs.forEach { (a, b) -> all += result(a, b, winner = if (rnd.nextBoolean()) a else b, loserScore = rnd.nextInt(4), round = r + 1) }
        }
        val keys = all.map { pairKey(it.pairing.playerAId, it.pairing.playerBId) }
        assertEquals("rematch found", keys.size, keys.toSet().size)
    }

    @Test fun laterRoundsPairByRecord() {
        val ps = players(8)
        // Round 1: 1,2,3,4 win.
        val r1 = listOf(result(1, 5), result(2, 6), result(3, 7), result(4, 8))
        val ranked = TournamentEngine.standings(ps, r1).map { it.player.id }
        val pairs = TournamentEngine.pairSwissRound(ranked, r1, 2)
        val winners = setOf(1L, 2L, 3L, 4L)
        pairs.forEach { (a, b) -> assertEquals("winners meet winners", a in winners, b in winners) }
    }

    @Test fun backtracksToAvoidRematch() {
        // A greedy pass would pair 1-2 then be stuck with 3-4, a rematch. 1-3 / 2-4 is the only clean option... check it's found.
        val prev = listOf(result(1, 2, round = 1), result(3, 4, round = 1), result(1, 4, round = 2), result(2, 3, round = 2))
        val pairs = TournamentEngine.pairSwissRound(listOf(1L, 2L, 3L, 4L), prev, 3)
        val keys = pairs.map { pairKey(it.first, it.second) }.toSet()
        assertEquals(setOf("1-3", "2-4"), keys)
    }

    @Test fun byesGoToLowestRankedWithoutABye() {
        val ps = players(7)
        val all = mutableListOf<PairingResult>()
        val byes = mutableListOf<Long>()
        val rnd = Random(7)
        repeat(3) { r ->
            val ranked = TournamentEngine.standings(ps, all).map { it.player.id }
            val pairs = TournamentEngine.pairSwissRound(ranked, all, r + 1, rnd)
            val bye = pairs.single { it.second == null }.first
            if (r > 0) {
                val withoutBye = ranked.filter { it !in byes }
                assertEquals("bye goes to the lowest ranked without one", withoutBye.last(), bye)
            }
            byes += bye
            pairs.forEach { (a, b) -> all += result(a, b, winner = if (b == null || rnd.nextBoolean()) a else b, round = r + 1) }
        }
        assertEquals(3, byes.toSet().size)
        // A bye is a win worth byeScore–0.
        val st = TournamentEngine.standings(ps, all).first { it.player.id == byes.first() }
        assertEquals(1, st.byes)
    }

    @Test fun tiebreakersPointDiffThenOpponentsThenHeadToHead() {
        val ps = players(4)
        // 1 and 2 both 1-1. 1 beat 3 4-0 and lost to 4 3-4; 2 beat 4 4-3 and lost to 3 0-4. Diff: 1 => +3, 2 => -3.
        val res = listOf(result(1, 3, winner = 1, loserScore = 0), result(4, 1, winner = 4, loserScore = 3), result(2, 4, winner = 2, loserScore = 3), result(3, 2, winner = 3, loserScore = 0))
        val st = TournamentEngine.standings(ps, res)
        assertTrue(st.indexOfFirst { it.player.id == 1L } < st.indexOfFirst { it.player.id == 2L })

        // Same record and diff: head-to-head decides.
        val h2h = listOf(result(1, 2, winner = 2, loserScore = 3), result(3, 4, winner = 3, loserScore = 3), result(1, 3, winner = 1, loserScore = 3), result(2, 4, winner = 4, loserScore = 3))
        val st2 = TournamentEngine.standings(ps, h2h)
        val one = st2.first { it.player.id == 1L }; val two = st2.first { it.player.id == 2L }
        assertEquals(one.wins, two.wins); assertEquals(one.diff, two.diff)
        if (Math.round(one.opponentWinPct * 10000) == Math.round(two.opponentWinPct * 10000)) assertTrue(two.rank < one.rank)

        // Opponents' win % is floored at 33%.
        val floor = TournamentEngine.standings(players(2), listOf(result(1, 2)))
        assertEquals(0.33, floor.first { it.player.id == 1L }.opponentWinPct, 1e-9)
    }

    @Test fun bracketSeeding() {
        assertEquals(listOf(1, 4, 2, 3), TournamentEngine.bracketOrder(4))
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), TournamentEngine.bracketOrder(8))
        // 6 players: seeds 1 and 2 get byes.
        val first = TournamentEngine.seedBracket(listOf(11L, 12L, 13L, 14L, 15L, 16L))
        assertEquals(4, first.size)
        assertEquals(11L to null, first[0]); assertEquals(14L to 15L, first[1]); assertEquals(12L to null, first[2]); assertEquals(13L to 16L, first[3])
    }

    @Test fun roundRobinEveryoneMeetsOnce() {
        val four = TournamentEngine.roundRobinSchedule(listOf(1, 2, 3, 4))
        assertEquals(3, four.size)
        assertEquals(6, four.flatten().map { pairKey(it.first, it.second) }.toSet().size)
        val five = TournamentEngine.roundRobinSchedule(listOf(1, 2, 3, 4, 5))
        assertEquals(5, five.size)
        assertEquals(10, five.flatten().filter { it.second != null }.map { pairKey(it.first, it.second) }.toSet().size)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), five.flatten().filter { it.second == null }.map { it.first }.toSet())
    }

    @Test fun fullSwissTopCutWithThirdPlace() {
        val tour = t.copy(thirdPlaceMatch = true, stage = EventStage.ROUNDS)
        val ps = players(6)
        val pairings = mutableListOf<Pairing>()
        fun view() = TournamentEngine.view(tour, ps, pairings, emptyList(), emptyList(), emptyList())
        fun add(pairs: List<Pair<Long, Long?>>, stage: MatchStage, round: Int, third: Boolean = false, startSlot: Int = 0) {
            pairs.forEachIndexed { i, (a, b) ->
                pairings += Pairing(id = nextPairing++, tournamentId = 1, stage = stage, round = round, table = i + 1, bracketSlot = startSlot + i, thirdPlace = third, playerAId = a, playerBId = b)
            }
        }
        /** Lower id always wins 4–2 (so P1 is the best player). */
        fun playAll() {
            val v = view()
            v.currentResults.filter { !it.isBye }.forEach { r ->
                val idx = pairings.indexOfFirst { it.id == r.pairing.id }
                val aWins = r.pairing.playerAId < r.pairing.playerBId!!
                pairings[idx] = r.pairing.copy(manual = true, scoreA = if (aWins) 4 else 2, scoreB = if (aWins) 2 else 4)
            }
        }
        assertEquals(NextStep.PairRound(1), view().next)
        for (round in 1..3) {
            val v = view()
            add(TournamentEngine.pairSwissRound(v.standings.map { it.player.id }, v.results, round, Random(round)), MatchStage.SWISS, round)
            assertTrue(view().next is NextStep.Waiting)
            playAll()
        }
        assertEquals(NextStep.StartBracket(4), view().next)
        val seeds = view().standings.take(4).map { it.player.id }
        add(TournamentEngine.seedBracket(seeds), MatchStage.TOP_CUT, 1)
        assertEquals(seeds[0] to seeds[3], pairings.filter { it.stage == MatchStage.TOP_CUT }.first().let { it.playerAId to it.playerBId })
        playAll()
        assertEquals(NextStep.NextBracketRound("F"), view().next)
        val semis = view().currentResults
        add(TournamentEngine.nextBracketRound(semis), MatchStage.TOP_CUT, 2)
        add(listOf(semis[0].loserId!! to semis[1].loserId), MatchStage.TOP_CUT, 2, third = true)
        playAll()
        assertEquals(NextStep.Finish, view().next)
        val placings = TournamentEngine.finalPlacings(view())
        assertEquals(6, placings.size)
        assertEquals((1..6).toSet(), placings.values.toSet())
        assertEquals(1, placings[1L]) // P1 never loses
        assertFalse(placings.values.any { it <= 0 })
    }
}
