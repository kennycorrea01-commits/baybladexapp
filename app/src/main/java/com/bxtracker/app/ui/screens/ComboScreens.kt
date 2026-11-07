package com.bxtracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Combo
import com.bxtracker.app.data.Part
import com.bxtracker.app.data.PartKind
import com.bxtracker.app.data.Record
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.comboRecords
import com.bxtracker.app.data.lastBattleAt
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.bxtracker.app.R
import com.bxtracker.app.ui.shortDate
import com.bxtracker.app.data.isCx
import com.bxtracker.app.data.ComboShape
import com.bxtracker.app.data.isComplete
import com.bxtracker.app.data.neededSlots
import com.bxtracker.app.data.normalized
import com.bxtracker.app.data.partFor
import com.bxtracker.app.data.shape
import com.bxtracker.app.data.partIds
import com.bxtracker.app.data.usageCounts
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.PartPhoto
import com.bxtracker.app.ui.components.RecordBar
import com.bxtracker.app.ui.components.TypeTag
import com.bxtracker.app.ui.theme.warnColor
import com.bxtracker.app.ui.theme.winColor

@Composable
fun CombosScreen(vm: AppViewModel, onNew: () -> Unit, onCombo: (Long) -> Unit, onAddPart: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var sort by rememberSaveable { mutableStateOf(ComboSort.WIN_RATE) }
    var sortMenu by remember { mutableStateOf(false) }
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val records = remember(points) { comboRecords(points) }
    val lastUsed = remember(points) { lastBattleAt(points) }

    val shown = combos
        .filter {
            when (filter) {
                FILTER_ALL -> true
                FILTER_FAVORITES -> it.favorite
                else -> it.type.name == filter
            }
        }
        .sortedWith(
            when (sort) {
                ComboSort.WIN_RATE -> compareByDescending<Combo> { it.favorite }
                    .thenByDescending { records[it.id]?.rate ?: -1f }
                    .thenByDescending { records[it.id]?.total ?: 0 }
                    .thenBy { comboName(it, partMap) }
                ComboSort.RECENT -> compareByDescending<Combo> { lastUsed[it.id] ?: 0L }.thenByDescending { it.createdAt }
                ComboSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { comboName(it, partMap) }
            }
        )

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.tab_combos),
                subtitle = pluralStringResource(R.plurals.combo_list_subtitle, combos.size, combos.size, stringResource(sort.label).lowercase()),
                actions = {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.combo_sort_cd)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        ComboSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(stringResource(s.label)) },
                                onClick = { sort = s; sortMenu = false },
                                trailingIcon = if (sort == s) { { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.combo_sort_current_cd)) } } else null,
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (parts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.combo_new)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = listOf(
                        FILTER_ALL to stringResource(R.string.combo_filter_all),
                        FILTER_FAVORITES to stringResource(R.string.combo_filter_favorites),
                    ) + BeyType.entries.map { it.name to it.label }
                    filters.forEach { (key, label) ->
                        FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    when {
                        parts.isEmpty() -> EmptyState(
                            Icons.Outlined.Layers,
                            stringResource(R.string.combo_empty_no_parts_title),
                            stringResource(R.string.combo_empty_no_parts_body),
                            stringResource(R.string.combo_add_part),
                            onAddPart,
                        )
                        combos.isEmpty() -> EmptyState(
                            Icons.Outlined.Layers,
                            stringResource(R.string.combo_empty_title),
                            stringResource(
                                R.string.combo_empty_body,
                                "${PartKind.BLADE.label} + ${PartKind.RATCHET.label} + ${PartKind.BIT.label}",
                            ),
                            stringResource(R.string.combo_new),
                            onNew,
                        )
                        else -> EmptyState(
                            Icons.Outlined.Layers,
                            stringResource(R.string.combo_empty_filter_title),
                            stringResource(R.string.combo_empty_filter_body),
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { combo ->
                ComboCard(combo, partMap, records[combo.id], lastUsed[combo.id], onClick = { onCombo(combo.id) }, onStar = { vm.toggleFavorite(combo) })
            }
        }
    }
}

/** Saveable filter keys. Type filters use [BeyType.name]; only the chip labels are localized. */
private const val FILTER_ALL = "ALL"
private const val FILTER_FAVORITES = "FAVORITES"

private enum class ComboSort(@StringRes val label: Int) {
    WIN_RATE(R.string.combo_sort_win_rate),
    RECENT(R.string.combo_sort_recent),
    NAME(R.string.combo_sort_name),
}

