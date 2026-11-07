package com.bxtracker.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.EventFormat
import com.bxtracker.app.data.EventPlayer
import com.bxtracker.app.data.EventScoring
import com.bxtracker.app.data.EventStage
import com.bxtracker.app.data.MatchStage
import com.bxtracker.app.data.NextStep
import com.bxtracker.app.data.Pairing
import com.bxtracker.app.data.PairingResult
import com.bxtracker.app.data.PairingStatus
import com.bxtracker.app.data.Standing
import com.bxtracker.app.data.Tournament
import com.bxtracker.app.data.TournamentEngine
import com.bxtracker.app.data.TournamentEngine.EventView
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.ordinal
import com.bxtracker.app.ui.ordinalText
import com.bxtracker.app.ui.pickedDay
import com.bxtracker.app.ui.theme.lossColor
import com.bxtracker.app.ui.theme.warnColor
import com.bxtracker.app.ui.theme.winColor
import com.bxtracker.app.ui.uiLocale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * Organizer mode: the running event (screens 17, 18 and 19 as tabs). Every rule (who wins, standings, next step,
 * seeding, placings) comes from TournamentEngine; this file only shows it and calls the view model.
 */

private enum class OrgEventTab(val label: Int) {
    ROUND(R.string.org_event_tab_round),
    STANDINGS(R.string.org_event_tab_standings),
    BRACKET(R.string.org_event_tab_bracket),
}

