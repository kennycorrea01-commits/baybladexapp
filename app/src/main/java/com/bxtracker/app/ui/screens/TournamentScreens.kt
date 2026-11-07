package com.bxtracker.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.MatchStage
import com.bxtracker.app.data.Scorer
import com.bxtracker.app.data.Tournament
import com.bxtracker.app.data.TournamentStatus
import com.bxtracker.app.data.comboIds
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.formatText
import com.bxtracker.app.data.matchOutcome
import com.bxtracker.app.data.matchRecord
import com.bxtracker.app.data.matchScore
import com.bxtracker.app.data.roundLabel
import com.bxtracker.app.data.totalRounds
import com.bxtracker.app.ui.ordinalText
import com.bxtracker.app.ui.pickedDay
import com.bxtracker.app.ui.placingText
import com.bxtracker.app.ui.shortDate
import com.bxtracker.app.ui.uiLocale
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.DateField
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.LabeledBar
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.StatTile
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.theme.warnColor
import java.util.Calendar
import java.util.TimeZone

/** Round badge: the placing ("3rd") once known, otherwise a trophy. First place is drawn in gold. */
@Composable
fun PlacingBadge(t: Tournament, size: Dp = 44.dp) {
    val placement = t.placement
    val color = if (placement == 1) warnColor() else MaterialTheme.colorScheme.secondary
    val description = placingText(t)?.let { stringResource(R.string.tourn_placed, it) } ?: stringResource(t.status.label)
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (placement != null) {
            Text(ordinalText(placement), color = color, fontWeight = FontWeight.ExtraBold, fontSize = if (size > 50.dp) 18.sp else 13.sp, maxLines = 1)
        } else {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = color)
        }
    }
}

