package com.bxtracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.ui.draw.clip
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bxtracker.app.R
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.DeckFix
import com.bxtracker.app.data.Part
import com.bxtracker.app.data.Record
import com.bxtracker.app.data.deckFixes
import com.bxtracker.app.data.deckMix
import com.bxtracker.app.data.missingParts
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.theme.typeTint
import com.bxtracker.app.ui.theme.warnColor
import com.bxtracker.app.data.comboIds
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.comboRecords
import com.bxtracker.app.data.deckConflicts
import com.bxtracker.app.data.sharedParts
import com.bxtracker.app.data.withSlot
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.TypeTag
import com.bxtracker.app.ui.theme.winColor

@Composable
fun DecksScreen(vm: AppViewModel, onDeck: (Long) -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(R.string.tab_decks), subtitle = stringResource(R.string.deck_list_subtitle, "3on3")) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.createDeck(context.getString(R.string.deck_default_name, decks.size + 1)) { onDeck(it) } },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.deck_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (decks.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Style, stringResource(R.string.deck_empty_title), stringResource(R.string.deck_empty_body))
                }
            }
            items(decks, key = { it.id }) { deck ->
                val slotCombos = deck.comboIds().map { id -> id?.let { comboMap[it] } }
                val conflicts = deckConflicts(slotCombos, partMap)
                val filled = slotCombos.count { it != null }
                AppCard(Modifier.fillMaxWidth().clickable { onDeck(deck.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(deck.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        when {
                            conflicts.isNotEmpty() -> Text(pluralStringResource(R.plurals.deck_conflicts, conflicts.size, conflicts.size), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            filled < 3 -> Text(stringResource(R.string.deck_filled_of, filled, 3), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            else -> Text(stringResource(R.string.deck_ready), color = winColor(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val emptySlot = stringResource(R.string.deck_slot_empty)
                    slotCombos.forEachIndexed { i, c ->
                        Text(
                            "${i + 1}. " + (c?.let { comboName(it, partMap) } ?: emptySlot),
                            fontSize = 14.sp,
                            color = if (c == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun freeText(context: Context, fix: DeckFix): String {
    val copies = fix.with.copies
    val free = fix.freeCopies
    return when {
        free == 0 -> context.getString(R.string.deck_fix_none_free)
        free == copies && copies == 1 -> context.getString(R.string.deck_fix_free_one)
        free == copies && copies == 2 -> context.getString(R.string.deck_fix_free_both)
        free == copies -> context.getString(R.string.deck_fix_free_all)
        else -> context.getString(R.string.deck_fix_free_some, free, copies)
    }
}

/** "1 and 2" / "1, 2 and 3" (localized conjunction). */
private fun joinSlots(context: Context, slots: List<Int>): String = when (slots.size) {
    0 -> ""
    1 -> slots[0].toString()
    else -> context.getString(R.string.deck_list_and, slots.dropLast(1).joinToString(", "), slots.last().toString())
}

@Composable
private fun FixCard(fix: DeckFix, parts: Map<Long, Part>, records: Map<Long, Record>, onApply: () -> Unit) {
    val context = LocalContext.current
    val result = fix.result()
    val label = fix.with.code.ifBlank { fix.with.name }
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Text(
            stringResource(
                R.string.deck_fix_title,
                fix.slot,
                fix.replace.kind.label.lowercase(),
                fix.replace.code.ifBlank { fix.replace.name },
                label,
            ),
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(comboName(result, parts), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            stringResource(R.string.deck_fix_you_have, label, fix.with.copies, freeText(context, fix)),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val rec = fix.existing?.let { records[it.id] }
        Text(
            when {
                fix.existing == null -> stringResource(R.string.deck_fix_new_combo)
                rec != null && rec.total > 0 -> stringResource(
                    R.string.deck_fix_already_built_record,
                    stringResource(R.string.record_wl, rec.wins, rec.losses),
                    rec.percent,
                )
                else -> stringResource(R.string.deck_fix_already_built_unbattled)
            },
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onApply) { Text(stringResource(R.string.deck_fix_apply, fix.slot)) }
    }
}

@Composable
fun DeckEditScreen(vm: AppViewModel, deckId: Long, onBack: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val deck = decks.firstOrNull { it.id == deckId }
    if (deck == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.deck_title), onBack = onBack) }) { pad -> Column(Modifier.padding(pad)) {} }
        return
    }
    val context = LocalContext.current
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val comboMap = remember(combos) { combos.associateBy { it.id } }
    val records = remember(points) { comboRecords(points) }
    var name by rememberSaveable(deck.id) { mutableStateOf(deck.name) }
    var activeSlot by rememberSaveable { mutableIntStateOf(deck.comboIds().indexOfFirst { it == null }.coerceAtLeast(0)) }
    var confirmDelete by remember { mutableStateOf(false) }

    val slotCombos = deck.comboIds().map { id -> id?.let { comboMap[it] } }
    val conflicts = deckConflicts(slotCombos, partMap)
    val others = slotCombos.filterIndexed { i, c -> i != activeSlot && c != null }.filterNotNull()
    val fixes = remember(slotCombos, parts, combos, records) { deckFixes(slotCombos, parts, combos, records) }
    val mix = deckMix(slotCombos)
    val missing = missingParts(slotCombos, partMap)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(deck.name, subtitle = stringResource(R.string.deck_edit_subtitle, "3on3"), onBack = {
                if (name.isNotBlank() && name != deck.name) vm.updateDeck(deck.copy(name = name.trim()))
                onBack()
            }, actions = {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.deck_delete_cd)) }
            })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.deck_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (conflicts.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                conflicts.forEach { c ->
                                    Text(
                                        stringResource(R.string.deck_conflict_line, c.part.kind.label, c.part.name, joinSlots(context, c.slots)),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                                Text(
                                    stringResource(R.string.deck_conflict_hint, "3on3"),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
                if (fixes.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.deck_suggested_fix)) }
                    items(fixes, key = { "fix_${it.slot}_${it.replace.id}_${it.with.id}" }) { fix ->
                        FixCard(fix, partMap, records) { vm.applyDeckFix(deck, fix) }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.deck_no_fix),
                            modifier = Modifier.padding(horizontal = 20.dp),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = warnColor().copy(alpha = 0.14f),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = warnColor())
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pluralStringResource(
                                    R.plurals.deck_missing_parts,
                                    missing.size,
                                    missing.joinToString { "${it.kind.label} ${it.name}" },
                                ),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = warnColor(),
                            )
                        }
                    }
                }
            }
            if (mix.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.deck_mix), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BeyType.entries.filter { it != BeyType.BALANCE || (mix[it] ?: 0) > 0 }.forEach { t ->
                            val tint = typeTint(t)
                            val n = mix[t] ?: 0
                            Text(
                                stringResource(R.string.deck_mix_count, n, t.label),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (n > 0) tint.bg else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (n > 0) tint.fg else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slotCombos.forEachIndexed { i, combo ->
                        val isActive = activeSlot == i
                        val bad = conflicts.any { (i + 1) in it.slots }
                        Surface(
                            onClick = { activeSlot = i },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                if (isActive || bad) 2.dp else 1.dp,
                                when {
                                    bad -> MaterialTheme.colorScheme.error
                                    isActive -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(32.dp)) {
                                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    if (combo == null) {
                                        Text(stringResource(R.string.deck_choose_combo), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    } else {
                                        Text(comboName(combo, partMap), fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        TypeTag(combo.type)
                                    }
                                }
                                if (combo != null && !bad) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.deck_no_conflicts_cd), tint = winColor())
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.deck_pick_combo, activeSlot + 1),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (combos.isEmpty()) {
                item { Text(stringResource(R.string.deck_no_combos), modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(combos, key = { it.id }) { combo ->
                val clash = sharedParts(combo, others, partMap)
                val rec = records[combo.id]
                val selected = deck.comboIds()[activeSlot] == combo.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.updateDeck(deck.withSlot(activeSlot, if (selected) null else combo.id))
                            val next = (0..2).firstOrNull { it != activeSlot && deck.comboIds()[it] == null }
                            if (!selected && next != null) activeSlot = next
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(comboName(combo, partMap), fontWeight = FontWeight.SemiBold)
                        if (clash.isNotEmpty()) {
                            Text(
                                stringResource(R.string.deck_repeats, clash.joinToString { it.name }),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                            )
                        } else if (rec != null && rec.total > 0) {
                            Text(
                                stringResource(R.string.deck_record_percent, stringResource(R.string.record_wl, rec.wins, rec.losses), rec.percent),
                                fontSize = 12.sp, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.deck_in_slot_cd), tint = MaterialTheme.colorScheme.secondary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.deck_delete_title, deck.name)) },
            text = { Text(stringResource(R.string.deck_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteDeck(deck); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
