package com.bxtracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.BattlePoint
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.Match
import com.bxtracker.app.data.Scorer
import com.bxtracker.app.data.StatsPeriod
import com.bxtracker.app.data.Tournament
import com.bxtracker.app.data.TournamentStatus
import com.bxtracker.app.data.battleRecord
import com.bxtracker.app.data.comboIds
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.comboRecords
import com.bxtracker.app.data.finishBreakdown
import com.bxtracker.app.data.matchOutcome
import com.bxtracker.app.data.matchRecord
import com.bxtracker.app.data.matchScore
import com.bxtracker.app.data.matchesFor
import com.bxtracker.app.data.totalRounds
import com.bxtracker.app.ui.pickedDay
import com.bxtracker.app.ui.placingText
import com.bxtracker.app.ui.shortDate
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.FinishBar
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.RecordBar
import com.bxtracker.app.ui.components.ResultBadge
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.StatTile
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.theme.lossColor
import com.bxtracker.app.ui.theme.typeTint
import com.bxtracker.app.ui.theme.winColor

private val battleTabs = listOf(R.string.battle_tab_overview, R.string.battle_tab_matches, R.string.battle_tab_tournaments)

@Composable
fun BattlesScreen(
    vm: AppViewModel,
    onQuickBattle: () -> Unit,
    onMatch: (Long) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onTournament: (Long) -> Unit,
    onNewTournament: () -> Unit,
    onOrganize: () -> Unit,
    onEvent: (Long) -> Unit,
) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }
    val tournamentMap = remember(tournaments) { tournaments.associateBy { it.id } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var startRoundFor by remember { mutableStateOf<Tournament?>(null) }

    val season = matchesFor(StatsPeriod.SEASON, matches, settings.seasonStart)
    val seasonRecord = matchRecord(season, points)
    val battle = battleRecord(points)
    val myFinishes = finishBreakdown(points, Scorer.ME)
    val theirFinishes = finishBreakdown(points, Scorer.OPPONENT)
    val best = comboRecords(points).entries
        .filter { it.value.total > 0 && comboMap.containsKey(it.key) }
        .sortedWith(compareByDescending<Map.Entry<Long, com.bxtracker.app.data.Record>> { it.value.rate }.thenByDescending { it.value.total })
        .take(3)
    val live = tournaments.filter { it.status == TournamentStatus.IN_PROGRESS }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.tab_battles),
                subtitle = if (seasonRecord.total > 0) {
                    stringResource(
                        if (settings.seasonStart != null) R.string.battle_header_season else R.string.battle_header_all_time,
                        stringResource(R.string.record_wl, seasonRecord.wins, seasonRecord.losses),
                    )
                } else null,
                actions = {
                    IconButton(onClick = onOrganize) { Icon(Icons.Outlined.Groups, contentDescription = stringResource(R.string.battle_cd_organize)) }
                    IconButton(onClick = onStats) { Icon(Icons.Outlined.BarChart, contentDescription = stringResource(R.string.battle_cd_stats)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            if (tab == 2) {
                ExtendedFloatingActionButton(
                    onClick = onNewTournament,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.tourn_new)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else if (combos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onQuickBattle,
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                    text = { Text(stringResource(R.string.battle_quick)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    battleTabs.forEachIndexed { i, name ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { tab = i },
                            shape = SegmentedButtonDefaults.itemShape(i, battleTabs.size),
                            icon = {},
                        ) { Text(stringResource(name), maxLines = 1) }
                    }
                }
            }
            when (tab) {
                0 -> {
                    items(live, key = { "live${it.id}" }) { t ->
                        if (t.organizer) {
                            OrganizingCard(t) { onEvent(t.id) }
                        } else {
                            LiveTournamentCard(
                                t, matches.filter { it.tournamentId == t.id }, points,
                                onStart = { startRoundFor = t }, onView = { onTournament(t.id) },
                            )
                        }
                    }
                    if (matches.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.EmojiEvents,
                                stringResource(R.string.battle_empty_title),
                                if (combos.isEmpty()) stringResource(R.string.battle_empty_no_combos)
                                else stringResource(R.string.battle_empty_body),
                                if (combos.isEmpty()) null else stringResource(R.string.battle_quick),
                                onQuickBattle,
                            )
                        }
                    } else {
                        item {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile("${battle.percent}%", stringResource(R.string.battle_win_rate), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                                StatTile("${matches.size}", stringResource(R.string.battle_tab_matches), Modifier.weight(1f))
                                StatTile("${points.count { it.scorer == Scorer.ME && it.finish == Finish.XTREME }}", "Xtremes", Modifier.weight(1f))
                            }
                        }
                        if (myFinishes.isNotEmpty()) {
                            item {
                                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Text(stringResource(R.string.battle_how_you_score), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    FinishBar(myFinishes)
                                    if (theirFinishes.isNotEmpty()) {
                                        val worst = theirFinishes.maxBy { it.value }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            stringResource(R.string.battle_most_against, worst.key.label, worst.value * 100 / theirFinishes.values.sum()),
                                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (best.isNotEmpty()) {
                            item {
                                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.battle_best_combos), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        TextButton(onClick = onStats) { Text(stringResource(R.string.battle_all_stats)) }
                                    }
                                    best.forEach { (id, rec) ->
                                        Spacer(Modifier.height(6.dp))
                                        Text(comboName(comboMap.getValue(id), partMap), fontSize = 14.sp)
                                        RecordBar(rec)
                                    }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel(stringResource(R.string.battle_recent_matches), Modifier.weight(1f))
                                if (matches.size > 5) TextButton(onClick = { tab = 1 }) { Text(stringResource(R.string.battle_see_all)) }
                            }
                        }
                        items(matches.take(5), key = { "r${it.id}" }) { m ->
                            MatchRow(m, tournamentMap[m.tournamentId], matchOutcome(m, matchScore(m.id, points)), matchScore(m.id, points)) { onMatch(m.id) }
                        }
                    }
                }
                1 -> {
                    if (matches.isEmpty()) {
                        item { EmptyState(Icons.Outlined.EmojiEvents, stringResource(R.string.battle_empty_title), stringResource(R.string.battle_matches_empty_body)) }
                    }
                    items(matches, key = { it.id }) { m ->
                        val s = matchScore(m.id, points)
                        MatchRow(m, tournamentMap[m.tournamentId], matchOutcome(m, s), s) { onMatch(m.id) }
                    }
                }
                else -> {
                    if (tournaments.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.EmojiEvents, stringResource(R.string.tourn_empty_title),
                                stringResource(R.string.tourn_empty_body),
                                stringResource(R.string.tourn_new), onNewTournament,
                            )
                        }
                    }
                    items(tournaments, key = { "t${it.id}" }) { t ->
                        TournamentRow(t, matchRecord(matches.filter { it.tournamentId == t.id }, points)) {
                            if (t.organizer) onEvent(t.id) else onTournament(t.id)
                        }
                    }
                }
            }
        }
    }

    startRoundFor?.let { t ->
        StartRoundDialog(vm, t, onDismiss = { startRoundFor = null }) { id ->
            startRoundFor = null
            onMatch(id)
        }
    }
}