/** Asks for the opponent, then creates the next round's match. */
@Composable
fun StartRoundDialog(vm: AppViewModel, t: Tournament, onDismiss: () -> Unit, onStarted: (Long) -> Unit) {
    val matches by vm.matches.collectAsStateWithLifecycle()
    val index = matches.count { it.tournamentId == t.id }
    val defaultOpponent = stringResource(R.string.default_opponent)
    val past = remember(matches, defaultOpponent) {
        matches.map { it.opponent }.filter { it != "Opponent" && it != defaultOpponent }.distinct().take(8)
    }
    var opponent by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tourn_start_round, t.roundLabel(index))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    opponent, { opponent = it }, label = { Text(stringResource(R.string.match_opponent)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (past.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        past.forEach { name -> FilterChip(selected = opponent == name, onClick = { opponent = name }, label = { Text(name) }) }
                    }
                }
                if (t.deckId == null) {
                    Text(stringResource(R.string.tourn_no_deck_warning), fontSize = 13.sp, color = warnColor())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.startRound(t, opponent.trim().ifBlank { defaultOpponent }, onStarted) }) { Text(stringResource(R.string.tourn_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun todayUtcMidnight(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TournamentEditScreen(vm: AppViewModel, tournamentId: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val existing = tournamentId?.let { id -> tournaments.firstOrNull { it.id == id } }

    var loaded by rememberSaveable { mutableStateOf(tournamentId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<Long?>(todayUtcMidnight()) }
    var location by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("3on3") }
    var swiss by rememberSaveable { mutableIntStateOf(4) }
    var topCut by rememberSaveable { mutableIntStateOf(0) }
    var players by rememberSaveable { mutableStateOf("") }
    var deckId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(existing) {
        if (!loaded && existing != null) {
            name = existing.name; date = existing.date; location = existing.location; format = existing.format
            swiss = existing.swissRounds; topCut = existing.topCut
            players = existing.playerCount.takeIf { it > 0 }?.toString().orEmpty(); deckId = existing.deckId
            loaded = true
        }
    }
    if (deckId == null && existing == null && decks.size == 1) deckId = decks.first().id
    val defaultName = stringResource(R.string.tourn_default_name)

    fun save() {
        val base = existing ?: Tournament(name = "")
        val t = base.copy(
            name = name.trim().ifBlank { defaultName },
            date = date ?: todayUtcMidnight(),
            location = location.trim(),
            format = format.trim().ifBlank { "3on3" },
            swissRounds = swiss,
            topCut = topCut,
            playerCount = players.toIntOrNull() ?: 0,
            deckId = deckId,
        )
        if (existing == null) vm.createTournament(t, onSaved) else { vm.updateTournament(t); onBack() }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(if (existing == null) R.string.tourn_new else R.string.tourn_edit), onBack = onBack) },
        bottomBar = {
            Button(onClick = ::save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text(stringResource(if (existing == null) R.string.tourn_create else R.string.save), fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text(stringResource(R.string.tourn_name)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(stringResource(R.string.tourn_date), date, { date = it })
                    OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.tourn_location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.tourn_format), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3on3", "1on1").forEach { f -> FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tourn_swiss_rounds), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Stepper(swiss, { swiss = it }, min = 0, max = 15, label = stringResource(R.string.tourn_swiss_rounds))
                    }
                    Text(stringResource(R.string.tourn_top_cut), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 4, 8, 16).forEach { n ->
                            FilterChip(
                                selected = topCut == n, onClick = { topCut = n },
                                label = { Text(if (n == 0) stringResource(R.string.tourn_top_cut_none) else stringResource(R.string.tourn_top_n, n)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        players, { v -> players = v.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.tourn_players_optional)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.tourn_deck_bringing), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (decks.isEmpty()) {
                item { Text(stringResource(R.string.tourn_no_decks), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(decks, key = { it.id }) { d ->
                ChoiceRow(selected = deckId == d.id, title = d.name, subtitle = stringResource(R.string.battle_deck_combo_count, d.comboIds().count { it != null })) {
                    deckId = if (deckId == d.id) null else d.id
                }
            }
        }
    }
}

@Composable
fun TournamentScreen(vm: AppViewModel, tournamentId: Long, onBack: () -> Unit, onEdit: () -> Unit, onMatch: (Long) -> Unit, onOpenEvent: () -> Unit = {}) {
    val context = LocalContext.current
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val allPoints by vm.points.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val parts by vm.parts.collectAsStateWithLifecycle()
    val t = tournaments.firstOrNull { it.id == tournamentId }
    if (t == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.tourn_title), onBack = onBack) }) { pad -> Column(Modifier.padding(pad)) {} }
        return
    }
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }
    val rounds = matches.filter { it.tournamentId == t.id }.sortedBy { it.startedAt }
    val roundIds = rounds.map { it.id }.toSet()
    val points = allPoints.filter { it.matchId in roundIds }
    val rec = matchRecord(rounds, allPoints)
    val pointsFor = points.filter { it.scorer == Scorer.ME }.sumOf { it.points }
    val pointsAgainst = points.filter { it.scorer == Scorer.OPPONENT }.sumOf { it.points }
    val xtremes = points.count { it.scorer == Scorer.ME && it.finish == Finish.XTREME }
    val deck = t.deckId?.let { id -> decks.firstOrNull { it.id == id } }
    // Combos actually played here first (the deck may have changed since), then any deck combos not played yet.
    val deckCombos = (points.mapNotNull { it.myComboId } + deck?.comboIds()?.filterNotNull().orEmpty()).distinct().mapNotNull { comboMap[it] }
    val unfinished = rounds.any { matchOutcome(it, matchScore(it.id, allPoints)) == null }

    var note by rememberSaveable(t.id) { mutableStateOf(t.notes) }
    var startRound by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun share() {
        val locale = context.uiLocale()
        val text = buildString {
            append(t.name)
            placingText(context, t)?.let { append(" · $it") }
            append('\n')
            appendLine(listOf(pickedDay(t.date, locale), t.location, t.formatText()).filter { it.isNotBlank() }.joinToString(" · "))
            appendLine(
                context.getString(
                    R.string.tourn_share_record,
                    rec.wins, rec.losses, pointsFor, pointsAgainst,
                    "$xtremes Xtreme${if (xtremes == 1) "" else "s"}",
                )
            )
            rounds.forEach { m ->
                val s = matchScore(m.id, allPoints)
                val o = when (matchOutcome(m, s)) {
                    true -> context.getString(R.string.tourn_share_win)
                    false -> context.getString(R.string.tourn_share_loss)
                    null -> "–"
                }
                appendLine(context.getString(R.string.tourn_share_round, m.round, o, m.opponent, s.me, s.opponent))
            }
            if (deckCombos.isNotEmpty()) {
                appendLine(context.getString(R.string.tourn_share_deck, deckCombos.joinToString(", ") { c ->
                    val mine = points.filter { it.myComboId == c.id && it.scorer != Scorer.DRAW }
                    "${comboName(c, partMap)} (${mine.count { it.scorer == Scorer.ME }}/${mine.size})"
                }))
            }
            if (t.notes.isNotBlank()) appendLine(t.notes)
        }.trim()
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.tourn_share_results)))
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(t.name, subtitle = stringResource(t.status.label), onBack = onBack, actions = {
                IconButton(onClick = ::share) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.tourn_share_results)) }
                if (!t.organizer) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.tourn_edit)) }
                if (!t.organizer) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.tourn_cd_delete)) }
            })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlacingBadge(t, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(placingText(t) ?: stringResource(t.status.label), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            listOf(pickedDay(t.date), t.location).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(t.formatText(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${rec.wins}–${rec.losses}", stringResource(R.string.tourn_stat_matches_wl), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    StatTile("$pointsFor–$pointsAgainst", stringResource(R.string.tourn_stat_points), Modifier.weight(1f))
                    StatTile("$xtremes", "Xtremes", Modifier.weight(1f))
                }
            }
            if (t.organizer) {
                // Rounds and placings of an event I organize are run from the organizer screen.
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.tourn_managed_by_event), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = onOpenEvent) { Text(stringResource(R.string.tourn_open_event)) }
                    }
                }
            } else if (t.status != TournamentStatus.DONE) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { startRound = true }, enabled = !unfinished, modifier = Modifier.weight(1f)) {
                            Text(
                                if (unfinished) stringResource(R.string.tourn_round_in_progress)
                                else stringResource(R.string.tourn_start_round, t.roundLabel(rounds.size)),
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(onClick = { finishing = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tourn_finish), maxLines = 1) }
                    }
                    if (rounds.size >= t.totalRounds() && t.totalRounds() > 0) {
                        Text(
                            pluralStringResource(R.plurals.tourn_all_rounds_played, t.totalRounds(), t.totalRounds()),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    TextButton(onClick = { finishing = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.tourn_change_placing)) }
                }
            }
            item { SectionLabel(stringResource(R.string.tourn_rounds), Modifier.padding(top = 8.dp)) }
            if (rounds.isEmpty()) {
                item { EmptyState(Icons.Outlined.EmojiEvents, stringResource(R.string.tourn_no_rounds_title), stringResource(R.string.tourn_no_rounds_body)) }
            }
            items(rounds, key = { it.id }) { m ->
                val s = matchScore(m.id, allPoints)
                val stage = if (m.stage == MatchStage.TOP_CUT) stringResource(R.string.tourn_top_cut) else "Swiss"
                MatchRow(m, t, matchOutcome(m, s), s, subtitle = listOf(stage, shortDate(m.startedAt)).joinToString(" · ")) { onMatch(m.id) }
            }
            item { SectionLabel(stringResource(R.string.tourn_deck_brought), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (deckCombos.isEmpty()) {
                        Text(
                            if (deck == null) stringResource(R.string.tourn_no_deck_set) else stringResource(R.string.tourn_deck_no_combos, deck.name),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onEdit) { Text(stringResource(if (deck == null) R.string.tourn_pick_deck else R.string.tourn_change_deck)) }
                    } else {
                        Text(deck?.name ?: stringResource(R.string.tourn_combos_played), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.tourn_battles_won_per_combo), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        deckCombos.forEach { c ->
                            val mine = points.filter { it.myComboId == c.id && it.scorer != Scorer.DRAW }
                            val won = mine.count { it.scorer == Scorer.ME }
                            Spacer(Modifier.height(10.dp))
                            LabeledBar(comboName(c, partMap), "$won/${mine.size}", if (mine.isEmpty()) 0f else won.toFloat() / mine.size)
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.tourn_notes), Modifier.padding(top = 12.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text(stringResource(R.string.tourn_notes_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    if (note != t.notes) {
                        TextButton(onClick = { vm.updateTournament(t.copy(notes = note.trim())) }) { Text(stringResource(R.string.tourn_save_note)) }
                    }
                }
            }
        }
    }

    if (startRound) {
        StartRoundDialog(vm, t, onDismiss = { startRound = false }) { id -> startRound = false; onMatch(id) }
    }
    if (finishing) {
        FinishDialog(t, onDismiss = { finishing = false }) { placement, players ->
            finishing = false
            vm.updateTournament(t.copy(placement = placement, playerCount = players, status = TournamentStatus.DONE))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tourn_delete_title, t.name)) },
            text = { Text(pluralStringResource(R.plurals.tourn_delete_body, rounds.size, rounds.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteTournament(t); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FinishDialog(t: Tournament, onDismiss: () -> Unit, onDone: (Int?, Int) -> Unit) {
    var placement by rememberSaveable { mutableStateOf(t.placement?.toString().orEmpty()) }
    var players by rememberSaveable { mutableStateOf(t.playerCount.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tourn_finish_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tourn_finish_question))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        placement, { v -> placement = v.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.tourn_placing)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        players, { v -> players = v.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.tourn_players)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                placement.toIntOrNull()?.let { p ->
                    val playerCount = players.toIntOrNull() ?: 0
                    Text(
                        if (playerCount > 0) stringResource(R.string.placing_of, ordinalText(p), playerCount) else ordinalText(p),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(placement.toIntOrNull()?.takeIf { it > 0 }, players.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
