package com.bxtracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.BattleSide
import com.bxtracker.app.data.EventBattle
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.MatchStage
import com.bxtracker.app.data.Pairing
import com.bxtracker.app.data.PairingStatus
import com.bxtracker.app.data.TournamentEngine
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.theme.lossColor
import com.bxtracker.app.ui.theme.winColor

/**
 * Battle-by-battle scoring of a match between two other players in an organized event (the organizer's
 * counterpart of [MatchScreen]; my own pairings are scored there). Scores resolve through
 * [TournamentEngine.resolve], so a typed-in final score wins over the battles logged here.
 */
@Composable
fun EventMatchScreen(vm: AppViewModel, pairingId: Long, onBack: () -> Unit) {
    val pairings by vm.pairings.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val players by vm.eventPlayers.collectAsStateWithLifecycle()
    val allBattles by vm.eventBattles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pairing = pairings.firstOrNull { it.id == pairingId }
    val tournament = pairing?.let { p -> tournaments.firstOrNull { it.id == p.tournamentId } }
    if (pairing == null || tournament == null) {
        Scaffold(
            contentWindowInsets = NoInsets,
            topBar = { BxTopBar(stringResource(R.string.org_score_title_missing), onBack = onBack) },
        ) { pad -> Column(Modifier.padding(pad)) {} }
        return
    }
    val haptics = LocalHapticFeedback.current
    val battles = allBattles.filter { it.pairingId == pairing.id }
    val result = TournamentEngine.resolve(pairing, tournament, battles, null, emptyList(), null)
    val nameA = players.firstOrNull { it.id == pairing.playerAId }?.name ?: "?"
    val nameB = pairing.playerBId?.let { id -> players.firstOrNull { it.id == id }?.name ?: "?" }
        ?: stringResource(R.string.org_score_bye)
    val roundLabel = remember(pairing, pairings) { roundLabelFor(pairing, pairings) }
    val done = result.status == PairingStatus.DONE
    val canScore = !done && !pairing.manual && pairing.playerBId != null
    val colorA = MaterialTheme.colorScheme.secondary
    val colorB = lossColor()

    var comboA by rememberSaveable { mutableStateOf("") }
    var comboB by rememberSaveable { mutableStateOf("") }
    var finalDialog by remember { mutableStateOf(false) }

    fun record(side: BattleSide, finish: Finish?) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.addEventBattle(
            EventBattle(
                pairingId = pairing.id,
                scorer = side,
                finish = finish,
                points = finish?.let { settings.pointsFor(it) } ?: 0,
                comboText = when (side) {
                    BattleSide.A -> comboA.trim()
                    BattleSide.B -> comboB.trim()
                    BattleSide.DRAW -> ""
                },
            )
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.org_score_title, nameA, nameB, roundLabel),
                subtitle = pluralStringResource(R.plurals.org_score_subtitle, tournament.pointsToWin, tournament.name, tournament.pointsToWin),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { vm.undoEventBattle(pairing.id) }, enabled = battles.isNotEmpty() && !pairing.manual) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.org_score_cd_undo))
                    }
                    IconButton(onClick = { finalDialog = true }, enabled = pairing.playerBId != null) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.org_score_cd_final))
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreColumn(nameA, result.scoreA, colorA, Modifier.weight(1f))
                        Text(stringResource(R.string.org_score_battle_n, battles.size + 1), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        ScoreColumn(nameB, result.scoreB, colorB, Modifier.weight(1f))
                    }
                }
            }
            if (done) {
                val winnerName = if (result.winnerId == pairing.playerAId) nameA else nameB
                val (high, low) = if (result.winnerId == pairing.playerAId) result.scoreA to result.scoreB else result.scoreB to result.scoreA
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = winColor().copy(alpha = 0.14f),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.org_score_wins, winnerName, high, low),
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = winColor(),
                            )
                            if (!pairing.manual && pairing.playerBId != null) {
                                Text(stringResource(R.string.org_score_saved_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.done)) }
                        }
                    }
                }
            }
            if (pairing.manual) {
                item {
                    AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            stringResource(R.string.org_score_manual_note, pairing.scoreA, pairing.scoreB),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { vm.clearFinalScore(pairing) }) { Text(stringResource(R.string.org_score_back_to_battles)) }
                    }
                }
            }
            if (pairing.playerBId != null) {
                item {
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = comboA,
                            onValueChange = { comboA = it },
                            label = { Text(stringResource(R.string.org_score_combo_label, nameA), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            enabled = canScore,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = comboB,
                            onValueChange = { comboB = it },
                            label = { Text(stringResource(R.string.org_score_combo_label, nameB), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            enabled = canScore,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FinishGrid(stringResource(R.string.org_score_scored, nameA), colorA, settings::pointsFor, enabled = canScore, modifier = Modifier.weight(1f)) { record(BattleSide.A, it) }
                        FinishGrid(stringResource(R.string.org_score_scored, nameB), colorB, settings::pointsFor, enabled = canScore, modifier = Modifier.weight(1f)) { record(BattleSide.B, it) }
                    }
                    OutlinedButton(
                        onClick = { record(BattleSide.DRAW, null) },
                        enabled = canScore,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    ) { Text(stringResource(R.string.org_score_draw_button)) }
                }
            }
            if (battles.isNotEmpty()) {
                item { Text(stringResource(R.string.org_score_this_match), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                val numbered = battles.mapIndexed { i, b -> (i + 1) to b }.reversed()
                items(numbered, key = { it.second.id }) { (index, b) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.org_score_battle_short, index), fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (b.scorer) {
                                    BattleSide.A -> stringResource(R.string.org_score_history_point, b.finish?.label.orEmpty(), nameA, b.points)
                                    BattleSide.B -> stringResource(R.string.org_score_history_point, b.finish?.label.orEmpty(), nameB, b.points)
                                    BattleSide.DRAW -> stringResource(R.string.org_score_draw)
                                },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = when (b.scorer) {
                                    BattleSide.A -> colorA
                                    BattleSide.B -> colorB
                                    BattleSide.DRAW -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (b.comboText.isNotBlank()) {
                                Text(b.comboText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (finalDialog) {
        var a by rememberSaveable { mutableIntStateOf(result.scoreA) }
        var b by rememberSaveable { mutableIntStateOf(result.scoreB) }
        AlertDialog(
            onDismissRequest = { finalDialog = false },
            title = { Text(stringResource(R.string.org_score_final_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.org_score_final_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nameA, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = colorA, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Stepper(a, { a = it }, min = 0, max = 99, label = nameA)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nameB, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = colorB, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Stepper(b, { b = it }, min = 0, max = 99, label = nameB)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finalDialog = false
                    vm.setFinalScore(pairing, a, b)
                    onBack()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { finalDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "R2" in Swiss / round-robin rounds, the bracket code ("QF", "SF", "F") in the bracket, "3P" for the 3rd place match. */
private fun roundLabelFor(p: Pairing, all: List<Pairing>): String = when {
    p.thirdPlace -> "3P"
    p.stage == MatchStage.SWISS -> "R${p.round}"
    else -> TournamentEngine.bracketLabel(
        all.count { it.tournamentId == p.tournamentId && it.stage == MatchStage.TOP_CUT && it.round == p.round && !it.thirdPlace } * 2,
        p.round,
    )
}
