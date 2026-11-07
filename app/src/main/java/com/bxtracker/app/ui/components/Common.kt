package com.bxtracker.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.bxtracker.app.data.BeyType
import com.bxtracker.app.data.Finish
import com.bxtracker.app.data.Record
import com.bxtracker.app.ui.theme.LocalIsDark
import com.bxtracker.app.ui.theme.lossColor
import com.bxtracker.app.ui.theme.typeTint
import com.bxtracker.app.ui.theme.winColor
import androidx.compose.ui.res.stringResource
import com.bxtracker.app.R
import com.bxtracker.app.ui.pickedDay
import java.io.File

val NoInsets = WindowInsets(0, 0, 0, 0)
val Mono = FontFamily.Monospace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BxTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            }
        },
        actions = actions,
        windowInsets = NoInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = border,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun TypeTag(type: BeyType?) {
    if (type == null) return
    val tint = typeTint(type)
    Text(
        type.label.uppercase(),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = tint.fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
}

/** The part's photo, or a camera placeholder that nudges you to add one. */
@Composable
fun PartPhoto(
    path: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentDescription: String? = null,
) {
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = contentDescription ?: stringResource(R.string.no_photo),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(if (size > 80.dp) 32.dp else 20.dp),
                )
                if (size >= 48.dp) {
                    Text(stringResource(R.string.photo_add), color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 99, label: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedIconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.less_x, label))
        }
        Text(
            value.toString(),
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        OutlinedIconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.more_x, label))
        }
    }
}

@Composable
fun RecordBar(record: Record, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.record_wl, record.wins, record.losses), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        LinearProgressIndicator(
            progress = { record.rate },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(10.dp))
        Text("${record.percent}%", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    AppCard(modifier.fillMaxHeight(), padding = PaddingValues(12.dp)) {
        Text(value, fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** W, L or a dot for a match still being played. */
@Composable
fun ResultBadge(outcome: Boolean?, size: Dp = 36.dp) {
    val (text, color) = when (outcome) {
        true -> stringResource(R.string.common_badge_win) to winColor()
        false -> stringResource(R.string.common_badge_loss) to lossColor()
        null -> "•" to MaterialTheme.colorScheme.secondary
    }
    val description = stringResource(
        when (outcome) {
            true -> R.string.result_won
            false -> R.string.result_lost
            null -> R.string.result_in_progress
        }
    )
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontWeight = FontWeight.ExtraBold) }
}

/** Finish order used by every finish chart: biggest finishes first. */
val finishOrder = listOf(Finish.XTREME, Finish.OVER, Finish.BURST, Finish.SPIN)

/** One hue at four lightness steps, so the segments read apart without relying on color alone. */
@Composable
fun finishShades(): List<Color> = if (LocalIsDark.current) {
    listOf(Color(0xFFC9EBF9), Color(0xFF74C6EA), Color(0xFF179CD8), Color(0xFF0E5679))
} else {
    listOf(Color(0xFF062A3C), Color(0xFF0F729F), Color(0xFF52B5E2), Color(0xFFB3DEF2))
}

/** Stacked bar of points by finish, with a legend that gives each share. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinishBar(breakdown: Map<Finish, Int>) {
    val total = breakdown.values.sum().coerceAtLeast(1)
    val shades = finishShades()
    val gap = MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(gap)
            .semantics { contentDescription = finishOrder.joinToString { "${it.label} ${(breakdown[it] ?: 0) * 100 / total}%" } },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        finishOrder.forEachIndexed { i, f ->
            val v = breakdown[f] ?: 0
            if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(shades[i]))
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        finishOrder.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(shades[i]))
                Spacer(Modifier.width(4.dp))
                Text("${f.label} ${(breakdown[f] ?: 0) * 100 / total}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A labeled horizontal bar, e.g. "Phoenix Wing 9-60GF  8/10". */
@Composable
fun LabeledBar(label: String, value: String, fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

/** A read-only field that opens a date picker. Dates are handled as UTC midnight, like the Material picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, millis: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val chooseLabel = stringResource(R.string.choose_x, label)
    Box(modifier) {
        OutlinedTextField(
            value = if (millis != null) pickedDay(millis) else stringResource(R.string.not_set),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field swallows taps, so a transparent layer on top opens the picker.
        Box(Modifier.matchParentSize().clickable(onClickLabel = chooseLabel) { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(state.selectedDateMillis); open = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                Row {
                    if (clearable) TextButton(onClick = { onChange(null); open = false }) { Text(stringResource(R.string.clear)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) { DatePicker(state) }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * One line of text that shrinks (down to [minFontSize]) instead of wrapping or clipping, for labels in
 * fixed-width tabs and tiles. Narrow phones (360 dp) and Spanish labels need it.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 9.sp,
) {
    val base = LocalTextStyle.current
    val max = if (fontSize.isSpecified) fontSize else if (base.fontSize.isSpecified) base.fontSize else 14.sp
    val resolved = if (color.isSpecified) color else base.color.takeOrElse { LocalContentColor.current }
    BasicText(
        text = text,
        modifier = modifier,
        style = base.merge(TextStyle(color = resolved, fontWeight = fontWeight)),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = max, stepSize = 0.5.sp),
    )
}
