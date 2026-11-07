package com.bxtracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.DataResult
import com.bxtracker.app.R
import com.bxtracker.app.data.Backup
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.PhotoSize
import com.bxtracker.app.data.ThemeMode
import com.bxtracker.app.ui.AppLanguage
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.DateField
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.Stepper
import com.bxtracker.app.ui.setAppLanguage
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var language by remember { mutableStateOf(AppLanguage.current()) }
    val context = LocalContext.current
    fun report(result: DataResult) {
        busy = false
        val msg = when (result) {
            is DataResult.BackedUp -> context.getString(R.string.settings_backed_up, result.parts, result.combos, result.matches)
            is DataResult.Restored -> context.getString(R.string.settings_restored, result.parts, result.combos, result.matches)
            is DataResult.CsvSaved -> context.getString(R.string.settings_csv_saved, result.files.joinToString(", "))
            is DataResult.Failed -> if (result.notABackup) context.getString(R.string.settings_not_a_backup) else context.getString(
                when (result.action) {
                    DataResult.Action.BACKUP -> R.string.settings_backup_failed
                    DataResult.Action.RESTORE -> R.string.settings_restore_failed
                    DataResult.Action.CSV -> R.string.settings_export_failed
                },
                result.error.message ?: result.error.javaClass.simpleName,
            )
        }
        scope.launch { snackbar.showSnackbar(msg) }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { busy = true; vm.exportBackup(uri, ::report) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { busy = true; vm.exportCsv(uri, ::report) }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(R.string.settings), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionLabel(stringResource(R.string.settings_appearance)) }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreview(
                        stringResource(R.string.theme_light), light = true, selected = settings.theme == ThemeMode.LIGHT,
                        modifier = Modifier.weight(1f),
                    ) { vm.setTheme(ThemeMode.LIGHT) }
                    ThemePreview(
                        stringResource(R.string.theme_oled), light = false, selected = settings.theme == ThemeMode.OLED,
                        modifier = Modifier.weight(1f),
                    ) { vm.setTheme(ThemeMode.OLED) }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), padding = PaddingValues(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(stringResource(mode.label), stringResource(mode.description), settings.theme == mode) { vm.setTheme(mode) }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_language), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        RadioRow(stringResource(lang.label), null, language == lang) {
                            if (language != lang) {
                                language = lang
                                setAppLanguage(lang)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_language_note),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionLabel(stringResource(R.string.settings_match_rules), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_points_to_win), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_points_to_win_default), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Stepper(settings.pointsToWin, { vm.setPointsToWin(it) }, min = 1, max = 20, label = stringResource(R.string.settings_points_to_win_label))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.settings_points_per_finish), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_points_per_finish_sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Finish.entries.forEach { f ->
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.label, modifier = Modifier.weight(1f))
                            Stepper(settings.pointsFor(f), { vm.setFinishPoints(f, it) }, min = 0, max = 9, label = stringResource(R.string.settings_finish_points_label, f.label))
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_season), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    DateField(stringResource(R.string.settings_season_start), settings.seasonStart, { vm.setSeasonStart(it) }, clearable = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_season_note, stringResource(R.string.period_season)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.settings_photos), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
                    PhotoSize.entries.forEach { size ->
                        RadioRow(stringResource(size.label), stringResource(size.description), settings.photoSize == size) { vm.setPhotoSize(size) }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.settings_your_data), Modifier.padding(top = 12.dp)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
                    ActionRow(Icons.Outlined.Backup, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_sub), enabled = !busy) {
                        backupLauncher.launch(Backup.suggestedName())
                    }
                    ActionRow(Icons.Outlined.Restore, stringResource(R.string.settings_restore), stringResource(R.string.settings_restore_sub), enabled = !busy) {
                        restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                    }
                    ActionRow(Icons.Outlined.TableChart, stringResource(R.string.settings_export_csv), stringResource(R.string.settings_export_csv_sub), enabled = !busy) {
                        csvLauncher.launch(null)
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_footer),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_body)) },
            confirmButton = {
                TextButton(onClick = { pendingRestore = null; busy = true; vm.importBackup(uri, ::report) }) {
                    Text(stringResource(R.string.settings_restore_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A tiny mock of the app in each theme, using the real theme colors. */
@Composable
private fun ThemePreview(label: String, light: Boolean, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (light) Color(0xFFF7F9FB) else Color.Black
    val card = if (light) Color.White else Color(0xFF0D1117)
    val line = if (light) Color(0xFFE1E7EE) else Color(0xFF21262D)
    val text = if (light) Color(0xFF0B1E2B) else Color(0xFFF3F7FB)
    val muted = if (light) Color(0xFF4A5666) else Color(0xFFA9B8C7)
    val primary = if (light) Color(0xFF0F729F) else Color(0xFF179CD8)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(8.dp)) {
            Column(
                Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.width(44.dp).height(7.dp).clip(CircleShape).background(text))
                repeat(2) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(card).padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(line))
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(muted))
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.width(14.dp).height(5.dp).clip(CircleShape).background(primary))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
