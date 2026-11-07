package com.bxtracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BxDao {
    @Query("SELECT * FROM parts ORDER BY name COLLATE NOCASE")
    fun parts(): Flow<List<Part>>

    @Insert suspend fun insertPart(part: Part): Long
    @Update suspend fun updatePart(part: Part)
    @Delete suspend fun deletePart(part: Part)

    @Query("SELECT * FROM combos ORDER BY createdAt DESC")
    fun combos(): Flow<List<Combo>>

    @Insert suspend fun insertCombo(combo: Combo): Long
    @Update suspend fun updateCombo(combo: Combo)
    @Delete suspend fun deleteCombo(combo: Combo)

    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun decks(): Flow<List<Deck>>

    @Insert suspend fun insertDeck(deck: Deck): Long
    @Update suspend fun updateDeck(deck: Deck)
    @Delete suspend fun deleteDeck(deck: Deck)

    @Query("SELECT * FROM tournaments ORDER BY date DESC, id DESC")
    fun tournaments(): Flow<List<Tournament>>

    @Insert suspend fun insertTournament(t: Tournament): Long
    @Update suspend fun updateTournament(t: Tournament)
    @Delete suspend fun deleteTournament(t: Tournament)

    @Query("SELECT * FROM matches ORDER BY startedAt DESC")
    fun matches(): Flow<List<Match>>

    @Insert suspend fun insertMatch(match: Match): Long
    @Update suspend fun updateMatch(match: Match)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatch(id: Long)

    @Query("SELECT * FROM battle_points ORDER BY createdAt, id")
    fun points(): Flow<List<BattlePoint>>

    @Insert suspend fun insertPoint(point: BattlePoint): Long

    @Query("DELETE FROM battle_points WHERE id = (SELECT id FROM battle_points WHERE matchId = :matchId ORDER BY createdAt DESC, id DESC LIMIT 1)")
    suspend fun undoLastPoint(matchId: Long)

    @Query("DELETE FROM battle_points WHERE matchId = :matchId")
    suspend fun deletePointsFor(matchId: Long)

    // Organizer mode

    @Query("SELECT * FROM event_players ORDER BY createdAt, id")
    fun eventPlayers(): Flow<List<EventPlayer>>

    @Insert suspend fun insertEventPlayer(p: EventPlayer): Long
    @Update suspend fun updateEventPlayer(p: EventPlayer)
    @Update suspend fun updateEventPlayers(list: List<EventPlayer>)
    @Delete suspend fun deleteEventPlayer(p: EventPlayer)

    @Query("SELECT * FROM pairings ORDER BY stage, round, thirdPlace, bracketSlot, `table`, id")
    fun pairings(): Flow<List<Pairing>>

    @Insert suspend fun insertPairings(list: List<Pairing>): List<Long>
    @Update suspend fun updatePairing(p: Pairing)
    @Update suspend fun updatePairings(list: List<Pairing>)

    @Query("SELECT * FROM event_battles ORDER BY createdAt, id")
    fun eventBattles(): Flow<List<EventBattle>>

    @Insert suspend fun insertEventBattle(b: EventBattle): Long

    @Query("DELETE FROM event_battles WHERE id = (SELECT id FROM event_battles WHERE pairingId = :pairingId ORDER BY createdAt DESC, id DESC LIMIT 1)")
    suspend fun undoLastEventBattle(pairingId: Long)

    @Query("DELETE FROM event_battles WHERE pairingId IN (SELECT id FROM pairings WHERE tournamentId = :tournamentId)")
    suspend fun deleteEventBattlesFor(tournamentId: Long)

    @Query("DELETE FROM pairings WHERE tournamentId = :tournamentId")
    suspend fun deletePairingsFor(tournamentId: Long)

    @Query("DELETE FROM event_players WHERE tournamentId = :tournamentId")
    suspend fun deleteEventPlayersFor(tournamentId: Long)

    // Backup and restore

    @Query("DELETE FROM parts") suspend fun clearParts()
    @Query("DELETE FROM combos") suspend fun clearCombos()
    @Query("DELETE FROM decks") suspend fun clearDecks()
    @Query("DELETE FROM tournaments") suspend fun clearTournaments()
    @Query("DELETE FROM matches") suspend fun clearMatches()
    @Query("DELETE FROM battle_points") suspend fun clearPoints()
    @Query("DELETE FROM event_players") suspend fun clearEventPlayers()
    @Query("DELETE FROM pairings") suspend fun clearPairings()
    @Query("DELETE FROM event_battles") suspend fun clearEventBattles()

    @Insert suspend fun insertEventPlayers(list: List<EventPlayer>)
    @Insert suspend fun insertEventBattles(list: List<EventBattle>)

    @Insert suspend fun insertParts(list: List<Part>)
    @Insert suspend fun insertCombos(list: List<Combo>)
    @Insert suspend fun insertDecks(list: List<Deck>)
    @Insert suspend fun insertTournaments(list: List<Tournament>)
    @Insert suspend fun insertMatches(list: List<Match>)
    @Insert suspend fun insertPoints(list: List<BattlePoint>)

    /** Replaces everything with a backup. Ids are kept, so links between rows stay intact. */
    @Transaction
    suspend fun replaceAll(b: BackupData) {
        clearEventBattles(); clearPairings(); clearEventPlayers()
        clearPoints(); clearMatches(); clearTournaments(); clearDecks(); clearCombos(); clearParts()
        insertParts(b.parts); insertCombos(b.combos); insertDecks(b.decks)
        insertTournaments(b.tournaments); insertMatches(b.matches); insertPoints(b.points)
        insertEventPlayers(b.eventPlayers); insertPairings(b.pairings); insertEventBattles(b.eventBattles)
    }
}

@Database(
    entities = [
        Part::class, Combo::class, Deck::class, Tournament::class, Match::class, BattlePoint::class,
        EventPlayer::class, Pairing::class, EventBattle::class,
    ],
    // Every schema change needs a version bump and a migration. Exported schemas live in app/schemas.
    // v2: combo shapes (metal/over blade, optional ratchet and bit) and the part built-in flags.
    // v3: organizer mode (event fields on tournaments; event_players, pairings, event_battles).
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BxDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bx.db")
                .build().also { instance = it }
        }
    }
}
