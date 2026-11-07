package com.bxtracker.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Catalog
import com.bxtracker.app.data.CatalogEntry
import com.bxtracker.app.data.ProductLine
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.bxtracker.app.data.Part
import com.bxtracker.app.data.PartKind
import com.bxtracker.app.data.PhotoStore
import com.bxtracker.app.data.comboName
import com.bxtracker.app.data.comboRecords
import com.bxtracker.app.data.partIds
import com.bxtracker.app.data.usageCounts
import com.bxtracker.app.ui.components.FitText
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.PartPhoto
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.components.TypeTag
import com.bxtracker.app.ui.theme.warnColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val tabKinds = listOf(
    listOf(PartKind.BLADE),
    listOf(PartKind.RATCHET),
    listOf(PartKind.BIT),
    listOf(PartKind.LOCK_CHIP, PartKind.MAIN_BLADE, PartKind.METAL_BLADE, PartKind.OVER_BLADE, PartKind.ASSIST_BLADE),
)

/** Tab names are game terms and stay in English in every language. */
private val tabNames = listOf("Blades", "Ratchets", "Bits", "CX")

/** Filter chips. The filter state is the enum; only the label shown is localized. */
private enum class PartFilter { ALL, UNUSED, NO_PHOTO, ATTACK, DEFENSE, STAMINA, LOCK_CHIPS, MAIN, METAL, OVER, ASSIST }

@Composable
private fun PartFilter.label(): String = when (this) {
    PartFilter.ALL -> stringResource(R.string.parts_filter_all)
    PartFilter.UNUSED -> stringResource(R.string.parts_unused)
    PartFilter.NO_PHOTO -> stringResource(R.string.no_photo)
    PartFilter.ATTACK -> BeyType.ATTACK.label
    PartFilter.DEFENSE -> BeyType.DEFENSE.label
    PartFilter.STAMINA -> BeyType.STAMINA.label
    PartFilter.LOCK_CHIPS -> PartKind.LOCK_CHIP.plural
    // Game terms (short for Main Blade / Assist Blade), English in every language.
    PartFilter.MAIN -> "Main"
    PartFilter.METAL -> "Metal"
    PartFilter.OVER -> "Over"
    PartFilter.ASSIST -> "Assist"
}

