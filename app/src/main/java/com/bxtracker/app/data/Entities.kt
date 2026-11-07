package com.bxtracker.app.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.bxtracker.app.R
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class PartKind(val label: String, val plural: String, val isCx: Boolean = false) {
    BLADE("Blade", "Blades"),
    RATCHET("Ratchet", "Ratchets"),
    BIT("Bit", "Bits"),
    LOCK_CHIP("Lock Chip", "Lock Chips", true),
    MAIN_BLADE("Main Blade", "Main Blades", true),
    /** CX alternative to a Main Blade: a Metal Blade plus an Over Blade (e.g. "Bahamut Blitz BK1-50I"). */
    METAL_BLADE("Metal Blade", "Metal Blades", true),
    OVER_BLADE("Over Blade", "Over Blades", true),
    ASSIST_BLADE("Assist Blade", "Assist Blades", true),
}

/** Which parts a combo is built from. */
enum class ComboShape(val label: String) {
    /** Blade + Ratchet + Bit. */
    BASIC("Basic"),
    /** Lock Chip + Main Blade + Assist Blade + Ratchet + Bit. */
    CX("CX"),
    /** Lock Chip + Metal Blade + Over Blade + Assist Blade + Ratchet + Bit. */
    CX_METAL("CX Metal"),
}

enum class BeyType(val label: String) {
    ATTACK("Attack"), DEFENSE("Defense"), STAMINA("Stamina"), BALANCE("Balance")
}

/** Default points per finish. The values actually used come from settings (see [AppSettings.pointsFor]). */
enum class Finish(val label: String, val points: Int) {
    SPIN("Spin", 1), OVER("Over", 2), BURST("Burst", 2), XTREME("Xtreme", 3)
}

enum class Scorer { ME, OPPONENT, DRAW }

/** Which product line a part comes from. Stored as text so it's easy to read in backups. */
enum class ProductLine(val label: String) { TAKARA_TOMY("Takara Tomy"), HASBRO("Hasbro") }

enum class TournamentStatus(@StringRes val label: Int) { PLANNED(R.string.status_planned), IN_PROGRESS(R.string.status_in_progress), DONE(R.string.status_done) }

enum class MatchStage { SWISS, TOP_CUT }

// Organizer mode: the app runs a whole event. An organized event is a Tournament with organizer = true.

/** Swiss rounds then an optional top cut; a single-elimination bracket; or everyone plays everyone. */
enum class EventFormat { SWISS_TOP_CUT, SINGLE_ELIM, ROUND_ROBIN }

/** Battle by battle (full stats for every player) or only the final score of each match. */
enum class EventScoring { BATTLES, FINAL_SCORE_ONLY }

/** SETUP: adding players. ROUNDS: Swiss or round-robin rounds. BRACKET: top cut or elimination bracket. */
enum class EventStage { SETUP, ROUNDS, BRACKET, DONE }

enum class PairingStatus { NOT_STARTED, LIVE, DONE }

/** Who scored in an organizer-scored battle: player A, player B, or nobody. */
enum class BattleSide { A, B, DRAW }

@Serializable
@Entity(tableName = "event_players", indices = [Index("tournamentId")])
data class EventPlayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tournamentId: Long,
    val name: String,
    /** The row that is me. My matches are scored as normal matches so my stats stay complete. */
    val isMe: Boolean = false,
    val deckNote: String = "",
    /** Dropped players are never paired again. */
    val dropped: Boolean = false,
    val checkedIn: Boolean = false,
    /** Set when the bracket starts (top cut seed or elimination seed). */
    val seed: Int? = null,
    val finalPlacing: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One match in an organized event. [playerBId] null is a bye. Scores come from, in order: [manual] scores,
 * the linked personal [matchId] (when I play), or the [EventBattle]s; see TournamentEngine.resolve.
 */
@Serializable
@Entity(tableName = "pairings", indices = [Index("tournamentId")])
data class Pairing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tournamentId: Long,
    /** SWISS for Swiss and round-robin rounds, TOP_CUT for the top cut and elimination brackets. */
    val stage: MatchStage,
    /** 1-based round within the stage. */
    val round: Int,
    val table: Int,
    /** Position in the bracket round (0-based), used to advance winners. */
    val bracketSlot: Int = 0,
    val thirdPlace: Boolean = false,
    val playerAId: Long,
    val playerBId: Long? = null,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    /** True when the score was typed in (final-score-only events, or the organizer overriding). */
    val manual: Boolean = false,
    /** My personal match for this pairing, when I'm one of the players. */
    val matchId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A battle in a pairing scored by the organizer (only when scoring = BATTLES and I'm not playing it). */
@Serializable
@Entity(tableName = "event_battles", indices = [Index("pairingId")])
data class EventBattle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pairingId: Long,
    val scorer: BattleSide,
    val finish: Finish? = null,
    val points: Int = 0,
    val comboText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "parts")
