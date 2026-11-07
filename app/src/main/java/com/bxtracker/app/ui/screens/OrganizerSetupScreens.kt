package com.bxtracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.bxtracker.app.data.Tournament
import com.bxtracker.app.data.TournamentEngine
import com.bxtracker.app.data.comboIds
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.DateField
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.Stepper
import java.util.Calendar
import java.util.TimeZone

// Organizer mode, screens 15 (format) and 16 (players).

/** Today as UTC midnight, the way the date picker stores days. */
private fun orgSetupTodayUtc(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Screen 15 · Organize: format. [tournamentId] null creates a new organized event; otherwise edits one that is
 * still in SETUP (after that the format is locked).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventSetupScreen(vm: AppViewModel, tournamentId: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val allPlayers by vm.eventPlayers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val creating = tournamentId == null
    val existing = tournamentId?.let { id -> tournaments.firstOrNull { it.id == id } }

    if (!creating && existing == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.org_setup_title_edit), onBack = onBack) }) { pad ->
            Column(Modifier.padding(pad)) {}
        }
        return
    }
    if (existing != null && existing.stage != EventStage.SETUP) {
        OrgSetupLocked(existing, onBack)
        return
    }

    // In edit mode `existing` is loaded by now, so the form starts from it.
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var date by rememberSaveable { mutableStateOf<Long?>(existing?.date ?: orgSetupTodayUtc()) }
    var place by rememberSaveable { mutableStateOf(existing?.location.orEmpty()) }
    var formatName by rememberSaveable { mutableStateOf((existing?.eventFormat ?: EventFormat.SWISS_TOP_CUT).name) }
    var matchFormat by rememberSaveable { mutableStateOf(existing?.format ?: "3on3") }
    var swiss by rememberSaveable { mutableIntStateOf(existing?.swissRounds ?: 3) }
    var topCut by rememberSaveable { mutableIntStateOf(existing?.topCut ?: 0) }
    var third by rememberSaveable { mutableStateOf(existing?.thirdPlaceMatch ?: false) }
    var randomSeeding by rememberSaveable { mutableStateOf(existing?.randomSeeding ?: false) }
    var pointsToWin by rememberSaveable { mutableIntStateOf((existing?.pointsToWin ?: settings.pointsToWin).coerceIn(1, 20)) }
    // A bye counts as a win worth byeScore–0 in point difference. New events follow "First to" until it's changed.
    var byeScore by rememberSaveable { mutableIntStateOf(existing?.byeScore ?: pointsToWin) }
    var byeTouched by rememberSaveable { mutableStateOf(existing != null) }
    var scoringName by rememberSaveable { mutableStateOf((existing?.scoring ?: EventScoring.BATTLES).name) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var myName by rememberSaveable { mutableStateOf("") }
    var deckId by rememberSaveable { mutableStateOf(existing?.deckId) }
    // Set once the event is created, so coming back here updates it instead of creating a second one.
    var createdId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }

    val eventFormat = EventFormat.valueOf(formatName)
    val players = if (existing == null) emptyList() else allPlayers.filter { it.tournamentId == existing.id && !it.dropped }
    val hasMe = players.any { it.isMe }
    val showDeck = if (creating) playing else hasMe
    val defaultMe = stringResource(R.string.org_setup_default_me)

    LaunchedEffect(decks) {
        if (creating && deckId == null && decks.size == 1) deckId = decks.first().id
    }

    fun save() {
        if (busy) return
        val cut = if (eventFormat == EventFormat.SWISS_TOP_CUT) topCut else 0
        val thirdOn = third && ((eventFormat == EventFormat.SWISS_TOP_CUT && cut > 0) || eventFormat == EventFormat.SINGLE_ELIM)
        val created = createdId?.let { id -> tournaments.firstOrNull { it.id == id } }
        val base = existing ?: created ?: Tournament(name = "")
        val t = base.copy(
            name = name.trim(),
            date = date ?: orgSetupTodayUtc(),
            location = place.trim(),
            format = matchFormat,
            eventFormat = eventFormat,
            swissRounds = swiss,
            topCut = cut,
            thirdPlaceMatch = thirdOn,
            randomSeeding = eventFormat == EventFormat.SINGLE_ELIM && randomSeeding,
            pointsToWin = pointsToWin,
            byeScore = byeScore,
            scoring = EventScoring.valueOf(scoringName),
            deckId = if (showDeck) deckId else base.deckId.takeIf { !creating },
        )
        when {
            existing != null -> { vm.updateEvent(t); onSaved(existing.id) }
            created != null -> { vm.updateEvent(t); onSaved(created.id) }
            else -> {
                busy = true
                vm.createEvent(t, myName = if (playing) myName.trim().ifBlank { defaultMe } else null) { id ->
                    createdId = id
                    busy = false
                    onSaved(id)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            if (creating) BxTopBar(stringResource(R.string.org_setup_title_new), subtitle = stringResource(R.string.org_setup_step), onBack = onBack)
            else BxTopBar(stringResource(R.string.org_setup_title_edit), subtitle = existing?.name, onBack = onBack)
        },
        bottomBar = {
            Button(onClick = ::save, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text(stringResource(if (creating) R.string.org_setup_next else R.string.save), fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Event
            item { SectionLabel(stringResource(R.string.org_setup_section_event)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text(stringResource(R.string.org_setup_name)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateField(stringResource(R.string.org_setup_date), date, { date = it })
                    OutlinedTextField(
                        place, { place = it }, label = { Text(stringResource(R.string.org_setup_place)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Format
            item { SectionLabel(stringResource(R.string.org_setup_section_format), Modifier.padding(top = 12.dp)) }
            item {
                Column {
                    ChoiceRow(
                        eventFormat == EventFormat.SWISS_TOP_CUT, stringResource(R.string.org_setup_format_swiss),
                        stringResource(R.string.org_setup_format_swiss_sub),
                    ) { formatName = EventFormat.SWISS_TOP_CUT.name }
                    ChoiceRow(
                        eventFormat == EventFormat.SINGLE_ELIM, stringResource(R.string.org_setup_format_elim),
                        stringResource(R.string.org_setup_format_elim_sub),
                    ) { formatName = EventFormat.SINGLE_ELIM.name }
                    ChoiceRow(
                        eventFormat == EventFormat.ROUND_ROBIN, stringResource(R.string.org_setup_format_rr),
                        stringResource(R.string.org_setup_format_rr_sub),
                    ) { formatName = EventFormat.ROUND_ROBIN.name }
                }
            }
            when (eventFormat) {
                EventFormat.SWISS_TOP_CUT -> item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val roundsLabel = stringResource(R.string.org_setup_swiss_rounds)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(roundsLabel, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Stepper(swiss, { swiss = it }, min = 1, max = 15, label = roundsLabel)
                            }
                            if (!creating && players.size >= 2) {
                                val suggested = TournamentEngine.suggestedSwissRounds(players.size)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.org_setup_suggested_for, players.size, suggested),
                                        modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (swiss != suggested) {
                                        TextButton(onClick = { swiss = suggested }) { Text(stringResource(R.string.org_setup_use_n, suggested)) }
                                    }
                                }
                            } else {
                                Text(stringResource(R.string.org_setup_suggest_later), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OrgSetupFieldLabel(stringResource(R.string.org_setup_top_cut))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0, 4, 8, 16).forEach { n ->
                                    FilterChip(
                                        selected = topCut == n, onClick = { topCut = n },
                                        label = { Text(if (n == 0) stringResource(R.string.org_setup_top_cut_none) else stringResource(R.string.org_setup_top_n, n)) },
                                    )
                                }
                            }
                        }
                        if (topCut > 0) {
                            OrgSetupSwitchRow(stringResource(R.string.org_setup_third_place), stringResource(R.string.org_setup_third_place_sub), third) { third = it }
                        }
                    }
                }
                EventFormat.SINGLE_ELIM -> item {
                    Column(Modifier.padding(top = 8.dp)) {
                        OrgSetupFieldLabel(stringResource(R.string.org_setup_seeding), Modifier.padding(horizontal = 16.dp))
                        ChoiceRow(!randomSeeding, stringResource(R.string.org_setup_seed_order), stringResource(R.string.org_setup_seed_order_sub)) {
                            randomSeeding = false
                        }
                        ChoiceRow(randomSeeding, stringResource(R.string.org_setup_seed_random), stringResource(R.string.org_setup_seed_random_sub)) {
                            randomSeeding = true
                        }
                        OrgSetupSwitchRow(stringResource(R.string.org_setup_third_place), stringResource(R.string.org_setup_third_place_sub), third) { third = it }
                    }
                }
                EventFormat.ROUND_ROBIN -> Unit
            }

            // Match
            item { SectionLabel(stringResource(R.string.org_setup_section_match), Modifier.padding(top = 12.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3on3", "1on1").forEach { f -> FilterChip(selected = matchFormat == f, onClick = { matchFormat = f }, label = { Text(f) }) }
                    }
                    val firstTo = stringResource(R.string.org_setup_first_to)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(firstTo, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Stepper(pointsToWin, { pointsToWin = it; if (!byeTouched) byeScore = it }, min = 1, max = 20, label = firstTo)
                    }
                    // Byes only count toward standings in Swiss and round robin; in elimination they just advance.
                    if (eventFormat != EventFormat.SINGLE_ELIM) {
                        val byeLabel = stringResource(R.string.org_setup_bye_points)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(byeLabel, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.org_setup_bye_points_sub, byeScore),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Stepper(byeScore, { byeScore = it; byeTouched = true }, min = 0, max = 20, label = byeLabel)
                        }
                    }
                }
            }

            // Scoring
            item { SectionLabel(stringResource(R.string.org_setup_section_scoring), Modifier.padding(top = 12.dp)) }
            item {
                Column {
                    ChoiceRow(
                        scoringName == EventScoring.BATTLES.name, stringResource(R.string.org_setup_scoring_battles),
                        stringResource(R.string.org_setup_scoring_battles_sub),
                    ) { scoringName = EventScoring.BATTLES.name }
                    ChoiceRow(
                        scoringName == EventScoring.FINAL_SCORE_ONLY.name, stringResource(R.string.org_setup_scoring_final),
                        stringResource(R.string.org_setup_scoring_final_sub),
                    ) { scoringName = EventScoring.FINAL_SCORE_ONLY.name }
                }
            }

            // Me
            if (creating) {
                item { SectionLabel(stringResource(R.string.org_setup_section_you), Modifier.padding(top = 12.dp)) }
                item {
                    OrgSetupSwitchRow(stringResource(R.string.org_setup_playing), stringResource(R.string.org_setup_playing_sub), playing) { playing = it }
                }
                if (playing) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                myName, { myName = it }, label = { Text(stringResource(R.string.org_setup_my_name)) },
                                placeholder = { Text(defaultMe) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.org_setup_my_matches_note), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (showDeck) {
                item {
                    OrgSetupFieldLabel(stringResource(R.string.org_setup_my_deck), Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                }
                if (decks.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.org_setup_no_decks), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(decks, key = { it.id }) { d ->
                    ChoiceRow(deckId == d.id, d.name, stringResource(R.string.battle_deck_combo_count, d.comboIds().count { it != null })) {
                        deckId = if (deckId == d.id) null else d.id
                    }
                }
            }
        }
    }
}

/** Shown instead of the form once the event has started: the format can't change anymore. */
@Composable
private fun OrgSetupLocked(t: Tournament, onBack: () -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(R.string.org_setup_title_edit), subtitle = t.name, onBack = onBack) },
        bottomBar = {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                Text(stringResource(R.string.org_setup_back), fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.org_setup_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Screen 16 · Organize: players. Add players (with autocomplete from past names), check them in, then pair
 * round 1 or start the bracket.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventPlayersScreen(vm: AppViewModel, tournamentId: Long, onBack: () -> Unit, onEditFormat: () -> Unit, onStarted: () -> Unit) {
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val allPlayers by vm.eventPlayers.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val t = tournaments.firstOrNull { it.id == tournamentId }
    if (t == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.org_setup_players_title, 0), onBack = onBack) }) { pad ->
            Column(Modifier.padding(pad)) {}
        }
        return
    }

    val players = remember(allPlayers, tournamentId) {
        allPlayers.filter { it.tournamentId == tournamentId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }
    val active = players.filter { !it.dropped }
    val anyCheckedIn = active.any { it.checkedIn }
    // Same rule as AppViewModel.startEvent: once anyone is checked in, only checked-in players are paired.
    val pairable = if (anyCheckedIn) active.filter { it.checkedIn } else active
    val notCheckedIn = if (anyCheckedIn) active.count { !it.checkedIn } else 0
    val setup = t.stage == EventStage.SETUP
    val canStart = setup && pairable.size >= 2
    val elim = t.eventFormat == EventFormat.SINGLE_ELIM

    val known = remember(allPlayers, matches) { vm.knownPlayerNames() }
    var entry by rememberSaveable { mutableStateOf("") }
    val typed = entry.trim()
    val duplicate = typed.isNotEmpty() && players.any { it.name.equals(typed, ignoreCase = true) }
    val suggestions = remember(typed, known, players) {
        if (typed.isEmpty()) emptyList()
        else known.filter { n -> n.contains(typed, ignoreCase = true) && players.none { it.name.equals(n, ignoreCase = true) } }.take(5)
    }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var removingMeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmStart by rememberSaveable { mutableStateOf(false) }

    fun add(name: String) {
        val n = name.trim()
        if (n.isEmpty() || players.any { it.name.equals(n, ignoreCase = true) }) return
        vm.addEventPlayer(tournamentId, n)
        entry = ""
    }

    fun start() {
        confirmStart = false
        vm.startEvent(tournamentId)
        onStarted()
    }

    val startLabel = stringResource(if (elim) R.string.org_setup_start_bracket else R.string.org_setup_pair_round_1)

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(stringResource(R.string.org_setup_players_title, active.size), subtitle = t.name, onBack = onBack, actions = {
                IconButton(onClick = onEditFormat) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.org_setup_cd_edit_format)) }
            })
        },
        bottomBar = {
            if (setup) {
                Column {
                    if (!canStart) {
                        Text(
                            stringResource(if (anyCheckedIn) R.string.org_setup_need_two_checked_in else R.string.org_setup_need_two),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { if (notCheckedIn > 0) confirmStart = true else start() },
                        enabled = canStart,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) { Text(startLabel, fontWeight = FontWeight.Bold) }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (!setup) {
                item {
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.org_setup_started_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                entry, { entry = it }, label = { Text(stringResource(R.string.org_setup_add_label)) }, singleLine = true,
                                isError = duplicate,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                // Overriding onDone keeps the keyboard open, so names can be typed one after another.
                                keyboardActions = KeyboardActions(onDone = { add(entry) }),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(onClick = { add(entry) }, enabled = typed.isNotEmpty() && !duplicate, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.org_setup_cd_add_player))
                            }
                        }
                        if (duplicate) {
                            Text(
                                stringResource(R.string.org_setup_already_added), modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (suggestions.isNotEmpty()) {
                            Text(
                                stringResource(R.string.org_setup_past_players), modifier = Modifier.padding(top = 8.dp),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { n ->
                                    AssistChip(
                                        onClick = { add(n) },
                                        label = { Text(n) },
                                        leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (players.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Groups, stringResource(R.string.org_setup_empty_title), stringResource(R.string.org_setup_empty_body))
                }
            } else {
                item {
                    val checked = active.count { it.checkedIn }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(pluralStringResource(R.plurals.org_setup_checked_in_count, checked, checked, active.size), Modifier.weight(1f))
                        if (setup && active.any { !it.checkedIn }) {
                            TextButton(onClick = { active.filter { !it.checkedIn }.forEach { vm.updateEventPlayer(it.copy(checkedIn = true)) } }) {
                                Text(stringResource(R.string.org_setup_check_in_all))
                            }
                        }
                    }
                }
                items(players, key = { it.id }) { p ->
                    OrgSetupPlayerRow(
                        p = p,
                        editable = setup,
                        onEdit = { editingId = p.id },
                        onCheck = { vm.updateEventPlayer(p.copy(checkedIn = it)) },
                        onRemove = { if (p.isMe) removingMeId = p.id else vm.removeEventPlayer(p) },
                    )
                }
            }

            if (t.eventFormat == EventFormat.SWISS_TOP_CUT && pairable.size >= 2) {
                val suggested = TournamentEngine.suggestedSwissRounds(pairable.size)
                item {
                    AppCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Text(stringResource(R.string.org_setup_suggested_rounds, pairable.size, suggested), fontWeight = FontWeight.SemiBold)
                        if (t.swissRounds != suggested) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.org_setup_rounds_now, t.swissRounds), modifier = Modifier.weight(1f),
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (setup) {
                                    TextButton(onClick = { vm.updateEvent(t.copy(swissRounds = suggested)) }) {
                                        Text(stringResource(R.string.org_setup_use_n, suggested))
                                    }
                                }
                            }
                        } else {
                            Text(stringResource(R.string.org_setup_rounds_ok), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                AppCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Text(stringResource(R.string.org_setup_notes_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OrgSetupBullet(stringResource(if (elim) R.string.org_setup_note_bye_bracket else R.string.org_setup_note_bye))
                    OrgSetupBullet(stringResource(R.string.org_setup_note_drop))
                    OrgSetupBullet(stringResource(if (anyCheckedIn) R.string.org_setup_note_checkin_on else R.string.org_setup_note_checkin_off))
                    if (players.any { it.isMe }) OrgSetupBullet(stringResource(R.string.org_setup_my_matches_note))
                }
            }
        }
    }

    players.firstOrNull { it.id == editingId }?.let { p ->
        OrgSetupEditPlayerDialog(p, players, onDismiss = { editingId = null }) { updated ->
            editingId = null
            vm.updateEventPlayer(updated)
        }
    }

    players.firstOrNull { it.id == removingMeId }?.let { p ->
        AlertDialog(
            onDismissRequest = { removingMeId = null },
            title = { Text(stringResource(R.string.org_setup_remove_me_title)) },
            text = { Text(stringResource(R.string.org_setup_remove_me_body)) },
            confirmButton = {
                TextButton(onClick = { removingMeId = null; vm.removeEventPlayer(p) }) {
                    Text(stringResource(R.string.org_setup_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removingMeId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text(stringResource(R.string.org_setup_start_title)) },
            text = { Text(pluralStringResource(R.plurals.org_setup_start_body, notCheckedIn, notCheckedIn)) },
            confirmButton = { TextButton(onClick = ::start, enabled = canStart) { Text(startLabel) } },
            dismissButton = { TextButton(onClick = { confirmStart = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OrgSetupPlayerRow(
    p: EventPlayer,
    editable: Boolean,
    onEdit: () -> Unit,
    onCheck: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val editLabel = stringResource(R.string.org_setup_edit_player)
    val checkDescription = stringResource(R.string.org_setup_cd_check_in, p.name)
    val avatarBg = if (p.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val avatarFg = if (p.isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(avatarBg), contentAlignment = Alignment.Center) {
            Text(p.name.trim().take(1).uppercase(), color = avatarFg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (p.isMe) {
                    Spacer(Modifier.width(6.dp))
                    OrgSetupTag(stringResource(R.string.org_setup_you_tag), MaterialTheme.colorScheme.primary)
                }
                if (p.dropped) {
                    Spacer(Modifier.width(6.dp))
                    OrgSetupTag(stringResource(R.string.org_setup_dropped), MaterialTheme.colorScheme.error)
                }
            }
            Text(
                p.deckNote.ifBlank { stringResource(R.string.org_setup_no_deck_note) },
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (p.deckNote.isBlank()) 0.7f else 1f),
            )
        }
        Checkbox(
            checked = p.checkedIn,
            onCheckedChange = onCheck,
            enabled = editable,
            modifier = Modifier.semantics { contentDescription = checkDescription },
        )
        if (editable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.org_setup_cd_remove, p.name))
            }
        }
    }
}

@Composable
private fun OrgSetupEditPlayerDialog(p: EventPlayer, players: List<EventPlayer>, onDismiss: () -> Unit, onSave: (EventPlayer) -> Unit) {
    var name by rememberSaveable(p.id) { mutableStateOf(p.name) }
    var note by rememberSaveable(p.id) { mutableStateOf(p.deckNote) }
    val taken = players.any { it.id != p.id && it.name.equals(name.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.org_setup_edit_player)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text(stringResource(R.string.org_setup_player_name)) }, singleLine = true,
                    isError = taken,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (taken) {
                    Text(stringResource(R.string.org_setup_already_added), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    note, { note = it }, label = { Text(stringResource(R.string.org_setup_deck_note)) },
                    placeholder = { Text(stringResource(R.string.org_setup_deck_note_hint)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(p.copy(name = name.trim(), deckNote = note.trim())) }, enabled = name.isNotBlank() && !taken) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun OrgSetupFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A full-width row that toggles a switch; the whole row is the touch target. */
@Composable
private fun OrgSetupSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun OrgSetupTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

@Composable
private fun OrgSetupBullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
