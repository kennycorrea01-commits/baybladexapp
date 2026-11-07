package com.bxtracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Scorer
import com.bxtracker.app.data.StatsPeriod
import com.bxtracker.app.data.battleRecord
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.comboRecords
import com.bxtracker.app.data.finishBreakdown
import com.bxtracker.app.data.matchesFor
import com.bxtracker.app.data.recordVsType
import com.bxtracker.app.data.utcDayToLocal
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.FinishBar
import com.bxtracker.app.ui.components.LabeledBar
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.StatTile
import com.bxtracker.app.ui.ordinalText
import com.bxtracker.app.ui.pickedDay
import com.bxtracker.app.ui.placingText
import com.bxtracker.app.ui.theme.typeTint
import com.bxtracker.app.ui.theme.warnColor

@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val allPoints by vm.points.collectAsStateWithLifecycle()
    val tournaments by vm.tournaments.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }

    var period by rememberSaveable { mutableStateOf(if (settings.seasonStart != null) StatsPeriod.SEASON else StatsPeriod.ALL) }
    var showFew by rememberSaveable { mutableStateOf(false) }

    val periodMatches = matchesFor(period, matches, settings.seasonStart)
    val ids = periodMatches.map { it.id }.toSet()
    val points = allPoints.filter { it.matchId in ids }
    val periodTournaments = when (period) {
        StatsPeriod.SEASON -> settings.seasonStart?.let { s -> tournaments.filter { utcDayToLocal(it.date) >= utcDayToLocal(s) } } ?: tournaments
        else -> tournaments
    }
    val battles = battleRecord(points)
    val bestFinish = periodTournaments.filter { it.placement != null }.minByOrNull { it.placement!! }
    val mine = finishBreakdown(points, Scorer.ME)
    val theirs = finishBreakdown(points, Scorer.OPPONENT)
    val ranked = comboRecords(points).entries
        .filter { comboMap.containsKey(it.key) && it.value.total > 0 }
        .sortedWith(compareByDescending<Map.Entry<Long, com.bxtracker.app.data.Record>> { it.value.rate }.thenByDescending { it.value.total })
    val shownCombos = if (showFew) ranked else ranked.filter { it.value.total >= 3 }
    val hidden = ranked.size - shownCombos.size
    val vsType = recordVsType(points)
    val placings = periodTournaments.filter { it.placement != null }.sortedBy { it.date }.takeLast(8)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.stats_title),
                subtitle = when (period) {
                    StatsPeriod.SEASON -> settings.seasonStart?.let { stringResource(R.string.stats_since, pickedDay(it)) }
                        ?: stringResource(R.string.stats_no_season)
                    StatsPeriod.TOURNAMENTS -> stringResource(R.string.stats_sub_tournaments)
                    StatsPeriod.ALL -> stringResource(R.string.stats_sub_all)
                },
                onBack = onBack,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsPeriod.entries.forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(p.label)) }) }
                }
            }
            if (periodMatches.isEmpty()) {
                item { EmptyState(Icons.Outlined.BarChart, stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_body)) }
                return@LazyColumn
            }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${battles.percent}%", stringResource(R.string.stats_battle_win_rate), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                    StatTile("${periodMatches.size}", stringResource(R.string.stats_matches_played), Modifier.weight(1f))
                    StatTile(
                        bestFinish?.placement?.let { ordinalText(it) } ?: "–",
                        if (bestFinish != null && bestFinish.playerCount > 0) stringResource(R.string.stats_best_finish_of, bestFinish.playerCount)
                        else stringResource(R.string.stats_best_finish),
                        Modifier.weight(1f),
                    )
                }
            }
            if (mine.isNotEmpty() || theirs.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.stats_how_you_score), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        if (mine.isNotEmpty()) FinishBar(mine) else Text(stringResource(R.string.stats_not_scored), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (theirs.isNotEmpty()) {
                            val worst = theirs.maxBy { it.value }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.stats_worst_finish, worst.key.label, worst.value * 100 / theirs.values.sum()),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.stats_best_combos), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.stats_by_win_rate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.stats_under_3), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(4.dp))
                        Switch(checked = showFew, onCheckedChange = { showFew = it })
                    }
                    if (shownCombos.isEmpty()) {
                        Text(
                            stringResource(if (ranked.isEmpty()) R.string.stats_no_combo_battles else R.string.stats_no_combo_3),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    shownCombos.forEachIndexed { i, (id, rec) ->
                        Spacer(Modifier.height(10.dp))
                        LabeledBar(
                            "${i + 1}. ${comboName(comboMap.getValue(id), partMap)}",
                            stringResource(R.string.stats_record_rate, stringResource(R.string.record_wl, rec.wins, rec.losses), rec.percent),
                            rec.rate,
                        )
                    }
                    if (!showFew && hidden > 0) {
                        Text(pluralStringResource(R.plurals.stats_hidden_combos, hidden, hidden), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stats_vs_type), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.stats_vs_type_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val types = BeyType.entries.filter { it != BeyType.BALANCE || vsType.containsKey(it) }
                    types.forEach { t ->
                        val rec = vsType[t]
                        Spacer(Modifier.height(10.dp))
                        LabeledBar(
                            t.label,
                            rec?.let { stringResource(R.string.stats_rate_of, it.percent, it.wins, it.total) } ?: stringResource(R.string.stats_no_battles),
                            rec?.rate ?: 0f,
                            color = typeTint(t).fg,
                        )
                    }
                }
            }
            if (placings.isNotEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.stats_placings), fontWeight = FontWeight.Bold)
                        Text(pluralStringResource(R.plurals.stats_last_events, placings.size, placings.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                            placings.forEach { t ->
                                val p = t.placement!!
                                val fraction = if (t.playerCount > 0) 1f - (p - 1).toFloat() / t.playerCount else 1f / p
                                val barDescription = stringResource(R.string.stats_placing_desc, t.name, placingText(t).orEmpty())
                                Column(Modifier.weight(1f, fill = false).widthIn(max = 56.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Text(ordinalText(p), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Box(
                                        Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0.06f, 1f) * 0.75f)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (p == 1) warnColor() else MaterialTheme.colorScheme.primary)
                                            .semantics { contentDescription = barDescription },
                                    )
                                    Text(t.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