data class Part(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: PartKind,
    val name: String,
    /** Short code used in combo names: ratchet "9-60", bit "GF", assist blade "S". */
    val code: String = "",
    val type: BeyType? = null,
    val leftSpin: Boolean = false,
    val photoPath: String? = null,
    val copies: Int = 1,
    val weight: String = "",
    val note: String = "",
    val line: ProductLine? = null,
    /** "Got it from": a shop, a trade, a starter set. */
    val source: String = "",
    /** Blades only: the ratchet is part of the blade (e.g. Bullet Griffon), so combos skip the ratchet slot. */
    @ColumnInfo(defaultValue = "0") val builtInRatchet: Boolean = false,
    /** Ratchets only: the bit is part of it (Turbo, Operate), so combos skip the bit slot. */
    @ColumnInfo(defaultValue = "0") val builtInBit: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Shape comes from which ids are set (see [shape]): basic uses [bladeId]; CX uses lock chip + main blade +
 * assist blade; CX Metal uses lock chip + metal blade + over blade + assist blade. [ratchetId] is null when the
 * blade has a built-in ratchet, [bitId] is null when the ratchet has a built-in bit.
 */
@Serializable
@Entity(tableName = "combos")
data class Combo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bladeId: Long? = null,
    val lockChipId: Long? = null,
    val mainBladeId: Long? = null,
    val metalBladeId: Long? = null,
    val overBladeId: Long? = null,
    val assistBladeId: Long? = null,
    val ratchetId: Long? = null,
    val bitId: Long? = null,
    val type: BeyType,
    val favorite: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val combo1Id: Long? = null,
    val combo2Id: Long? = null,
    val combo3Id: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "tournaments")
data class Tournament(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val location: String = "",
    val format: String = "3on3",
    val swissRounds: Int = 4,
    /** 0, 4, 8 or 16 players make the top cut. */
    val topCut: Int = 0,
    val playerCount: Int = 0,
    val deckId: Long? = null,
    val placement: Int? = null,
    val notes: String = "",
    val status: TournamentStatus = TournamentStatus.PLANNED,
    val createdAt: Long = System.currentTimeMillis(),
    // Organizer mode. For a tournament I only played in, these keep their defaults.
    @ColumnInfo(defaultValue = "0") val organizer: Boolean = false,
    val eventFormat: EventFormat? = null,
    @ColumnInfo(defaultValue = "BATTLES") val scoring: EventScoring = EventScoring.BATTLES,
    @ColumnInfo(defaultValue = "SETUP") val stage: EventStage = EventStage.SETUP,
    @ColumnInfo(defaultValue = "4") val pointsToWin: Int = 4,
    @ColumnInfo(defaultValue = "0") val thirdPlaceMatch: Boolean = false,
    /** Points a bye counts for in point difference (a bye is a win, e.g. 4–0). */
    @ColumnInfo(defaultValue = "4") val byeScore: Int = 4,
    /** Single elimination: random seeding instead of registration order. */
    @ColumnInfo(defaultValue = "0") val randomSeeding: Boolean = false,
)

/** One match against one opponent. Played with a deck (3on3) or a single combo. Casual when [tournamentId] is null. */
@Serializable
@Entity(tableName = "matches", indices = [Index("tournamentId")])
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opponent: String,
    val tournamentId: Long? = null,
    val round: String = "",
    val stage: MatchStage? = null,
    val deckId: Long? = null,
    val comboId: Long? = null,
    val pointsToWin: Int = 4,
    val finished: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
)

/** One battle inside a match: who scored, how, and with which of your combos. */
@Serializable
@Entity(tableName = "battle_points", indices = [Index("matchId")])
data class BattlePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val myComboId: Long? = null,
    val opponentCombo: String = "",
    /** The type of the combo the opponent spun this battle, if logged. Feeds "vs opponent type" on Stats. */
    val opponentType: BeyType? = null,
    val scorer: Scorer,
    val finish: Finish? = null,
    val points: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