@Composable
fun EventScreen(
    vm: AppViewModel,
    tournamentId: Long,
    onBack: () -> Unit,
    onScoreBattles: (pairingId: Long) -> Unit,
    onScoreMine: (matchId: Long) -> Unit,
    onPlayers: () -> Unit,
    onMyResults: () -> Unit,
) {
    val context = LocalContext.current
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val players by vm.eventPlayers.collectAsStateWithLifecycle()
    val pairings by vm.pairings.collectAsStateWithLifecycle()
    val battles by vm.eventBattles.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val t = tournaments.firstOrNull { it.id == tournamentId }
    if (t == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.org_event_title), onBack = onBack) }) { pad ->
            Column(Modifier.padding(pad)) {}
        }
        return
    }
    val view = remember(t, players, pairings, battles, matches, points) {
        TournamentEngine.view(t, players, pairings, battles, matches, points)
    }
    val me = view.me
    val cut = cutSize(view)
    val fmt = t.eventFormat ?: EventFormat.SWISS_TOP_CUT
    val hasBracket = (fmt == EventFormat.SWISS_TOP_CUT && t.topCut > 0) || fmt == EventFormat.SINGLE_ELIM
    val tabs = if (hasBracket) OrgEventTab.entries.toList() else listOf(OrgEventTab.ROUND, OrgEventTab.STANDINGS)
    val editable = t.stage != EventStage.DONE

    var tab by rememberSaveable(t.id) {
        mutableStateOf(if (t.stage == EventStage.BRACKET && hasBracket) OrgEventTab.BRACKET else OrgEventTab.ROUND)
    }
    // Jump to the bracket when it starts while this screen is open (not on every recomposition or rotation).
    var seenStage by rememberSaveable(t.id) { mutableStateOf(t.stage) }
    LaunchedEffect(t.stage) {
        if (t.stage != seenStage) {
            if (t.stage == EventStage.BRACKET && hasBracket) tab = OrgEventTab.BRACKET
            seenStage = t.stage
        }
    }
    val shown = if (tab in tabs) tab else OrgEventTab.ROUND

    var finalFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var swapFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Stops a double tap from pairing the same round twice before the database catches up.
    var advancing by remember { mutableStateOf(false) }
    LaunchedEffect(view.next) { advancing = false }
    LaunchedEffect(advancing) {
        if (advancing) {
            delay(2500)
            advancing = false
        }
    }

    fun involvesMe(r: PairingResult) = me != null && r.involves(me.id)

    /** Typed-in scores (or final-score-only events) are edited in the dialog; everything else opens a scorer. */
    fun opensDialog(r: PairingResult) =
        r.pairing.manual || (t.scoring == EventScoring.FINAL_SCORE_ONLY && !involvesMe(r))

    fun score(r: PairingResult) {
        val p = r.pairing
        when {
            opensDialog(r) -> finalFor = p.id
            involvesMe(r) -> vm.matchForPairing(p) { onScoreMine(it) }
            else -> onScoreBattles(p.id)
        }
    }

    fun swappable(r: PairingResult) = editable && !r.isBye && r.status == PairingStatus.NOT_STARTED &&
        r.battles == 0 && !r.pairing.manual && r.pairing.matchId == null

    fun share() {
        val locale = context.uiLocale()
        val placed = view.players.filter { it.finalPlacing != null }.sortedBy { it.finalPlacing }
        val text = buildString {
            appendLine(t.name)
            appendLine(
                listOf(
                    pickedDay(t.date, locale),
                    t.location,
                    formatSummary(context, t, cut),
                    context.resources.getQuantityString(R.plurals.org_event_players_count, view.players.size, view.players.size),
                ).filter { it.isNotBlank() }.joinToString(" · ")
            )
            placed.firstOrNull { it.finalPlacing == 1 }?.let { appendLine(context.getString(R.string.org_event_share_champion, it.name)) }
            if (placed.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.org_event_final_placings))
                placed.take(8).forEach { pl -> appendLine("${ordinal(pl.finalPlacing ?: 0, locale)} ${pl.name}") }
            }
            me?.finalPlacing?.let { mine ->
                appendLine()
                appendLine(
                    context.getString(
                        R.string.org_event_share_mine,
                        context.getString(R.string.placing_of, ordinal(mine, locale), view.players.size),
                    )
                )
            }
        }.trim()
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.org_event_share)))
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(t.name, subtitle = eventSubtitle(view, cut), onBack = onBack, actions = {
                IconButton(onClick = onPlayers) {
                    Icon(Icons.Outlined.Groups, contentDescription = stringResource(R.string.org_event_players))
                }
                if (t.stage == EventStage.DONE) {
                    IconButton(onClick = ::share) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.org_event_share))
                    }
                }
                if (me != null) {
                    IconButton(onClick = onMyResults) {
                        Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.org_event_my_results))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.org_event_cd_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.org_event_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            })
        },
        bottomBar = {
            val footer = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp)
            if (t.stage == EventStage.SETUP) {
                Button(onClick = onPlayers, modifier = footer) {
                    Text(stringResource(R.string.org_event_add_players), fontWeight = FontWeight.Bold)
                }
            } else {
                val next = view.next
                if (next !is NextStep.Done) {
                    Button(
                        onClick = { advancing = true; vm.advanceEvent(t.id) },
                        enabled = next !is NextStep.Waiting && !advancing,
                        modifier = footer,
                    ) {
                        Text(nextStepLabel(view, next, cut), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    tabs.forEachIndexed { i, each ->
                        SegmentedButton(
                            selected = shown == each,
                            onClick = { tab = each },
                            shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                            icon = {},
                        ) { Text(stringResource(each.label), maxLines = 1) }
                    }
                }
            }
            when (shown) {
                OrgEventTab.ROUND -> roundTab(
                    v = view,
                    editable = editable,
                    onPlayers = onPlayers,
                    swappable = ::swappable,
                    opensDialog = ::opensDialog,
                    onScore = ::score,
                    onFinalScore = { finalFor = it.pairing.id },
                    onClear = { vm.clearFinalScore(it.pairing) },
                    onSwap = { swapFor = it.pairing.id },
                )
                OrgEventTab.STANDINGS -> standingsTab(view, cut, editable) { p, dropped -> vm.setDropped(p, dropped) }
                OrgEventTab.BRACKET -> bracketTab(view, cut, editable, ::score)
            }
        }
    }

    finalFor?.let { id ->
        val r = view.results.firstOrNull { it.pairing.id == id }
        if (r != null && !r.isBye) {
            FinalScoreDialog(
                playerA = view.player(r.pairing.playerAId)?.name ?: stringResource(R.string.org_event_tbd),
                playerB = view.player(r.pairing.playerBId)?.name ?: stringResource(R.string.org_event_tbd),
                initialA = r.scoreA,
                initialB = r.scoreB,
                pointsToWin = t.pointsToWin,
                onDismiss = { finalFor = null },
                onSave = { a, b -> vm.setFinalScore(r.pairing, a, b); finalFor = null },
            )
        }
    }
    swapFor?.let { id ->
        val target = view.currentResults.firstOrNull { it.pairing.id == id && swappable(it) }
        if (target != null) {
            SwapDialog(
                v = view,
                target = target,
                candidates = view.currentResults.filter { it.pairing.id != id && swappable(it) },
                onDismiss = { swapFor = null },
            ) { firstIsA, second, secondIsA ->
                vm.swapPlayers(target.pairing, firstIsA, second, secondIsA)
                swapFor = null
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.org_event_delete_title, t.name)) },
            text = { Text(stringResource(R.string.org_event_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteEvent(t); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// Final score dialog

/** Two steppers for a typed-in final score. Save only when the engine says the score has a winner. */
@Composable
fun FinalScoreDialog(
    playerA: String,
    playerB: String,
    initialA: Int,
    initialB: Int,
    pointsToWin: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    val max = pointsToWin + 5
    var a by rememberSaveable { mutableIntStateOf(initialA.coerceIn(0, max)) }
    var b by rememberSaveable { mutableIntStateOf(initialB.coerceIn(0, max)) }
    val winner = TournamentEngine.winner(a, b, pointsToWin, 1L, 2L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.org_event_final_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    pluralStringResource(R.plurals.org_event_first_to, pointsToWin, pointsToWin),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScoreStepperRow(playerA, a, { a = it }, max)
                ScoreStepperRow(playerB, b, { b = it }, max)
                Text(
                    when (winner) {
                        1L -> stringResource(R.string.org_event_final_wins, playerA, a, b)
                        2L -> stringResource(R.string.org_event_final_wins, playerB, b, a)
                        else -> pluralStringResource(R.plurals.org_event_final_hint, pointsToWin, pointsToWin)
                    },
                    color = if (winner != null) winColor() else warnColor(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(a, b) }, enabled = winner != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ScoreStepperRow(name: String, value: Int, onChange: (Int) -> Unit, max: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Stepper(value, onChange, min = 0, max = max, label = name)
    }
}

// Header and footer

/** How many players make (or made) the top cut, 0 without one. Same size the engine will use for StartBracket. */
private fun cutSize(v: EventView): Int {
    val t = v.tournament
    if (t.eventFormat != EventFormat.SWISS_TOP_CUT || t.topCut < 2) return 0
    val seeded = v.players.count { it.seed != null }
    if (v.bracket.isNotEmpty() && seeded > 0) return seeded
    (v.next as? NextStep.StartBracket)?.let { return it.size }
    val active = v.players.count { !it.dropped }
    if (active < 2) return 0
    val power = TournamentEngine.nextPowerOfTwo(active)
    return minOf(t.topCut, if (power > active) power / 2 else power).coerceAtLeast(2)
}

/** Code of a bracket round ("F", "SF", "QF", "T16", "R1"), from how many players it started with. */
private fun bracketCode(v: EventView, round: Int): String {
    val main = v.bracket.count { it.pairing.round == round && !it.pairing.thirdPlace }
    return TournamentEngine.bracketLabel(main * 2, round)
}

private fun formatSummary(context: Context, t: Tournament, cut: Int): String = when (t.eventFormat) {
    EventFormat.SINGLE_ELIM -> context.getString(R.string.org_event_format_elim)
    EventFormat.ROUND_ROBIN -> context.getString(R.string.org_event_format_rr)
    else -> if (cut > 0) context.resources.getQuantityString(R.plurals.org_event_format_swiss_cut, t.swissRounds, t.swissRounds, cut)
    else context.resources.getQuantityString(R.plurals.org_event_format_swiss, t.swissRounds, t.swissRounds)
}

@Composable
private fun eventSubtitle(v: EventView, cut: Int): String {
    val t = v.tournament
    val current = v.current
    return when {
        t.stage == EventStage.DONE -> stringResource(R.string.org_event_sub_done)
        current == null -> stringResource(R.string.org_event_sub_setup)
        current.first == MatchStage.SWISS -> when {
            t.eventFormat == EventFormat.ROUND_ROBIN -> stringResource(R.string.org_event_sub_rr, current.second, v.totalRounds)
            cut > 0 -> stringResource(R.string.org_event_sub_swiss_cut, current.second, v.totalRounds, cut)
            else -> stringResource(R.string.org_event_sub_swiss, current.second, v.totalRounds)
        }
        t.eventFormat == EventFormat.SINGLE_ELIM -> stringResource(R.string.org_event_sub_elim, bracketCode(v, current.second))
        else -> stringResource(R.string.org_event_sub_top, cut, bracketCode(v, current.second))
    }
}

@Composable
private fun nextStepLabel(v: EventView, next: NextStep, cut: Int): String = when (next) {
    is NextStep.Waiting -> waitingLabel(v, next.remaining, cut)
    is NextStep.PairRound -> stringResource(R.string.org_event_pair_round, next.round)
    is NextStep.StartBracket ->
        if (v.tournament.eventFormat == EventFormat.SINGLE_ELIM) stringResource(R.string.org_event_start_bracket)
        else stringResource(R.string.org_event_start_top, next.size)
    is NextStep.NextBracketRound -> stringResource(
        when (next.label) {
            "F" -> R.string.org_event_pair_final
            "SF" -> R.string.org_event_pair_semis
            "QF" -> R.string.org_event_pair_quarters
            "T16" -> R.string.org_event_pair_t16
            else -> R.string.org_event_pair_next
        }
    )
    NextStep.Finish -> stringResource(R.string.org_event_finish)
    NextStep.Done -> ""
}

/** "Finish 2 matches to pair round 3": what comes after the current round follows the same order as NextStep. */
@Composable
private fun waitingLabel(v: EventView, n: Int, cut: Int): String {
    val current = v.current ?: return pluralStringResource(R.plurals.org_event_wait_finish, n, n)
    return when {
        current.first == MatchStage.SWISS && current.second < v.totalRounds ->
            pluralStringResource(R.plurals.org_event_wait_round, n, n, current.second + 1)
        current.first == MatchStage.SWISS && cut > 0 -> pluralStringResource(R.plurals.org_event_wait_cut, n, n, cut)
        current.first == MatchStage.SWISS -> pluralStringResource(R.plurals.org_event_wait_finish, n, n)
        v.currentResults.count { !it.pairing.thirdPlace } <= 1 -> pluralStringResource(R.plurals.org_event_wait_finish, n, n)
        else -> pluralStringResource(R.plurals.org_event_wait_bracket, n, n)
    }
}

@Composable
private fun roundTitle(v: EventView, stage: MatchStage, round: Int): String = when {
    stage == MatchStage.TOP_CUT -> bracketRoundName(bracketCode(v, round), round)
    v.tournament.eventFormat == EventFormat.ROUND_ROBIN -> stringResource(R.string.org_event_round_n, round)
    else -> stringResource(R.string.org_event_swiss_round_n, round)
}

/** "Semifinals · SF". Round codes stay in English; R1, R2… become "Round 1". */
@Composable
private fun bracketRoundName(code: String, round: Int): String {
    val res = when (code) {
        "F" -> R.string.org_event_round_final
        "SF" -> R.string.org_event_round_semis
        "QF" -> R.string.org_event_round_quarters
        "T16" -> R.string.org_event_round_t16
        else -> null
    }
    return if (res != null) "${stringResource(res)} · $code" else stringResource(R.string.org_event_round_n, round)
}

@Composable
private fun scoreLabel(r: PairingResult, isFinal: Boolean): String = when {
    isFinal && !r.pairing.thirdPlace ->
        stringResource(if (r.status == PairingStatus.LIVE) R.string.org_event_resume_final else R.string.org_event_score_final)
    r.status == PairingStatus.LIVE -> stringResource(R.string.org_event_resume_scoring)
    else -> stringResource(R.string.org_event_score_match)
}

// Round tab (screen 17)

private fun LazyListScope.roundTab(
    v: EventView,
    editable: Boolean,
    onPlayers: () -> Unit,
    swappable: (PairingResult) -> Boolean,
    opensDialog: (PairingResult) -> Boolean,
    onScore: (PairingResult) -> Unit,
    onFinalScore: (PairingResult) -> Unit,
    onClear: (PairingResult) -> Unit,
    onSwap: (PairingResult) -> Unit,
) {
    if (v.tournament.stage == EventStage.DONE) item(key = "placings") { PlacingsCard(v) }
    val current = v.current
    if (current == null) {
        item(key = "empty") {
            EmptyState(
                Icons.Outlined.Groups,
                stringResource(R.string.org_event_no_round_title),
                stringResource(R.string.org_event_no_round_body),
                stringResource(R.string.org_event_players),
                onPlayers,
            )
        }
        return
    }
    val list = v.currentResults.sortedBy { it.pairing.table }
    val anySwappable = editable && list.any(swappable)
    item(key = "roundhead") { RoundHeader(v, current, anySwappable) }
    items(list, key = { "p${it.pairing.id}" }) { r ->
        PairingCard(
            v = v,
            r = r,
            editable = editable,
            swappable = swappable(r),
            opensDialog = opensDialog(r),
            onScore = { onScore(r) },
            onFinalScore = { onFinalScore(r) },
            onClear = { onClear(r) },
            onSwap = { onSwap(r) },
        )
    }
}

@Composable
private fun RoundHeader(v: EventView, current: Pair<MatchStage, Int>, showSwapHint: Boolean) {
    val total = v.currentResults.size
    val done = v.currentResults.count { it.status == PairingStatus.DONE }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(roundTitle(v, current.first, current.second), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.org_event_progress, done, total), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        if (showSwapHint) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.org_event_swap_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PairingCard(
    v: EventView,
    r: PairingResult,
    editable: Boolean,
    swappable: Boolean,
    opensDialog: Boolean,
    onScore: () -> Unit,
    onFinalScore: () -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
) {
    val p = r.pairing
    val live = r.status == PairingStatus.LIVE
    val shape = RoundedCornerShape(16.dp)
    val swapLabel = stringResource(R.string.org_event_swap_players)
    var menu by remember { mutableStateOf(false) }
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(shape)
            .then(if (swappable) Modifier.clickable(onClickLabel = swapLabel, onClick = onSwap) else Modifier),
        border = if (live) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.org_event_table_n, p.table) +
                    if (p.thirdPlace) " · " + stringResource(R.string.org_event_third_place) else "",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            StatusPill(r)
            if (editable && !r.isBye) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.org_event_cd_match_menu, p.table))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (swappable) {
                            DropdownMenuItem(text = { Text(swapLabel) }, onClick = { menu = false; onSwap() })
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.org_event_enter_final)) },
                            onClick = { menu = false; onFinalScore() },
                        )
                        if (p.manual) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.org_event_clear_typed)) },
                                onClick = { menu = false; onClear() },
                            )
                        }
                        if (r.status == PairingStatus.DONE && !opensDialog) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.org_event_open_scoring)) },
                                onClick = { menu = false; onScore() },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(44.dp))
            }
        }
        PairingLines(v, r, withRecord = true, withSeed = false)
        if (p.manual) {
            Text(stringResource(R.string.org_event_typed_score), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editable && !r.isBye && r.status != PairingStatus.DONE) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onScore, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(stringResource(if (live) R.string.org_event_resume_scoring else R.string.org_event_score_match))
            }
        }
    }
}

