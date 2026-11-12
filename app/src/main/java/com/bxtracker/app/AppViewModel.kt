package com.bxtracker.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bxtracker.app.data.AppDatabase
import com.bxtracker.app.data.AppSettings
import com.bxtracker.app.data.Backup
import com.bxtracker.app.data.BackupData
import com.bxtracker.app.data.BattlePoint
import com.bxtracker.app.data.Catalog
import com.bxtracker.app.data.CatalogEntry
import com.bxtracker.app.data.ManualRow
import com.bxtracker.app.data.Product
import com.bxtracker.app.data.Products
import com.bxtracker.app.data.StockBuild
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Combo
import com.bxtracker.app.data.Deck
import com.bxtracker.app.data.DeckFix
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.Match
import com.bxtracker.app.data.Part
import com.bxtracker.app.data.PartKind
import com.bxtracker.app.data.PhotoSize
import com.bxtracker.app.data.PhotoStore
import com.bxtracker.app.data.SettingsStore
import com.bxtracker.app.data.ThemeMode
import com.bxtracker.app.data.EventBattle
import com.bxtracker.app.data.EventFormat
import com.bxtracker.app.data.EventPlayer
import com.bxtracker.app.data.EventStage
import com.bxtracker.app.data.MatchStage
import com.bxtracker.app.data.NextStep
import com.bxtracker.app.data.Pairing
import com.bxtracker.app.data.TournamentEngine
import com.bxtracker.app.data.Tournament
import com.bxtracker.app.data.TournamentStatus
import com.bxtracker.app.data.comboIds
import com.bxtracker.app.data.partIds
import com.bxtracker.app.data.roundLabel
import com.bxtracker.app.data.stageFor
import com.bxtracker.app.data.withSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val store = SettingsStore(app)

    val parts: StateFlow<List<Part>> = dao.parts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val combos: StateFlow<List<Combo>> = dao.combos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val decks: StateFlow<List<Deck>> = dao.decks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tournaments: StateFlow<List<Tournament>> = dao.tournaments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val matches: StateFlow<List<Match>> = dao.matches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val points: StateFlow<List<BattlePoint>> = dao.points().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Bundled part names for autocomplete (assets/catalog.json). Loaded once. */
    val catalog: Map<PartKind, List<CatalogEntry>> by lazy { Catalog.load(app) }

    /** Bundled products with their stock builds (assets/catalog.json, PRODUCTS). Loaded once. */
    val products: List<Product> by lazy { Products.load(app) }

    /** The catalog entry for a stock part, for its code, type and built-in flags. */
    private fun catalogEntry(kind: PartKind, name: String): CatalogEntry? =
        catalog[kind].orEmpty().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Adds a product's stock parts: parts I already have get +1 copy, new parts are added with no photo and
     * the product in "Got it from". With [onlyMissing], only parts I don't own (or own 0 of) are added. With
     * [saveCombo], the stock combo is saved too, unless I already have a combo with exactly those parts.
     * [onDone] gets how many parts were added.
     */
    fun addProductBuild(product: Product, build: StockBuild, saveCombo: Boolean, onlyMissing: Boolean = false, onDone: (Int) -> Unit = {}) =
        viewModelScope.launch {
            var added = 0
            val ids = linkedMapOf<PartKind, Long>()
            build.parts.forEach { sp ->
                val entry = catalogEntry(sp.kind, sp.name)
                val existing = dao.findPart(sp.kind, sp.name)
                if (onlyMissing && existing != null && existing.copies > 0) { ids[sp.kind] = existing.id; return@forEach }
                if (existing != null) {
                    dao.updatePart(existing.copy(copies = existing.copies + 1, source = existing.source.ifBlank { product.label }))
                    ids[sp.kind] = existing.id
                } else {
                    val code = when (sp.kind) {
                        PartKind.RATCHET -> entry?.takeIf { it.builtInBit }?.code.orEmpty().ifBlank { sp.name }
                        else -> sp.code.ifBlank { entry?.code.orEmpty() }
                    }
                    ids[sp.kind] = dao.insertPart(
                        Part(
                            kind = sp.kind, name = entry?.name ?: sp.name, code = code,
                            type = entry?.type ?: build.type.takeIf { sp.kind == PartKind.BLADE || sp.kind == PartKind.MAIN_BLADE || sp.kind == PartKind.METAL_BLADE },
                            leftSpin = entry?.leftSpin == true,
                            builtInRatchet = entry?.builtInRatchet == true, builtInBit = entry?.builtInBit == true,
                            copies = 1, source = product.label,
                        )
                    )
                }
                added++
            }
            if (saveCombo && ids.size == build.parts.size) {
                val combo = Combo(
                    bladeId = ids[PartKind.BLADE], lockChipId = ids[PartKind.LOCK_CHIP], mainBladeId = ids[PartKind.MAIN_BLADE],
                    metalBladeId = ids[PartKind.METAL_BLADE], overBladeId = ids[PartKind.OVER_BLADE], assistBladeId = ids[PartKind.ASSIST_BLADE],
                    ratchetId = ids[PartKind.RATCHET], bitId = ids[PartKind.BIT], type = build.type ?: BeyType.BALANCE,
                )
                val same = dao.allCombos().any { it.partIds().sorted() == combo.partIds().sorted() }
                if (!same) dao.insertCombo(combo)
            }
            onDone(added)
        }

    /** Adds the manual rows that have a name (screen 20). Same kind + name merges into copies, like a single add. */
    fun addParts(rows: List<ManualRow>, onDone: (Int) -> Unit = {}) = viewModelScope.launch {
        val ready = rows.filter { it.ready }
        ready.forEach { r ->
            val entry = catalogEntry(r.kind, r.name)
            val name = r.name.trim()
            val existing = dao.findPart(r.kind, name)
            if (existing != null) {
                dao.updatePart(existing.copy(copies = existing.copies + r.copies))
            } else {
                val hasType = r.kind == PartKind.BLADE || r.kind == PartKind.MAIN_BLADE || r.kind == PartKind.METAL_BLADE
                dao.insertPart(
                    Part(
                        kind = r.kind, name = name,
                        code = if (r.kind == PartKind.RATCHET) entry?.takeIf { it.builtInBit }?.code.orEmpty().ifBlank { name }
                        else r.code.trim().ifBlank { entry?.code.orEmpty() },
                        type = if (hasType) r.type ?: entry?.type else null,
                        leftSpin = entry?.leftSpin == true,
                        builtInRatchet = entry?.builtInRatchet == true, builtInBit = entry?.builtInBit == true,
                        copies = r.copies,
                    )
                )
            }
        }
        onDone(ready.size)
    }

    // Parts

    fun updatePart(part: Part) = viewModelScope.launch { dao.updatePart(part) }

    /** Adds a part, or adds copies to an existing part with the same kind and name. */
    fun addPart(part: Part, onDone: () -> Unit = {}) = viewModelScope.launch {
        val existing = parts.value.firstOrNull {
            it.kind == part.kind && it.name.trim().equals(part.name.trim(), ignoreCase = true)
        }
        if (existing != null) {
            dao.updatePart(
                existing.copy(
                    copies = existing.copies + part.copies,
                    photoPath = existing.photoPath ?: part.photoPath,
                )
            )
            if (existing.photoPath != null) PhotoStore.delete(part.photoPath)
        } else {
            dao.insertPart(part)
        }
        onDone()
    }

    fun deletePart(part: Part) = viewModelScope.launch {
        combos.value.filter { part.id in it.partIds() }.forEach { removeCombo(it) }
        dao.deletePart(part)
        PhotoStore.delete(part.photoPath)
    }

    // Combos

    fun saveCombo(combo: Combo, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (combo.id == 0L) dao.insertCombo(combo) else dao.updateCombo(combo)
        onDone()
    }

    fun toggleFavorite(combo: Combo) = viewModelScope.launch { dao.updateCombo(combo.copy(favorite = !combo.favorite)) }

    fun deleteCombo(combo: Combo) = viewModelScope.launch { removeCombo(combo) }

    private suspend fun removeCombo(combo: Combo) {
        decks.value.filter { combo.id in it.comboIds() }.forEach { deck ->
            dao.updateDeck(
                deck.copy(
                    combo1Id = deck.combo1Id.takeIf { it != combo.id },
                    combo2Id = deck.combo2Id.takeIf { it != combo.id },
                    combo3Id = deck.combo3Id.takeIf { it != combo.id },
                )
            )
        }
        dao.deleteCombo(combo)
    }

    // Decks

    fun createDeck(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertDeck(Deck(name = name)))
    }

    fun updateDeck(deck: Deck) = viewModelScope.launch { dao.updateDeck(deck) }

    fun deleteDeck(deck: Deck) = viewModelScope.launch { dao.deleteDeck(deck) }

    /** Applies a suggested fix: reuses the variant combo if it exists, otherwise builds it, then puts it in the slot. */
    fun applyDeckFix(deck: Deck, fix: DeckFix) = viewModelScope.launch {
        val comboId = fix.existing?.id ?: dao.insertCombo(fix.result())
        dao.updateDeck(deck.withSlot(fix.slot - 1, comboId))
    }

    // Tournaments

    fun createTournament(t: Tournament, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertTournament(t))
    }

    fun updateTournament(t: Tournament) = viewModelScope.launch { dao.updateTournament(t) }

    /** Deletes the tournament with its matches and their battles. */
    fun deleteTournament(t: Tournament) = viewModelScope.launch {
        matches.value.filter { it.tournamentId == t.id }.forEach {
            dao.deletePointsFor(it.id)
            dao.deleteMatch(it.id)
        }
        dao.deleteTournament(t)
    }

    /** Starts the next round: a match linked to the tournament, named R1, R2… or QF, SF, F, using its deck. */
    fun startRound(t: Tournament, opponent: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val index = matches.value.count { it.tournamentId == t.id }
        if (t.status != TournamentStatus.IN_PROGRESS) dao.updateTournament(t.copy(status = TournamentStatus.IN_PROGRESS))
        val id = dao.insertMatch(
            Match(
                opponent = opponent.trim().ifBlank { "Opponent" },
                tournamentId = t.id,
                round = t.roundLabel(index),
                stage = t.stageFor(index),
                deckId = t.deckId,
                pointsToWin = settings.value.pointsToWin,
            )
        )
        onCreated(id)
    }

    // Matches

    fun startMatch(match: Match, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertMatch(match))
    }

    fun addPoint(point: BattlePoint) = viewModelScope.launch { dao.insertPoint(point) }

    fun undoLastPoint(matchId: Long) = viewModelScope.launch { dao.undoLastPoint(matchId) }

    fun setFinished(match: Match, finished: Boolean) = viewModelScope.launch {
        dao.updateMatch(match.copy(finished = finished))
    }

    fun deleteMatch(match: Match) = viewModelScope.launch {
        dao.deletePointsFor(match.id)
        dao.deleteMatch(match.id)
    }

    // Settings

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }

    fun setPointsToWin(points: Int) = viewModelScope.launch { store.setPointsToWin(points.coerceIn(1, 20)) }

    fun setFinishPoints(finish: Finish, points: Int) = viewModelScope.launch { store.setFinishPoints(finish, points.coerceIn(0, 9)) }

    fun setSeasonStart(millis: Long?) = viewModelScope.launch { store.setSeasonStart(millis) }

    fun setPhotoSize(size: PhotoSize) = viewModelScope.launch { store.setPhotoSize(size) }

    // Backup

    private fun snapshot() = BackupData(
        parts = parts.value, combos = combos.value, decks = decks.value,
        tournaments = tournaments.value, matches = matches.value, points = points.value,
        eventPlayers = eventPlayers.value, pairings = pairings.value, eventBattles = eventBattles.value,
    )

    // Organizer mode

    val eventPlayers: StateFlow<List<EventPlayer>> = dao.eventPlayers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pairings: StateFlow<List<Pairing>> = dao.pairings().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val eventBattles: StateFlow<List<EventBattle>> = dao.eventBattles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Current state of an event (results, standings, next step), or null if it doesn't exist. */
    fun eventView(tournamentId: Long): TournamentEngine.EventView? {
        val t = tournaments.value.firstOrNull { it.id == tournamentId } ?: return null
        return TournamentEngine.view(t, eventPlayers.value, pairings.value, eventBattles.value, matches.value, points.value)
    }

    /** Names used before (event players and match opponents), for autocomplete. */
    fun knownPlayerNames(): List<String> =
        (eventPlayers.value.filter { !it.isMe }.map { it.name } + matches.value.map { it.opponent })
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()

    /** Creates an organized event. If [myName] is given, I'm added as a player ("you"). */
    fun createEvent(t: Tournament, myName: String?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = dao.insertTournament(t.copy(organizer = true, stage = EventStage.SETUP, status = TournamentStatus.PLANNED))
        if (myName != null) dao.insertEventPlayer(EventPlayer(tournamentId = id, name = myName.trim().ifBlank { "Me" }, isMe = true))
        onCreated(id)
    }

    fun updateEvent(t: Tournament) = viewModelScope.launch { dao.updateTournament(t) }

    /** Adds a player during setup. Same name twice is ignored. */
    fun addEventPlayer(tournamentId: Long, name: String) = viewModelScope.launch {
        val n = name.trim()
        if (n.isBlank()) return@launch
        if (eventPlayers.value.any { it.tournamentId == tournamentId && it.name.equals(n, true) }) return@launch
        dao.insertEventPlayer(EventPlayer(tournamentId = tournamentId, name = n))
    }

    fun updateEventPlayer(p: EventPlayer) = viewModelScope.launch { dao.updateEventPlayer(p) }

    /** Removes a player. Only possible before the first round; after that, drop them instead. */
    fun removeEventPlayer(p: EventPlayer) = viewModelScope.launch {
        if (pairings.value.none { it.tournamentId == p.tournamentId }) dao.deleteEventPlayer(p)
    }

    /** Dropped players are never paired again. Their finished matches still count. */
    fun setDropped(p: EventPlayer, dropped: Boolean) = viewModelScope.launch { dao.updateEventPlayer(p.copy(dropped = dropped)) }

    /**
     * Starts the event: if anyone is checked in, players who aren't are dropped (they can be un-dropped later),
     * then round 1 (or the bracket) is paired.
     */
    fun startEvent(tournamentId: Long) = viewModelScope.launch {
        val players = eventPlayers.value.filter { it.tournamentId == tournamentId }
        if (players.any { it.checkedIn }) {
            dao.updateEventPlayers(players.filter { !it.checkedIn && !it.dropped }.map { it.copy(dropped = true) })
        }
        // Let the player flow catch up before pairing from it.
        eventPlayers.first { list -> list.filter { it.tournamentId == tournamentId }.none { !it.checkedIn && !it.dropped } || players.none { it.checkedIn } }
        advanceEvent(tournamentId).join()
    }

    /**
     * Does the next step: pair round 1 or the next round, start the top cut / bracket, pair the next bracket
     * round (plus the 3rd place match), or finish the event and save placings.
     */
    fun advanceEvent(tournamentId: Long, random: kotlin.random.Random = kotlin.random.Random.Default) = viewModelScope.launch {
        val v = eventView(tournamentId) ?: return@launch
        val t = v.tournament
        val active = v.standings.filter { !it.player.dropped }.map { it.player.id }
        when (val step = v.next) {
            is NextStep.PairRound -> {
                val pairs = if (t.eventFormat == EventFormat.ROUND_ROBIN) {
                    // The schedule is fixed by registration order, so each round is the same however often it's built.
                    val ids = v.players.filter { !it.dropped }.sortedBy { it.createdAt }.map { it.id }
                    TournamentEngine.roundRobinSchedule(ids).getOrNull(step.round - 1).orEmpty()
                } else {
                    TournamentEngine.pairSwissRound(active, v.results.filter { it.pairing.stage == MatchStage.SWISS }, step.round, random)
                }
                insertRound(t, MatchStage.SWISS, step.round, pairs)
                dao.updateTournament(t.copy(stage = EventStage.ROUNDS, status = TournamentStatus.IN_PROGRESS, playerCount = v.players.size))
            }
            is NextStep.StartBracket -> {
                val seeded = if (t.eventFormat == EventFormat.SINGLE_ELIM) {
                    val list = v.players.filter { !it.dropped }.sortedBy { it.createdAt }.map { it.id }
                    if (t.randomSeeding) list.shuffled(random) else list
                } else active.take(step.size)
                dao.updateEventPlayers(seeded.mapIndexedNotNull { i, id -> v.player(id)?.copy(seed = i + 1) })
                insertRound(t, MatchStage.TOP_CUT, 1, TournamentEngine.seedBracket(seeded))
                dao.updateTournament(t.copy(stage = EventStage.BRACKET, status = TournamentStatus.IN_PROGRESS, playerCount = v.players.size))
            }
            is NextStep.NextBracketRound -> {
                val (_, round) = v.current ?: return@launch
                val pairs = TournamentEngine.nextBracketRound(v.currentResults)
                insertRound(t, MatchStage.TOP_CUT, round + 1, pairs)
                if (pairs.size == 1 && t.thirdPlaceMatch) {
                    val losers = v.currentResults.filter { !it.pairing.thirdPlace && !it.isBye }.mapNotNull { it.loserId }
                    if (losers.size == 2) insertRound(t, MatchStage.TOP_CUT, round + 1, listOf(losers[0] to losers[1]), third = true, firstTable = 2)
                }
            }
            NextStep.Finish -> {
                val placings = TournamentEngine.finalPlacings(v)
                dao.updateEventPlayers(v.players.map { it.copy(finalPlacing = placings[it.id]) })
                // When I played, my placing also goes into my personal tournament results.
                val mine = v.me?.let { placings[it.id] }
                dao.updateTournament(t.copy(stage = EventStage.DONE, status = TournamentStatus.DONE, placement = mine ?: t.placement, playerCount = v.players.size))
            }
            else -> Unit
        }
    }

    private suspend fun insertRound(t: Tournament, stage: MatchStage, round: Int, pairs: List<Pair<Long, Long?>>, third: Boolean = false, firstTable: Int = 1) {
        // Real matches first, the bye last.
        val ordered = pairs.sortedBy { it.second == null }
        dao.insertPairings(ordered.mapIndexed { i, (a, b) ->
            Pairing(tournamentId = t.id, stage = stage, round = round, table = firstTable + i, bracketSlot = pairs.indexOf(a to b),
                thirdPlace = third, playerAId = a, playerBId = b)
        })
    }

    /** Swaps one player between two pairings of the round that haven't started. */
    fun swapPlayers(first: Pairing, firstIsA: Boolean, second: Pairing, secondIsA: Boolean) = viewModelScope.launch {
        val x = if (firstIsA) first.playerAId else first.playerBId ?: return@launch
        val y = if (secondIsA) second.playerAId else second.playerBId ?: return@launch
        if (first.id == second.id) {
            dao.updatePairing(first.copy(playerAId = first.playerBId ?: return@launch, playerBId = first.playerAId))
            return@launch
        }
        val a = if (firstIsA) first.copy(playerAId = y) else first.copy(playerBId = y)
        val b = if (secondIsA) second.copy(playerAId = x) else second.copy(playerBId = x)
        dao.updatePairings(listOf(a, b))
    }

    /** Types in a final score (final-score-only events, or the organizer correcting a match). */
    fun setFinalScore(p: Pairing, scoreA: Int, scoreB: Int) = viewModelScope.launch {
        dao.updatePairing(p.copy(scoreA = scoreA, scoreB = scoreB, manual = true))
    }

    /** Back to battle-by-battle scoring for a pairing whose score was typed in. */
    fun clearFinalScore(p: Pairing) = viewModelScope.launch { dao.updatePairing(p.copy(scoreA = 0, scoreB = 0, manual = false)) }

    fun addEventBattle(b: EventBattle) = viewModelScope.launch { dao.insertEventBattle(b) }

    fun undoEventBattle(pairingId: Long) = viewModelScope.launch { dao.undoLastEventBattle(pairingId) }

    /**
     * My pairings are scored as normal matches (so my combos and stats stay complete). Creates the match the
     * first time, named after the round ("R2", "SF", "3P"), and hands back its id.
     */
    fun matchForPairing(p: Pairing, onReady: (Long) -> Unit) = viewModelScope.launch {
        p.matchId?.let { existing -> if (matches.value.any { it.id == existing }) { onReady(existing); return@launch } }
        val v = eventView(p.tournamentId) ?: return@launch
        val me = v.me ?: return@launch
        val opponentId = if (p.playerAId == me.id) p.playerBId else p.playerAId
        val opponent = v.player(opponentId)?.name ?: return@launch
        val roundLabel = when {
            p.thirdPlace -> "3P"
            p.stage == MatchStage.SWISS -> "R${p.round}"
            else -> TournamentEngine.bracketLabel(
                v.results.count { it.pairing.stage == MatchStage.TOP_CUT && it.pairing.round == p.round && !it.pairing.thirdPlace } * 2,
                p.round,
            )
        }
        val id = dao.insertMatch(
            Match(opponent = opponent, tournamentId = p.tournamentId, round = roundLabel, stage = p.stage,
                deckId = v.tournament.deckId, pointsToWin = v.tournament.pointsToWin)
        )
        dao.updatePairing(p.copy(matchId = id))
        onReady(id)
    }

    /** Deletes an organized event with its players, pairings, battles and my matches in it. */
    fun deleteEvent(t: Tournament) = viewModelScope.launch {
        dao.deleteEventBattlesFor(t.id)
        dao.deletePairingsFor(t.id)
        dao.deleteEventPlayersFor(t.id)
        matches.value.filter { it.tournamentId == t.id }.forEach {
            dao.deletePointsFor(it.id)
            dao.deleteMatch(it.id)
        }
        dao.deleteTournament(t)
    }

    /** Exports a zip backup. The UI turns the [DataResult] into a message in the current language. */
    fun exportBackup(uri: Uri, onResult: (DataResult) -> Unit) = viewModelScope.launch {
        val data = snapshot()
        onResult(
            runCatching {
                withContext(Dispatchers.IO) { Backup.export(getApplication(), uri, data) }
                DataResult.BackedUp(data.parts.size, data.combos.size, data.matches.size)
            }.getOrElse { DataResult.Failed(DataResult.Action.BACKUP, it) }
        )
    }

    /** Replaces everything with the backup at [uri]. Photos no longer referenced are cleaned up. */
    fun importBackup(uri: Uri, onResult: (DataResult) -> Unit) = viewModelScope.launch {
        onResult(
            runCatching {
                val data = withContext(Dispatchers.IO) { Backup.read(getApplication(), uri) }
                dao.replaceAll(data)
                withContext(Dispatchers.IO) {
                    val keep = data.parts.mapNotNull { it.photoPath }.map { File(it).name }.toSet()
                    PhotoStore.dir(getApplication()).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
                }
                DataResult.Restored(data.parts.size, data.combos.size, data.matches.size)
            }.getOrElse { DataResult.Failed(DataResult.Action.RESTORE, it) }
        )
    }

    fun exportCsv(treeUri: Uri, onResult: (DataResult) -> Unit) = viewModelScope.launch {
        val data = snapshot()
        onResult(
            runCatching {
                DataResult.CsvSaved(withContext(Dispatchers.IO) { Backup.exportCsv(getApplication(), treeUri, data) })
            }.getOrElse { DataResult.Failed(DataResult.Action.CSV, it) }
        )
    }
}

/** Outcome of a backup, restore or CSV export. */
sealed interface DataResult {
    enum class Action { BACKUP, RESTORE, CSV }
    data class BackedUp(val parts: Int, val combos: Int, val matches: Int) : DataResult
    data class Restored(val parts: Int, val combos: Int, val matches: Int) : DataResult
    data class CsvSaved(val files: List<String>) : DataResult
    data class Failed(val action: Action, val error: Throwable) : DataResult {
        val notABackup: Boolean get() = error is com.bxtracker.app.data.NotABackupException
    }
}