/** An event I'm organizing that's running: rounds are run from the organizer screen. */
@Composable
private fun OrganizingCard(t: Tournament, onOpen: () -> Unit) {
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(stringResource(R.string.tourn_organizing_badge), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
        Text(t.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpen) { Text(stringResource(R.string.tourn_open_event)) }
    }
}

@Composable
private fun LiveTournamentCard(t: Tournament, tMatches: List<Match>, points: List<BattlePoint>, onStart: () -> Unit, onView: () -> Unit) {
    val rec = matchRecord(tMatches, points)
    val unfinished = tMatches.any { matchOutcome(it, matchScore(it.id, points)) == null }
    val next = tMatches.size + 1
    AppCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(stringResource(R.string.tourn_live_badge), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
        Text(t.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            stringResource(
                R.string.tourn_live_line,
                tMatches.size.coerceAtLeast(1), t.totalRounds(),
                if (t.topCut > 0) "Swiss + top ${t.topCut}" else "Swiss",
                rec.wins, rec.losses,
            ),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !unfinished) {
                Text(if (unfinished) stringResource(R.string.tourn_round_in_progress) else stringResource(R.string.tourn_start_round_n, next))
            }
            OutlinedButton(onClick = onView) { Text(stringResource(R.string.tourn_view)) }
        }
    }
}