/** Both players of a pairing (or the player and "Bye"), with scores once the match has started. */
@Composable
private fun PairingLines(v: EventView, r: PairingResult, withRecord: Boolean, withSeed: Boolean) {
    val p = r.pairing
    val started = r.status != PairingStatus.NOT_STARTED
    val decided = r.winnerId != null
    val a = v.player(p.playerAId)
    PlayerLine(
        player = a,
        sub = if (withRecord && a != null) v.record(a.id).let { (w, l) -> stringResource(R.string.record_wl, w, l) } else null,
        score = if (started) r.scoreA else null,
        winner = decided && r.winnerId == p.playerAId,
        decided = decided,
        seed = if (withSeed) a?.seed else null,
    )
    if (r.isBye) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.org_event_bye_line), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    } else {
        val b = v.player(p.playerBId)
        PlayerLine(
            player = b,
            sub = if (withRecord && b != null) v.record(b.id).let { (w, l) -> stringResource(R.string.record_wl, w, l) } else null,
            score = if (started) r.scoreB else null,
            winner = decided && r.winnerId == p.playerBId,
            decided = decided,
            seed = if (withSeed) b?.seed else null,
        )
    }
}

@Composable
private fun PlayerLine(player: EventPlayer?, sub: String?, score: Int?, winner: Boolean, decided: Boolean, seed: Int?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (seed != null) {
            SeedBox(seed)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            NameWithTags(
                name = player?.name ?: stringResource(R.string.org_event_tbd),
                isMe = player?.isMe == true,
                dropped = player?.dropped == true,
                bold = winner,
            )
            if (sub != null) Text(sub, fontSize = 12.sp, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (score != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$score",
                fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                color = when {
                    winner -> winColor()
                    decided -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun NameWithTags(name: String, isMe: Boolean, dropped: Boolean = false, bold: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            modifier = Modifier.weight(1f, fill = false),
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (isMe) {
            Spacer(Modifier.width(6.dp))
            Tag(stringResource(R.string.org_event_you), MaterialTheme.colorScheme.primary)
        }
        if (dropped) {
            Spacer(Modifier.width(6.dp))
            Tag(stringResource(R.string.org_event_dropped), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

@Composable
private fun StatusPill(r: PairingResult) {
    val (text, color) = when {
        r.isBye -> stringResource(R.string.org_event_status_bye) to winColor()
        r.status == PairingStatus.DONE -> stringResource(R.string.org_event_status_done) to winColor()
        r.status == PairingStatus.LIVE ->
            (if (r.battles > 0) stringResource(R.string.org_event_live_battle, r.battles + 1) else stringResource(R.string.org_event_status_live)) to
                MaterialTheme.colorScheme.primary
        else -> stringResource(R.string.org_event_status_not_started) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

@Composable
private fun SeedBox(seed: Int) {
    val description = stringResource(R.string.org_event_cd_seed, seed)
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text("$seed", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}

@Composable
private fun SwapDialog(
    v: EventView,
    target: PairingResult,
    candidates: List<PairingResult>,
    onDismiss: () -> Unit,
    onSwap: (firstIsA: Boolean, second: Pairing, secondIsA: Boolean) -> Unit,
) {
    var moveA by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val p = target.pairing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.org_event_swap_title, p.table)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val moving = moveA
                if (moving == null) {
                    Text(stringResource(R.string.org_event_swap_pick_player), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    listOf(true to p.playerAId, false to p.playerBId).forEach { (isA, id) ->
                        val pl = v.player(id)
                        if (pl != null) ChoiceLine(pl.name, null) { moveA = isA }
                    }
                } else {
                    val name = v.player(if (moving) p.playerAId else p.playerBId)?.name.orEmpty()
                    Text(stringResource(R.string.org_event_swap_pick_other, name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.org_event_swap_none))
                    }
                    candidates.sortedBy { it.pairing.table }.forEach { c ->
                        listOf(true, false).forEach { isA ->
                            val pl = v.player(if (isA) c.pairing.playerAId else c.pairing.playerBId)
                            val other = v.player(if (isA) c.pairing.playerBId else c.pairing.playerAId)
                            if (pl != null) {
                                ChoiceLine(pl.name, stringResource(R.string.org_event_swap_candidate, c.pairing.table, other?.name.orEmpty())) {
                                    onSwap(moving, c.pairing, isA)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = {
            if (moveA != null) TextButton(onClick = { moveA = null }) { Text(stringResource(R.string.back)) }
        },
    )
}

@Composable
private fun ChoiceLine(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Standings tab (screen 18)

private fun pct(value: Double): Int = (value * 100).roundToInt()

private fun signed(n: Int): String = if (n > 0) "+$n" else "$n"

/** Players who make (or made) the cut: the seeded players once the bracket exists, else the top active players. */
private fun cutIds(v: EventView, cut: Int): Set<Long> {
    if (cut <= 0) return emptySet()
    val seeded = v.players.filter { it.seed != null }
    if (v.bracket.isNotEmpty() && seeded.isNotEmpty()) return seeded.map { it.id }.toSet()
    return v.standings.filter { !it.player.dropped }.take(cut).map { it.player.id }.toSet()
}

private fun LazyListScope.standingsTab(v: EventView, cut: Int, editable: Boolean, onDrop: (EventPlayer, Boolean) -> Unit) {
    if (v.tournament.eventFormat == EventFormat.SINGLE_ELIM) {
        item(key = "elimnote") { NoteCard(stringResource(R.string.org_event_elim_standings_note)) }
        item(key = "sthead") { StandingsHeader(full = false) }
        val ordered = v.players.sortedWith(
            compareBy<EventPlayer> { it.finalPlacing ?: Int.MAX_VALUE }.thenBy { it.seed ?: Int.MAX_VALUE }.thenBy { it.createdAt }
        )
        ordered.forEachIndexed { i, pl ->
            item(key = "s${pl.id}") {
                val (w, l) = v.record(pl.id)
                StandingRow(pl.finalPlacing ?: pl.seed ?: (i + 1), pl, "$w–$l", null, null, editable) { onDrop(pl, it) }
            }
        }
        return
    }
    if (v.standings.isEmpty()) {
        item(key = "stempty") {
            EmptyState(Icons.Outlined.Groups, stringResource(R.string.org_event_no_players_title), stringResource(R.string.org_event_no_players_body))
        }
        return
    }
    val inCut = cutIds(v, cut)
    val showCut = cut > 0 && inCut.isNotEmpty()
    val lastInId = if (showCut) v.standings.lastOrNull { it.player.id in inCut }?.player?.id else null
    val lastRowId = v.standings.last().player.id
    item(key = "sthead") { StandingsHeader(full = true) }
    v.standings.forEach { s ->
        item(key = "s${s.player.id}") {
            StandingRow(s.rank, s.player, "${s.wins}–${s.losses}", s.diff, pct(s.opponentWinPct), editable) { onDrop(s.player, it) }
        }
        if (s.player.id == lastInId && s.player.id != lastRowId) item(key = "cutline") { CutLine(cut) }
    }
    val roundsOver = v.bracket.isNotEmpty() || v.next is NextStep.StartBracket
    item(key = "tiebreak") { TiebreakBox(v, inCut, showMissed = showCut && roundsOver) }
}

@Composable
private fun NoteCard(text: String) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StandingsHeader(full: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.org_event_col_rank), Modifier.width(32.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(stringResource(R.string.org_event_col_player), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Text(stringResource(R.string.org_event_col_wl), Modifier.width(52.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.End)
        if (full) {
            Text(stringResource(R.string.org_event_col_diff), Modifier.width(52.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.End)
            Text(stringResource(R.string.org_event_col_opp), Modifier.width(56.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.End)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandingRow(rank: Int, player: EventPlayer, wl: String, diff: Int?, opp: Int?, editable: Boolean, onDrop: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.org_event_cd_player_menu, player.name)
    Box {
        Row(
            Modifier.fillMaxWidth()
                .background(if (player.isMe) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .then(
                    if (editable) Modifier.combinedClickable(
                        onClickLabel = menuLabel,
                        onLongClickLabel = menuLabel,
                        onLongClick = { menu = true },
                        onClick = { menu = true },
                    ) else Modifier
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .alpha(if (player.dropped) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$rank", Modifier.width(32.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            NameWithTags(player.name, player.isMe, player.dropped, modifier = Modifier.weight(1f))
            Text(wl, Modifier.width(52.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End)
            if (diff != null) {
                Text(
                    signed(diff), Modifier.width(52.dp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End,
                    color = when {
                        diff > 0 -> winColor()
                        diff < 0 -> lossColor()
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (opp != null) {
                Text("$opp%", Modifier.width(56.dp), fontFamily = Mono, fontSize = 14.sp, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (player.dropped) R.string.org_event_undo_drop else R.string.org_event_drop)) },
                onClick = { menu = false; onDrop(!player.dropped) },
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CutLine(n: Int) {
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(2.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.org_event_cut_line, n), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TiebreakBox(v: EventView, inCut: Set<Long>, showMissed: Boolean) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.org_event_tb_title), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        listOf(
            R.string.org_event_tb_1, R.string.org_event_tb_2, R.string.org_event_tb_3, R.string.org_event_tb_4, R.string.org_event_tb_5,
        ).forEachIndexed { i, res ->
            Text(stringResource(R.string.org_event_tb_line, i + 1, stringResource(res)), fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.org_event_tb_bye, v.tournament.byeScore), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showMissed) {
            missedCutText(v, inCut)?.let { text ->
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.org_event_cut_title), fontWeight = FontWeight.Bold)
                Text(text, fontSize = 13.sp)
            }
        }
    }
}

/** Why the first player below the line missed it: the first tiebreaker where they differ from the last one in. */
@Composable
private fun missedCutText(v: EventView, inCut: Set<Long>): String? {
    val lastIn: Standing = v.standings.lastOrNull { it.player.id in inCut } ?: return null
    val out: Standing = v.standings.firstOrNull { it.player.id !in inCut && !it.player.dropped }
        ?: return stringResource(R.string.org_event_cut_everyone)
    val a = out.player.name
    val b = lastIn.player.name
    val h2h = v.results.firstOrNull {
        it.pairing.stage == MatchStage.SWISS && !it.pairing.thirdPlace && !it.isBye && it.status == PairingStatus.DONE &&
            it.involves(out.player.id) && it.involves(lastIn.player.id)
    }?.winnerId
    return when {
        out.wins != lastIn.wins -> stringResource(R.string.org_event_cut_missed_wins, a, b, lastIn.rank, out.wins, lastIn.wins)
        out.diff != lastIn.diff -> stringResource(R.string.org_event_cut_missed_diff, a, b, lastIn.rank, signed(out.diff), signed(lastIn.diff))
        Math.round(out.opponentWinPct * 10000) != Math.round(lastIn.opponentWinPct * 10000) ->
            stringResource(R.string.org_event_cut_missed_opp, a, b, lastIn.rank, pct(out.opponentWinPct), pct(lastIn.opponentWinPct))
        h2h == lastIn.player.id -> stringResource(R.string.org_event_cut_missed_h2h, a, b, lastIn.rank)
        out.pointsFor != lastIn.pointsFor -> stringResource(R.string.org_event_cut_missed_points, a, b, lastIn.rank, out.pointsFor, lastIn.pointsFor)
        else -> stringResource(R.string.org_event_cut_missed_order, a, b, lastIn.rank)
    }
}

// Bracket tab (screen 19)

private fun LazyListScope.bracketTab(v: EventView, cut: Int, editable: Boolean, onScore: (PairingResult) -> Unit) {
    item(key = "seednote") { SeedingNote(v, cut) }
    val bracket = v.bracket
    if (bracket.isEmpty()) {
        item(key = "nobracket") {
            EmptyState(
                Icons.Outlined.AccountTree,
                stringResource(R.string.org_event_bracket_empty_title),
                stringResource(
                    if (v.tournament.eventFormat == EventFormat.SINGLE_ELIM) R.string.org_event_bracket_empty_elim
                    else R.string.org_event_bracket_empty_body
                ),
            )
        }
        return
    }
    val done = v.tournament.stage == EventStage.DONE
    val rounds = bracket.filter { !it.pairing.thirdPlace }.groupBy { it.pairing.round }.toSortedMap()
    val third = bracket.firstOrNull { it.pairing.thirdPlace }
    val lastRound = rounds.lastKey()
    val lastList = rounds.getValue(lastRound)
    val finalMatch = lastList.singleOrNull()
    if (finalMatch != null && finalMatch.winnerId != null) item(key = "champion") { ChampionCard(v, finalMatch) }
    if (done) item(key = "placings") { PlacingsCard(v) }
    rounds.forEach { (round, list) ->
        val code = TournamentEngine.bracketLabel(list.size * 2, round)
        val isCurrent = v.current == (MatchStage.TOP_CUT to round)
        val isFinal = list.size == 1
        item(key = "bh$round") { SectionLabel(bracketRoundName(code, round), Modifier.padding(top = 8.dp)) }
        items(list.sortedBy { it.pairing.bracketSlot }, key = { "b${it.pairing.id}" }) { r ->
            val canScore = editable && isCurrent && !r.isBye && r.status != PairingStatus.DONE
            BracketCard(v, r, if (canScore) scoreLabel(r, isFinal) else null) { onScore(r) }
        }
        if (isFinal && third != null) {
            val thirdCurrent = v.current == (MatchStage.TOP_CUT to third.pairing.round)
            item(key = "thirdh") { SectionLabel(stringResource(R.string.org_event_third_place), Modifier.padding(top = 8.dp)) }
            item(key = "b${third.pairing.id}") {
                val canScore = editable && thirdCurrent && third.status != PairingStatus.DONE
                BracketCard(v, third, if (canScore) scoreLabel(third, isFinal = false) else null) { onScore(third) }
            }
        }
    }
    if (!done) {
        // Rounds still to come, as TBD placeholders.
        var size = lastList.size
        var round = lastRound
        while (size > 1) {
            size /= 2
            round += 1
            val n = size
            val r = round
            val code = TournamentEngine.bracketLabel(n * 2, r)
            item(key = "tbdh$r") { SectionLabel(bracketRoundName(code, r), Modifier.padding(top = 8.dp)) }
            items(n, key = { i -> "tbd${r}_$i" }) { TbdCard() }
            if (n == 1 && v.tournament.thirdPlaceMatch && third == null) {
                item(key = "tbd3h") { SectionLabel(stringResource(R.string.org_event_third_place), Modifier.padding(top = 8.dp)) }
                item(key = "tbd3") { TbdCard() }
            }
        }
    }
}

@Composable
private fun SeedingNote(v: EventView, cut: Int) {
    val t = v.tournament
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(stringResource(R.string.org_event_seed_title), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val body = if (t.eventFormat == EventFormat.SINGLE_ELIM) {
            stringResource(if (t.randomSeeding) R.string.org_event_seed_random else R.string.org_event_seed_registration)
        } else {
            val size = TournamentEngine.nextPowerOfTwo((if (cut > 0) cut else t.topCut).coerceAtLeast(2))
            val pairs = TournamentEngine.bracketOrder(size).chunked(2)
                .map { pair -> stringResource(R.string.org_event_seed_pair, pair[0], pair[1]) }
                .joinToString(", ")
            stringResource(R.string.org_event_seed_standings, pairs)
        }
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (t.thirdPlaceMatch) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.org_event_seed_third), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BracketCard(v: EventView, r: PairingResult, actionLabel: String?, onAction: () -> Unit) {
    val live = r.status == PairingStatus.LIVE
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        border = if (live) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.org_event_table_n, r.pairing.table), modifier = Modifier.weight(1f),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary,
            )
            StatusPill(r)
        }
        Spacer(Modifier.height(4.dp))
        PairingLines(v, r, withRecord = false, withSeed = true)
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun TbdCard() {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        repeat(2) {
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.org_event_tbd), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChampionCard(v: EventView, finalMatch: PairingResult) {
    val winnerId = finalMatch.winnerId ?: return
    val champion = v.player(winnerId) ?: return
    val runnerUp = v.player(finalMatch.loserId)
    val gold = warnColor()
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), border = BorderStroke(1.5.dp, gold)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = gold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.org_event_champion).uppercase(),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = gold,
                )
                NameWithTags(champion.name, champion.isMe, bold = true)
                if (runnerUp != null) {
                    Text(
                        stringResource(R.string.org_event_champion_beat, runnerUp.name, finalMatch.scoreFor(winnerId), finalMatch.scoreAgainst(winnerId)),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Final placings 1st to last, champion first, my row highlighted. */
@Composable
private fun PlacingsCard(v: EventView) {
    val ordered = v.players.sortedWith(compareBy<EventPlayer> { it.finalPlacing ?: Int.MAX_VALUE }.thenBy { it.createdAt })
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(stringResource(R.string.org_event_final_placings), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ordered.forEach { pl ->
            val place = pl.finalPlacing
            val (w, l) = v.record(pl.id)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (pl.isMe) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (place != null) ordinalText(place) else "–",
                    modifier = Modifier.width(48.dp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (place == 1) warnColor() else MaterialTheme.colorScheme.secondary,
                )
                NameWithTags(pl.name, pl.isMe, pl.dropped, bold = place == 1, modifier = Modifier.weight(1f))
                Text("$w–$l", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