/** "Unused" / "In 1 combo" / "In 3 combos". */
@Composable
private fun usedText(used: Int): String =
    if (used == 0) stringResource(R.string.parts_unused) else pluralStringResource(R.plurals.parts_in_combos, used, used)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(vm: AppViewModel, onPart: (Long) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PartFilter.ALL) }
    val usage = remember(combos) { usageCounts(combos) }
    val filters = when (tab) {
        0 -> listOf(PartFilter.ALL, PartFilter.UNUSED, PartFilter.NO_PHOTO, PartFilter.ATTACK, PartFilter.DEFENSE, PartFilter.STAMINA)
        3 -> listOf(PartFilter.ALL, PartFilter.LOCK_CHIPS, PartFilter.MAIN, PartFilter.METAL, PartFilter.OVER, PartFilter.ASSIST, PartFilter.UNUSED, PartFilter.NO_PHOTO)
        else -> listOf(PartFilter.ALL, PartFilter.UNUSED, PartFilter.NO_PHOTO)
    }
    if (filter !in filters) filter = PartFilter.ALL

    val shown = parts
        .filter { it.kind in tabKinds[tab] }
        .filter { query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) }
        .filter {
            when (filter) {
                PartFilter.ALL -> true
                PartFilter.UNUSED -> (usage[it.id] ?: 0) == 0
                PartFilter.NO_PHOTO -> it.photoPath == null
                PartFilter.ATTACK -> it.type == BeyType.ATTACK
                PartFilter.DEFENSE -> it.type == BeyType.DEFENSE
                PartFilter.STAMINA -> it.type == BeyType.STAMINA
                PartFilter.LOCK_CHIPS -> it.kind == PartKind.LOCK_CHIP
                PartFilter.MAIN -> it.kind == PartKind.MAIN_BLADE
                PartFilter.METAL -> it.kind == PartKind.METAL_BLADE
                PartFilter.OVER -> it.kind == PartKind.OVER_BLADE
                PartFilter.ASSIST -> it.kind == PartKind.ASSIST_BLADE
            }
        }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(stringResource(R.string.tab_parts), subtitle = stringResource(R.string.parts_subtitle), actions = {
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings)) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.parts_add_part)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabKinds.forEachIndexed { i, kinds ->
                        AppCard(Modifier.weight(1f), padding = PaddingValues(10.dp)) {
                            Text(
                                parts.filter { it.kind in kinds }.sumOf { it.copies }.toString(),
                                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            )
                            FitText(tabNames[i], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    tabNames.forEachIndexed { i, name ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { tab = i },
                            shape = SegmentedButtonDefaults.itemShape(i, tabNames.size),
                            icon = {},
                        ) { FitText(name) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = {
                        Text(
                            if (tab == 3) stringResource(R.string.parts_search_cx)
                            else stringResource(R.string.parts_search_x, tabNames[tab].lowercase())
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label()) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.GridView,
                        title = stringResource(if (parts.isEmpty()) R.string.parts_empty_title else R.string.parts_nothing_here),
                        body = stringResource(if (parts.isEmpty()) R.string.parts_empty_body else R.string.parts_nothing_here_body),
                        actionLabel = if (parts.isEmpty()) stringResource(R.string.parts_empty_action) else null,
                        onAction = onAdd,
                    )
                }
            } else if (tab == 3) {
                // CX parts: a 3-column photo grid, grouped by kind.
                tabKinds[3].forEach { kind ->
                    val list = shown.filter { it.kind == kind }
                    if (list.isNotEmpty()) {
                        item(key = "h_$kind") { SectionLabel(kind.plural) }
                        items(list.chunked(3), key = { row -> "g_${row.first().id}" }) { row ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { p -> CxTile(p, usage[p.id] ?: 0, Modifier.weight(1f)) { onPart(p.id) } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            } else {
                items(shown, key = { it.id }) { PartRow(it, usage[it.id] ?: 0) { onPart(it.id) } }
            }
        }
    }
}

@Composable
private fun CxTile(part: Part, used: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    PartPhoto(part.photoPath, maxWidth, shape = RoundedCornerShape(10.dp), contentDescription = stringResource(R.string.parts_photo_of, part.name))
                }
                Text(
                    "×${part.copies}",
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(part.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if ((part.kind == PartKind.ASSIST_BLADE || part.kind == PartKind.OVER_BLADE) && part.code.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(part.code, fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Text(
                usedText(used),
                fontSize = 11.sp,
                color = if (used == 0) warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (used == 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun PartRow(part: Part, used: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PartPhoto(part.photoPath, 56.dp, contentDescription = stringResource(R.string.parts_photo_of, part.name))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(part.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (part.code.isNotBlank() && part.code != part.name) {
                    Spacer(Modifier.width(6.dp))
                    Text(part.code, fontFamily = Mono, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeTag(part.type)
                if (part.leftSpin) Text(stringResource(R.string.parts_left_spin_dot), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    usedText(used),
                    fontSize = 12.sp,
                    color = if (used == 0) warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (used == 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Text("×${part.copies}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Opens the in-app camera, the gallery, or (as a fallback) the phone's camera app. Photos land in app storage. */
private class PhotoPicker(val takePhoto: () -> Unit, val pickFromGallery: () -> Unit)

/**
 * [kind] and [onKind] show the kind strip in the camera (Add part only). [onSkip] runs when the camera is
 * closed without a photo.
 */
@Composable
private fun rememberPhotoPicker(
    maxSide: Int,
    kind: PartKind? = null,
    onKind: ((PartKind) -> Unit)? = null,
    onSkip: () -> Unit = {},
    onPhoto: (String) -> Unit,
): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }

    val systemCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath ?: return@rememberLauncherForActivityResult
        if (ok) {
            scope.launch {
                withContext(Dispatchers.IO) { PhotoStore.shrink(File(path), maxSide) }
                onPhoto(path)
            }
        } else {
            PhotoStore.delete(path)
        }
        pendingPath = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { PhotoStore.copyFromUri(context, uri, maxSide) }
                if (file != null) onPhoto(file.absolutePath)
            }
        }
    }
    val openGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    if (cameraOpen) {
        CameraCapture(
            kind = kind,
            onKindChange = onKind,
            maxSide = maxSide,
            onPhoto = { cameraOpen = false; onPhoto(it) },
            onGallery = { cameraOpen = false; openGallery() },
            onSystemCamera = {
                cameraOpen = false
                val file = PhotoStore.newFile(context)
                pendingPath = file.absolutePath
                runCatching { systemCamera.launch(PhotoStore.uriFor(context, file)) }.onFailure {
                    pendingPath = null
                    Toast.makeText(context, context.getString(R.string.parts_no_camera_app), Toast.LENGTH_LONG).show()
                }
            },
            onSkip = { cameraOpen = false; onSkip() },
        )
    }
    return remember(gallery) { PhotoPicker(takePhoto = { cameraOpen = true }, pickFromGallery = openGallery) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPartScreen(vm: AppViewModel, initialKind: PartKind?, onDone: () -> Unit) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(initialKind ?: PartKind.BLADE) }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<BeyType?>(null) }
    var leftSpin by rememberSaveable { mutableStateOf(false) }
    var builtInRatchet by rememberSaveable { mutableStateOf(false) }
    var builtInBit by rememberSaveable { mutableStateOf(false) }
    var copies by rememberSaveable { mutableIntStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var autoCameraDone by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()

    val picker = rememberPhotoPicker(settings.photoSize.maxSide, kind = kind, onKind = { kind = it }) { newPath ->
        PhotoStore.delete(photo)
        photo = newPath
    }
    val suggestions = remember(kind, name, parts) {
        val owned = parts.filter { it.kind == kind && name.isNotBlank() && it.name.contains(name.trim(), true) && !it.name.equals(name.trim(), true) }
            .map { CatalogEntry(it.name, it.code, it.type, it.leftSpin, it.builtInRatchet, it.builtInBit) }
        (owned + Catalog.suggest(vm.catalog, kind, name)).distinctBy { it.name.lowercase() }.take(6)
    }

    // Adding a part starts with the camera, so every part gets a photo.
    LaunchedEffect(Unit) {
        if (!autoCameraDone) {
            autoCameraDone = true
            picker.takePhoto()
        }
    }

    val existing = parts.firstOrNull { it.kind == kind && name.isNotBlank() && it.name.trim().equals(name.trim(), true) }
    val hasType = kind == PartKind.BLADE || kind == PartKind.MAIN_BLADE || kind == PartKind.METAL_BLADE
    // Ratchets use their name as the code ("9-60"), except Turbo-style ones that have their own ("Tr").
    val hasCode = kind == PartKind.BIT || kind == PartKind.ASSIST_BLADE || kind == PartKind.OVER_BLADE || (kind == PartKind.RATCHET && builtInBit)

    fun reset() {
        name = ""; code = ""; type = null; leftSpin = false; builtInRatchet = false; builtInBit = false; copies = 1; note = ""; photo = null
    }

    fun save(next: Boolean) {
        val part = Part(
            kind = kind,
            name = name.trim(),
            code = when {
                kind == PartKind.RATCHET && !builtInBit -> name.trim()
                kind == PartKind.RATCHET -> code.trim().ifBlank { name.trim() }
                else -> code.trim()
            },
            type = if (hasType) type else null,
            leftSpin = hasType && leftSpin,
            builtInRatchet = kind == PartKind.BLADE && builtInRatchet,
            builtInBit = kind == PartKind.RATCHET && builtInBit,
            photoPath = photo,
            copies = copies,
            note = note.trim(),
        )
        saved = true
        vm.addPart(part) {
            if (next) {
                reset()
                saved = false
                picker.takePhoto()
            } else onDone()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(stringResource(R.string.parts_new_part), onBack = {
                if (!saved) PhotoStore.delete(photo)
                onDone()
            })
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { save(next = true) }, enabled = name.isNotBlank(), modifier = Modifier.height(52.dp)) {
                    Text(stringResource(R.string.parts_save_next))
                }
                Button(onClick = { save(next = false) }, enabled = name.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.parts_add_to_collection), fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PartPhoto(
                    photo, 112.dp, shape = CircleShape,
                    modifier = Modifier.clickable { picker.takePhoto() },
                    contentDescription = stringResource(R.string.parts_part_photo),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(if (photo == null) R.string.parts_no_photo_yet else R.string.parts_photo_saved), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.parts_photo_tip),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = picker.takePhoto) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (photo == null) R.string.parts_camera else R.string.parts_retake))
                        }
                        TextButton(onClick = picker.pickFromGallery) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.parts_gallery))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.parts_what_is_it), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PartKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(if (kind == PartKind.RATCHET) R.string.parts_field_ratchet else R.string.parts_field_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                supportingText = if (existing != null) {
                    { Text(stringResource(R.string.parts_already_have, existing.name, existing.copies), color = warnColor()) }
                } else null,
            )
            if (suggestions.isNotEmpty()) {
                Column(Modifier.offset(y = (-8).dp)) {
                    suggestions.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                name = s.name
                                if (s.code.isNotBlank()) code = s.code
                                if (s.type != null) type = s.type
                                leftSpin = s.leftSpin
                                builtInRatchet = s.builtInRatchet
                                builtInBit = s.builtInBit
                            }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (s.code.isNotBlank() && s.code != s.name) {
                                Text(s.code, fontFamily = Mono, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(8.dp))
                            }
                            TypeTag(s.type)
                        }
                    }
                }
            }
            if (hasCode) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(4) },
                    label = { Text(stringResource(when (kind) { PartKind.BIT -> R.string.parts_field_bit_code; PartKind.RATCHET -> R.string.parts_field_ratchet_code; else -> R.string.parts_field_assist_letter })) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.parts_code_hint)) },
                )
            }
            if (hasType) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.parts_type), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeyType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = if (type == t) null else t }, label = { Text(t.label) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.parts_left_spin), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = leftSpin, onCheckedChange = { leftSpin = it })
                }
            }
            if (kind == PartKind.BLADE) BuiltInSwitch(kind, builtInRatchet) { builtInRatchet = it }
            if (kind == PartKind.RATCHET) BuiltInSwitch(kind, builtInBit) { builtInBit = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.parts_copies), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Stepper(copies, { copies = it }, min = 1, label = stringResource(R.string.parts_copies_label))
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.parts_note_optional)) },
                placeholder = { Text(stringResource(R.string.parts_note_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartDetailScreen(
    vm: AppViewModel,
    partId: Long,
    onBack: () -> Unit,
    onCombo: (Long) -> Unit,
    onBuildWith: (Long) -> Unit,
) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val part = parts.firstOrNull { it.id == partId }
    if (part == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.parts_part), onBack = onBack) }) { pad ->
            Column(Modifier.padding(pad)) {}
        }
        return
    }

    var name by rememberSaveable(part.id) { mutableStateOf(part.name) }
    var code by rememberSaveable(part.id) { mutableStateOf(part.code) }
    var weight by rememberSaveable(part.id) { mutableStateOf(part.weight) }
    var note by rememberSaveable(part.id) { mutableStateOf(part.note) }
    var source by rememberSaveable(part.id) { mutableStateOf(part.source) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dirty = name != part.name || code != part.code || weight != part.weight || note != part.note || source != part.source
    val settings by vm.settings.collectAsStateWithLifecycle()

    val picker = rememberPhotoPicker(settings.photoSize.maxSide) { newPath ->
        PhotoStore.delete(part.photoPath)
        vm.updatePart(part.copy(photoPath = newPath))
    }
    val partMap = remember(parts) { parts.associateBy { it.id } }
    val records = remember(points) { comboRecords(points) }
    val usedIn = combos.filter { part.id in it.partIds() }
    val canBuild = part.kind != PartKind.RATCHET && part.kind != PartKind.BIT

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(part.kind.label, onBack = onBack, actions = {
                if (dirty) {
                    IconButton(onClick = {
                        vm.updatePart(
                            part.copy(
                                name = name.trim().ifBlank { part.name },
                                code = when {
                                    part.kind == PartKind.RATCHET && !part.builtInBit -> name.trim()
                                    part.kind == PartKind.RATCHET -> code.trim().ifBlank { name.trim() }
                                    else -> code.trim()
                                },
                                weight = weight.trim(),
                                note = note.trim(),
                                source = source.trim(),
                            )
                        )
                    }) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.parts_save_changes)) }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.parts_delete_part)) }
            })
        },
        bottomBar = {
            Button(
                onClick = { onBuildWith(part.id) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(stringResource(R.string.parts_build_with), fontWeight = FontWeight.Bold) }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (dirty) {
                Text(stringResource(R.string.parts_unsaved), fontSize = 13.sp, color = warnColor())
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PartPhoto(
                    part.photoPath, 160.dp, shape = CircleShape,
                    modifier = Modifier.clickable { picker.takePhoto() },
                    contentDescription = stringResource(R.string.parts_photo_of_retake, part.name),
                )
                Row {
                    TextButton(onClick = picker.takePhoto) { Text(stringResource(if (part.photoPath == null) R.string.parts_take_photo else R.string.parts_retake)) }
                    TextButton(onClick = picker.pickFromGallery) { Text(stringResource(R.string.parts_gallery)) }
                }
            }
            if (part.kind == PartKind.BLADE || part.kind == PartKind.MAIN_BLADE || part.kind == PartKind.METAL_BLADE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeyType.entries.forEach { t ->
                        FilterChip(
                            selected = part.type == t,
                            onClick = { vm.updatePart(part.copy(type = if (part.type == t) null else t)) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            if (part.kind == PartKind.BLADE) BuiltInSwitch(part.kind, part.builtInRatchet) { vm.updatePart(part.copy(builtInRatchet = it)) }
            if (part.kind == PartKind.RATCHET) BuiltInSwitch(part.kind, part.builtInBit) { vm.updatePart(part.copy(builtInBit = it)) }
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.parts_copies_owned), fontWeight = FontWeight.Bold)
                        Text(
                            pluralStringResource(R.plurals.parts_used_in_combos, usedIn.size, usedIn.size),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Stepper(part.copies, { vm.updatePart(part.copy(copies = it)) }, min = 0, label = stringResource(R.string.parts_copies_label))
                }
                if (usedIn.size > part.copies) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.parts_overused_warning,
                            pluralStringResource(R.plurals.parts_n_copies, part.copies, part.copies),
                            pluralStringResource(R.plurals.parts_n_combos, usedIn.size, usedIn.size),
                        ),
                        fontSize = 13.sp, color = warnColor(),
                    )
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.parts_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (part.kind == PartKind.BIT || part.kind == PartKind.ASSIST_BLADE || part.kind == PartKind.OVER_BLADE || (part.kind == PartKind.RATCHET && part.builtInBit)) {
                OutlinedTextField(code, { code = it.take(4) }, label = { Text(stringResource(R.string.parts_field_short_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                weight, { weight = it }, label = { Text(stringResource(R.string.parts_weight)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.parts_line), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductLine.entries.forEach { l ->
                        FilterChip(
                            selected = part.line == l,
                            onClick = { vm.updatePart(part.copy(line = if (part.line == l) null else l)) },
                            label = { Text(l.label) },
                        )
                    }
                }
            }
            OutlinedTextField(
                source, { source = it }, label = { Text(stringResource(R.string.parts_source)) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.parts_source_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.parts_note)) }, modifier = Modifier.fillMaxWidth())

            Text(stringResource(R.string.parts_used_in), fontWeight = FontWeight.Bold)
            if (usedIn.isEmpty()) {
                Text(
                    stringResource(if (canBuild) R.string.parts_not_in_combo else R.string.parts_not_in_combo_build),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
            }
            usedIn.forEach { combo ->
                val rec = records[combo.id]
                AppCard(Modifier.fillMaxWidth().clickable { onCombo(combo.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comboName(combo, partMap), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        if (rec != null && rec.total > 0) {
                            Text("${rec.percent}%", fontFamily = Mono, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.parts_delete_title, part.name)) },
            text = {
                Text(
                    if (usedIn.isEmpty()) stringResource(R.string.parts_delete_photo_too)
                    else pluralStringResource(R.plurals.parts_delete_used_in, usedIn.size, usedIn.size)
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePart(part); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Blades: "built-in ratchet" (combos skip the ratchet). Ratchets: "includes the bit" like Turbo (combos skip the bit). */
@Composable
private fun BuiltInSwitch(kind: PartKind, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ratchet = kind == PartKind.BLADE
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (ratchet) R.string.parts_builtin_ratchet else R.string.parts_builtin_bit),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(if (ratchet) R.string.parts_builtin_ratchet_desc else R.string.parts_builtin_bit_desc),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