@Composable
fun MatchRow(m: Match, tournament: Tournament?, outcome: Boolean?, score: com.bxtracker.app.data.Score, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultBadge(outcome)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.match_vs, m.opponent) + if (m.round.isNotBlank()) " · ${m.round}" else "", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                subtitle ?: listOf(tournament?.name ?: stringResource(R.string.casual), shortDate(m.startedAt)).joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Text("${score.me}–${score.opponent}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TournamentRow(t: Tournament, rec: com.bxtracker.app.data.Record, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlacingBadge(t)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                listOf(pickedDay(t.date), placingText(t) ?: stringResource(t.status.label)).joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rec.total > 0) Text("${rec.wins}–${rec.losses}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun NewMatchScreen(vm: AppViewModel, onBack: () -> Unit, onStarted: (Long) -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val defaultOpponent = stringResource(R.string.default_opponent)
    val pastOpponents = remember(matches, defaultOpponent) {
        matches.map { it.opponent }.filter { it != "Opponent" && it != defaultOpponent }.distinct().take(8)
    }

    var opponent by rememberSaveable { mutableStateOf("") }
    var round by rememberSaveable { mutableStateOf("") }
    var useDeck by rememberSaveable { mutableStateOf(decks.isNotEmpty()) }
    var deckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var comboId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pointsToWin by rememberSaveable { mutableIntStateOf(settings.pointsToWin) }
    val ready = if (useDeck) deckId != null else comboId != null

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(R.string.battle_quick), subtitle = stringResource(R.string.battle_quick_subtitle), onBack = onBack) },
        bottomBar = {
            Button(
                onClick = {
                    vm.startMatch(
                        Match(
                            opponent = opponent.trim().ifBlank { defaultOpponent },
                            round = round.trim(),
                            deckId = if (useDeck) deckId else null,
                            comboId = if (useDeck) null else comboId,
                            pointsToWin = pointsToWin,
                        ),
                        onStarted,
                    )
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(stringResource(R.string.match_start), fontWeight = FontWeight.Bold) }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(opponent, { opponent = it }, label = { Text(stringResource(R.string.match_opponent)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (pastOpponents.isNotEmpty() && opponent.isBlank()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pastOpponents.forEach { name -> FilterChip(selected = false, onClick = { opponent = name }, label = { Text(name) }) }
                        }
                    }
                    OutlinedTextField(round, { round = it }, label = { Text(stringResource(R.string.match_label_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.match_points_to_win), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Stepper(pointsToWin, { pointsToWin = it }, min = 1, max = 20, label = stringResource(R.string.match_points_unit))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            true to stringResource(R.string.match_mode_deck, "3on3"),
                            false to stringResource(R.string.match_mode_single),
                        ).forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = useDeck == value,
                                onClick = { useDeck = value },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                icon = {},
                            ) { Text(label) }
                        }
                    }
                }
            }
            if (useDeck) {
                if (decks.isEmpty()) {
                    item { Text(stringResource(R.string.match_no_decks), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(decks, key = { "d${it.id}" }) { d ->
                    ChoiceRow(selected = deckId == d.id, title = d.name, subtitle = stringResource(R.string.battle_deck_combo_count, d.comboIds().count { it != null })) { deckId = d.id }
                }
            } else {
                items(combos, key = { "c${it.id}" }) { c ->
                    ChoiceRow(selected = comboId == c.id, title = comboName(c, partMap), subtitle = c.type.label) { comboId = c.id }
                }
            }
        }
    }
}

@Composable
fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MatchScreen(vm: AppViewModel, matchId: Long, onBack: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val allPoints by vm.points.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val match = matches.firstOrNull { it.id == matchId }
    if (match == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.match_title), onBack = onBack) }) { pad -> Column(Modifier.padding(pad)) {} }
        return
    }
    val haptics = LocalHapticFeedback.current
    val defaultOpponent = stringResource(R.string.default_opponent)
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }
    val tournament = match.tournamentId?.let { id -> tournaments.firstOrNull { it.id == id } }
    val myCombos = if (match.deckId != null) {
        decks.firstOrNull { it.id == match.deckId }?.comboIds()?.mapNotNull { id -> id?.let { comboMap[it] } }.orEmpty()
    } else listOfNotNull(match.comboId?.let { comboMap[it] })
    val points = allPoints.filter { it.matchId == match.id }
    val score = matchScore(match.id, allPoints)
    val outcome = matchOutcome(match, score)

    var slot by rememberSaveable { mutableIntStateOf(0) }
    var opponentCombo by rememberSaveable { mutableStateOf("") }
    var opponentType by rememberSaveable { mutableStateOf<BeyType?>(null) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currentCombo = myCombos.getOrNull(slot) ?: myCombos.firstOrNull()

    // Remember what this opponent last played: their combo text and its type.
    LaunchedEffect(allPoints.isNotEmpty(), matches.size) {
        if (prefilled) return@LaunchedEffect
        val isDefault = match.opponent == "Opponent" || match.opponent == defaultOpponent
        val sameOpponent = matches.filter { it.opponent.equals(match.opponent, true) && !isDefault }.map { it.id }.toSet()
        val last = allPoints.lastOrNull { it.matchId in sameOpponent && (it.opponentCombo.isNotBlank() || it.opponentType != null) }
        if (last != null) {
            opponentCombo = last.opponentCombo
            opponentType = last.opponentType
        }
        prefilled = true
    }

    LaunchedEffect(outcome, match.finished) {
        val done = outcome != null
        if (done != match.finished) vm.setFinished(match, done)
    }

    fun recordPoint(scorer: Scorer, finish: Finish?) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.addPoint(
            BattlePoint(
                matchId = match.id,
                myComboId = currentCombo?.id,
                opponentCombo = opponentCombo.trim(),
                opponentType = opponentType,
                scorer = scorer,
                finish = finish,
                points = finish?.let { settings.pointsFor(it) } ?: 0,
            )
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.match_vs, match.opponent) + if (match.round.isNotBlank()) " · ${match.round}" else "",
                subtitle = listOf(
                    tournament?.name ?: stringResource(R.string.casual),
                    pluralStringResource(R.plurals.match_first_to, match.pointsToWin, match.pointsToWin),
                ).joinToString(" · "),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.undoLastPoint(match.id) }, enabled = points.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.match_cd_undo))
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.match_cd_delete)) }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreColumn(stringResource(R.string.match_you), score.me, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        Text(stringResource(R.string.match_battle_n, points.size + 1), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        ScoreColumn(match.opponent, score.opponent, lossColor(), Modifier.weight(1f))
                    }
                }
            }
            if (outcome != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = (if (outcome) winColor() else lossColor()).copy(alpha = 0.14f),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(if (outcome) R.string.match_you_won else R.string.match_you_lost, score.me, score.opponent),
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                                color = if (outcome) winColor() else lossColor(),
                            )
                            Text(stringResource(R.string.match_saved_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.done)) }
                        }
                    }
                }
            }
            if (myCombos.size > 1) {
                item {
                    Text(stringResource(R.string.match_your_combo), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        myCombos.forEachIndexed { i, c ->
                            val selected = slot == i
                            Surface(
                                onClick = { slot = i },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            ) {
                                Text(
                                    "${i + 1} · " + (c.bladeId?.let { partMap[it]?.name } ?: c.lockChipId?.let { partMap[it]?.name } ?: "?"),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                    maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            } else if (currentCombo != null) {
                item {
                    Text(stringResource(R.string.match_playing, comboName(currentCombo, partMap)), modifier = Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                OutlinedTextField(
                    value = opponentCombo,
                    onValueChange = { opponentCombo = it },
                    label = { Text(stringResource(R.string.match_their_combo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.match_their_type), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BeyType.entries.forEach { t ->
                        val tint = typeTint(t)
                        FilterChip(
                            selected = opponentType == t,
                            onClick = { opponentType = if (opponentType == t) null else t },
                            label = { Text(t.label, color = if (opponentType == t) tint.fg else Color.Unspecified) },
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FinishGrid(stringResource(R.string.match_you_scored), MaterialTheme.colorScheme.secondary, settings::pointsFor, enabled = outcome == null, modifier = Modifier.weight(1f)) { recordPoint(Scorer.ME, it) }
                    FinishGrid(stringResource(R.string.match_they_scored), lossColor(), settings::pointsFor, enabled = outcome == null, modifier = Modifier.weight(1f)) { recordPoint(Scorer.OPPONENT, it) }
                }
                OutlinedButton(
                    onClick = { recordPoint(Scorer.DRAW, null) },
                    enabled = outcome == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                ) { Text(stringResource(R.string.match_draw_button)) }
            }
            if (points.isNotEmpty()) {
                item { Text(stringResource(R.string.match_this_match), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                items(points.reversed(), key = { it.id }) { p ->
                    val index = points.indexOf(p) + 1
                    val mine = p.myComboId?.let { comboMap[it] }?.let { c -> c.bladeId?.let { partMap[it]?.name } ?: c.lockChipId?.let { partMap[it]?.name } } ?: "?"
                    val theirs = p.opponentCombo.ifBlank { p.opponentType?.label.orEmpty() }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.match_battle_short, index), fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        Text(
                            if (theirs.isNotBlank()) stringResource(R.string.match_x_vs_y, mine, theirs) else mine,
                            modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 1,
                        )
                        Text(
                            when (p.scorer) {
                                Scorer.ME -> stringResource(R.string.match_point_you, p.finish?.label.orEmpty(), p.points)
                                Scorer.OPPONENT -> stringResource(R.string.match_point_them, p.finish?.label.orEmpty(), p.points)
                                Scorer.DRAW -> stringResource(R.string.match_draw)
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = when (p.scorer) {
                                Scorer.ME -> MaterialTheme.colorScheme.secondary
                                Scorer.OPPONENT -> lossColor()
                                Scorer.DRAW -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.match_delete_title)) },
            text = { Text(stringResource(R.string.match_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteMatch(match); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun ScoreColumn(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color, maxLines = 1)
        Text(value.toString(), fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp)
    }
}

@Composable
internal fun FinishGrid(
    title: String,
    color: Color,
    pointsFor: (Finish) -> Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFinish: (Finish) -> Unit,
) {
    Column(modifier) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color)
        Spacer(Modifier.height(8.dp))
        Finish.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                row.forEach { f ->
                    Surface(
                        onClick = { onFinish(f) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = color.copy(alpha = if (enabled) 0.14f else 0.05f),
                        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f)),
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("+${pointsFor(f)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
                            Text(f.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