@Composable
private fun ComboCard(combo: Combo, parts: Map<Long, Part>, record: Record?, lastUsed: Long?, onClick: () -> Unit, onStar: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeTag(combo.type)
            if (combo.isCx()) {
                Spacer(Modifier.width(6.dp))
                Text("CX", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.weight(1f))
            if (lastUsed != null) {
                Text(stringResource(R.string.combo_tested, shortDate(lastUsed)),fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (combo.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(if (combo.favorite) R.string.combo_favorite_remove_cd else R.string.combo_favorite_add_cd),
                    tint = if (combo.favorite) warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(comboName(combo, parts), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            combo.partIds().forEach { id -> PartPhoto(parts[id]?.photoPath, 40.dp, shape = RoundedCornerShape(8.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        if (record != null && record.total > 0) {
            RecordBar(record)
        } else {
            Text(stringResource(R.string.combo_not_battled),fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (combo.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(combo.notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun shapeFor(kind: PartKind): ComboShape = when (kind) {
    PartKind.METAL_BLADE, PartKind.OVER_BLADE -> ComboShape.CX_METAL
    else -> if (kind.isCx) ComboShape.CX else ComboShape.BASIC
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComboBuilderScreen(
    vm: AppViewModel,
    comboId: Long?,
    startPartId: Long?,
    onDone: () -> Unit,
    onAddPart: (PartKind) -> Unit,
) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val existing = comboId?.let { id -> combos.firstOrNull { it.id == id } }

    var initialized by rememberSaveable { mutableStateOf(false) }
    var shape by rememberSaveable { mutableStateOf(ComboShape.BASIC) }
    var blade by rememberSaveable { mutableStateOf<Long?>(null) }
    var lock by rememberSaveable { mutableStateOf<Long?>(null) }
    var main by rememberSaveable { mutableStateOf<Long?>(null) }
    var metal by rememberSaveable { mutableStateOf<Long?>(null) }
    var over by rememberSaveable { mutableStateOf<Long?>(null) }
    var assist by rememberSaveable { mutableStateOf<Long?>(null) }
    var ratchet by rememberSaveable { mutableStateOf<Long?>(null) }
    var bit by rememberSaveable { mutableStateOf<Long?>(null) }
    var type by rememberSaveable { mutableStateOf<BeyType?>(null) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf<PartKind?>(PartKind.BLADE) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Everything picked so far, for any shape. Only the current shape's needed slots are kept on save.
    fun draft() = Combo(
        bladeId = blade, lockChipId = lock, mainBladeId = main, metalBladeId = metal, overBladeId = over,
        assistBladeId = assist, ratchetId = ratchet, bitId = bit, type = type ?: BeyType.BALANCE,
    )
    fun slotValue(k: PartKind): Long? = draft().partFor(k)
    val slots = neededSlots(shape, draft(), partMap)
    fun firstEmpty(): PartKind? = neededSlots(shape, draft(), partMap).firstOrNull { slotValue(it) == null }

    fun pick(k: PartKind, id: Long?) {
        when (k) {
            PartKind.BLADE -> blade = id
            PartKind.LOCK_CHIP -> lock = id
            PartKind.MAIN_BLADE -> main = id
            PartKind.METAL_BLADE -> metal = id
            PartKind.OVER_BLADE -> over = id
            PartKind.ASSIST_BLADE -> assist = id
            PartKind.RATCHET -> ratchet = id
            PartKind.BIT -> bit = id
        }
        val part = id?.let { partMap[it] }
        // A built-in ratchet or bit means that slot is filled by this part.
        if (part?.builtInRatchet == true && k == PartKind.BLADE) ratchet = null
        if (part?.builtInBit == true && k == PartKind.RATCHET) bit = null
        if (part != null && type == null && k in listOf(PartKind.BLADE, PartKind.MAIN_BLADE, PartKind.METAL_BLADE)) type = part.type
        active = firstEmpty()
    }

    // Load an existing combo, or pre-fill the part you came from.
    LaunchedEffect(existing, parts) {
        if (initialized) return@LaunchedEffect
        if (existing != null) {
            shape = existing.shape()
            blade = existing.bladeId; lock = existing.lockChipId; main = existing.mainBladeId
            metal = existing.metalBladeId; over = existing.overBladeId; assist = existing.assistBladeId
            ratchet = existing.ratchetId; bit = existing.bitId
            type = existing.type; favorite = existing.favorite; notes = existing.notes
            active = null
            initialized = true
        } else if (comboId == null) {
            val start = startPartId?.let { partMap[it] }
            if (startPartId != null && start == null) return@LaunchedEffect // parts still loading
            if (start != null) {
                shape = shapeFor(start.kind)
                pick(start.kind, start.id)
            } else {
                active = slots.first()
            }
            initialized = true
        }
    }

    val complete = draft().isComplete(shape, partMap)
    val usage = remember(combos) { usageCounts(combos.filter { it.id != comboId }) }
    val previewName = comboName(draft(), partMap, shape)

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(stringResource(if (existing == null) R.string.combo_new else R.string.combo_edit), onBack = onDone, actions = {
                IconButton(onClick = { favorite = !favorite }) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.combo_favorite_cd),
                        tint = if (favorite) warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.combo_delete_cd)) }
                }
            })
        },
        bottomBar = {
            Button(
                onClick = {
                    vm.saveCombo(
                        draft().normalized(shape, partMap).copy(
                            id = existing?.id ?: 0L,
                            favorite = favorite,
                            notes = notes.trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                        onDone,
                    )
                },
                enabled = complete,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(if (complete) stringResource(R.string.combo_save_name, previewName) else stringResource(R.string.combo_pick_every_part), fontWeight = FontWeight.Bold, maxLines = 1) }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ComboShape.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = shape == s,
                            onClick = { shape = s; active = firstEmpty() },
                            shape = SegmentedButtonDefaults.itemShape(i, ComboShape.entries.size),
                            icon = {},
                        ) { Text(if (s == ComboShape.BASIC) stringResource(R.string.combo_basic) else s.label, maxLines = 1) }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEachIndexed { i, k ->
                        val part = slotValue(k)?.let { partMap[it] }
                        val isActive = active == k
                        Surface(
                            onClick = { active = k },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}",
                                    modifier = Modifier.width(24.dp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                PartPhoto(part?.photoPath, 40.dp, shape = RoundedCornerShape(8.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(k.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(part?.name ?: stringResource(R.string.combo_choose_part, k.label.lowercase()), fontWeight = FontWeight.Bold)
                                }
                                if (part != null) Icon(Icons.Default.Check, contentDescription = null, tint = winColor())
                            }
                        }
                    }
                    // Slots filled by a built-in part: shown so it's clear why they're missing.
                    val bladePart = blade?.let { partMap[it] }
                    val ratchetPart = ratchet?.let { partMap[it] }
                    listOfNotNull(
                        (PartKind.RATCHET to bladePart).takeIf { shape == ComboShape.BASIC && bladePart?.builtInRatchet == true },
                        (PartKind.BIT to ratchetPart).takeIf { PartKind.RATCHET in slots && ratchetPart?.builtInBit == true },
                    ).forEach { (kind, host) ->
                        Text(
                            "${kind.label}: " + stringResource(R.string.combo_slot_built_in, host!!.name),
                            modifier = Modifier.padding(start = 34.dp, top = 2.dp),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(previewName, fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
            val activeKind = active
            if (activeKind != null) {
                val options = parts.filter { it.kind == activeKind }.sortedBy { usage[it.id] ?: 0 }
                item {
                    Text(
                        stringResource(R.string.combo_your_parts, activeKind.plural.lowercase()),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(options, key = { "opt_${it.id}" }) { p ->
                    val used = usage[p.id] ?: 0
                    val selected = slotValue(activeKind) == p.id
                    Row(
                        Modifier.fillMaxWidth().clickable { pick(activeKind, p.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PartPhoto(p.photoPath, 44.dp, shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (p.code.isNotBlank() && p.code != p.name) "  ${p.code}" else "", fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.combo_owned_line,
                                    p.copies,
                                    if (used == 0) stringResource(R.string.combo_free) else pluralStringResource(R.plurals.combo_in_other_combos, used, used),
                                ),
                                fontSize = 12.sp,
                                color = if (used < p.copies) winColor() else warnColor(),
                            )
                        }
                        if (selected) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.combo_selected_cd), tint = MaterialTheme.colorScheme.secondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    TextButton(onClick = { onAddPart(activeKind) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.combo_add_new_part, activeKind.label.lowercase()))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.combo_type), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeyType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.combo_notes)) },
                        placeholder = { Text(stringResource(R.string.combo_notes_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.combo_delete_title)) },
            text = { Text(stringResource(R.string.combo_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteCombo(existing); onDone() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
