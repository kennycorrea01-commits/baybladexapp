package com.bxtracker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.ComboShape
import com.bxtracker.app.data.PartOwnership
import com.bxtracker.app.data.Product
import com.bxtracker.app.data.ProductMatch
import com.bxtracker.app.data.Products
import com.bxtracker.app.data.StockBuild
import com.bxtracker.app.data.partIds
import com.bxtracker.app.ui.components.AppCard
import com.bxtracker.app.ui.components.BxTopBar
import com.bxtracker.app.ui.components.EmptyState
import com.bxtracker.app.ui.components.FitText
import com.bxtracker.app.ui.components.Mono
import com.bxtracker.app.ui.components.NoInsets
import com.bxtracker.app.ui.components.PartPhoto
import com.bxtracker.app.ui.components.SectionLabel
import com.bxtracker.app.ui.components.TypeTag
import com.bxtracker.app.ui.pickedDay
import com.bxtracker.app.ui.theme.warnColor
import com.bxtracker.app.ui.theme.winColor
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * Products (screens 21 and 22): the "By product" tab of Add parts, the lookup-only product search, and the
 * product lookup page. Product codes, product names and combo names come from the bundled catalog and are
 * shown as they are in every language.
 */

// Shared pieces (By product tab and lookup)

/** The product search field. What the user types or pastes goes through [Products.queryText] in the search. */
@Composable
internal fun ProductSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.prod_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.prod_clear_search)) } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

/** Stable LazyColumn key for a search hit. */
internal fun matchKey(m: ProductMatch): String = "${m.product.label}|${m.build?.combo.orEmpty()}"

/** Search results without duplicate keys (a catalog with a repeated code or combo must not crash the list). */
internal fun searchProducts(products: List<Product>, query: String): List<ProductMatch> =
    Products.search(products, query).distinctBy(::matchKey)

/** The build a hit stands for: the matched build, or the only build of a single-Bey product. */
internal fun ProductMatch.buildOrSingle(): StockBuild? = build ?: product.builds.singleOrNull()

/**
 * A search hit: product code, product name, and the stock combo (or "3 Beys — pick one" for sets and random
 * boosters). [expanded] is drawn under the header when the card is selected.
 */
@Composable
internal fun ProductResultCard(
    match: ProductMatch,
    selected: Boolean,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    expanded: @Composable ColumnScope.() -> Unit = {},
) {
    val p = match.product
    val build = match.buildOrSingle()
    AppCard(
        modifier.fillMaxWidth(),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = onClickLabel, onClick = onClick)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.code, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    if (build != null) build.combo
                    else pluralStringResource(R.plurals.prod_n_beys_pick_one, p.builds.size, p.builds.size),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (build != null) {
                Spacer(Modifier.width(8.dp))
                TypeTag(build.type)
            }
        }
        expanded()
    }
}

/** Chips to pick one Bey of a set or random booster. Shows nothing for single-Bey products. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildPicker(product: Product, selected: StockBuild?, onSelect: (StockBuild) -> Unit, modifier: Modifier = Modifier) {
    if (product.builds.size < 2) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.prod_pick_bey), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            product.builds.forEach { b ->
                FilterChip(selected = b == selected, onClick = { onSelect(b) }, label = { Text(b.combo) })
            }
        }
    }
}

/** "Own ×2" (win color), or [notOwned] ("New" / "Missing", warning color). */
@Composable
internal fun OwnBadge(o: PartOwnership, notOwned: String) {
    val owned = o.owned
    val own = o.owns && owned != null
    val color = if (own) winColor() else warnColor()
    Text(
        if (own) stringResource(R.string.prod_own_n, owned!!.copies) else notOwned,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** One stock part: kind, name and code, plus the ownership badge. With [photo], the part's photo (or the Add placeholder). */
@Composable
internal fun StockPartRow(o: PartOwnership, notOwned: String, photo: Boolean, onClick: (() -> Unit)? = null, onClickLabel: String? = null) {
    val s = o.stock
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .heightIn(min = 44.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo) {
            PartPhoto(
                o.owned?.takeIf { o.owns }?.photoPath, 48.dp,
                contentDescription = stringResource(R.string.parts_photo_of, s.name),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(s.kind.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.code.isNotBlank() && !s.code.equals(s.name, ignoreCase = true)) {
                    Spacer(Modifier.width(6.dp))
                    Text(s.code, fontFamily = Mono, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        OwnBadge(o, notOwned)
    }
}

/** "Not in the catalog", optionally with "Add it manually". */
@Composable
internal fun NotInCatalog(body: String, onManual: (() -> Unit)?) {
    EmptyState(
        icon = Icons.Outlined.Inventory2,
        title = stringResource(R.string.prod_not_in_catalog),
        body = body,
        actionLabel = if (onManual != null) stringResource(R.string.prod_add_manually) else null,
        onAction = onManual ?: {},
    )
}

/** The "how to search" line shown before anything is typed. */
@Composable
private fun SearchHint() {
    Text(
        stringResource(R.string.prod_search_hint),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

// Add parts › By product (screen 21)

/**
 * Search a product and add the parts it came with (no camera, no photos). Parts I own get +1 copy. [onManual]
 * switches to the Manual tab; [onAdded] runs after the parts were added.
 */
@Composable
internal fun ByProductTab(vm: AppViewModel, onManual: () -> Unit, onAdded: () -> Unit, modifier: Modifier = Modifier) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val products = vm.products
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var buildCombo by rememberSaveable { mutableStateOf<String?>(null) }
    var saveCombo by rememberSaveable { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    val results = remember(query, products) { searchProducts(products, query) }
    val newLabel = stringResource(R.string.prod_new)
    val showPartsLabel = stringResource(R.string.prod_show_parts)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            ProductSearchField(query, {
                query = it
                selected = null
                buildCombo = null
            })
        }
        when {
            products.isEmpty() -> item(key = "none") { NotInCatalog(stringResource(R.string.prod_catalog_empty_body), onManual) }
            query.isBlank() -> item(key = "hint") { SearchHint() }
            results.isEmpty() -> item(key = "none") { NotInCatalog(stringResource(R.string.prod_no_match_body, Products.queryText(query)), onManual) }
        }
        items(results, key = ::matchKey) { m ->
            val key = matchKey(m)
            val isSelected = selected == key
            ProductResultCard(
                match = m,
                selected = isSelected,
                onClickLabel = showPartsLabel,
                onClick = {
                    if (isSelected) {
                        selected = null
                    } else {
                        selected = key
                        buildCombo = m.buildOrSingle()?.combo
                    }
                },
            ) {
                if (isSelected) {
                    val build = m.product.builds.firstOrNull { it.combo == buildCombo }
                    Spacer(Modifier.height(8.dp))
                    BuildPicker(m.product, build, { buildCombo = it.combo }, Modifier.padding(bottom = 8.dp))
                    if (build != null) {
                        val own = remember(build, parts) { Products.ownership(build, parts) }
                        own.forEach { StockPartRow(it, newLabel, photo = false) }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .toggleable(value = saveCombo, role = Role.Checkbox, onValueChange = { saveCombo = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = saveCombo, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.prod_save_stock_combo), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            stringResource(R.string.prod_add_note),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val count = build.parts.size
                        Button(
                            onClick = {
                                adding = true
                                val product = m.product
                                vm.addProductBuild(product, build, saveCombo) { added ->
                                    adding = false
                                    Toast.makeText(
                                        context,
                                        context.resources.getQuantityString(R.plurals.prod_added_from, added, added, product.code),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onAdded()
                                }
                            },
                            enabled = !adding && count > 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Text(pluralStringResource(R.plurals.prod_add_from, count, count, m.product.code), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (results.isNotEmpty()) {
            item(key = "manual") {
                TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.prod_not_in_list))
                }
            }
        }
    }
}

// Product lookup (screen 22)

/** "Look up a product": the same search as Add parts › By product, but it adds nothing. */
@Composable
fun ProductSearchScreen(vm: AppViewModel, onBack: () -> Unit, onProduct: (code: String, combo: String?) -> Unit) {
    val products = vm.products
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, products) { searchProducts(products, query) }
    val openLabel = stringResource(R.string.prod_open_product)

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = NoInsets,
        topBar = {
            BxTopBar(
                stringResource(R.string.prod_lookup_title),
                subtitle = stringResource(R.string.prod_lookup_subtitle),
                onBack = onBack,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") { ProductSearchField(query, { query = it }) }
            when {
                products.isEmpty() -> item(key = "none") { NotInCatalog(stringResource(R.string.prod_catalog_empty_body), null) }
                query.isBlank() -> item(key = "hint") { SearchHint() }
                results.isEmpty() -> item(key = "none") { NotInCatalog(stringResource(R.string.prod_no_match_body, Products.queryText(query)), null) }
            }
            items(results, key = ::matchKey) { m ->
                ProductResultCard(
                    match = m,
                    selected = false,
                    onClickLabel = openLabel,
                    onClick = { onProduct(m.product.label, m.build?.combo?.takeIf { m.product.builds.size > 1 }) },
                )
            }
        }
    }
}

/** "2023-07-15" as a local date ("Jul 15, 2023"); anything else is shown as it is. */
@Composable
private fun releaseText(raw: String): String {
    val millis = remember(raw) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(raw)?.time
        }.getOrNull()
    }
    return if (millis != null) pickedDay(millis) else raw
}

/**
 * "Lookup only": a product's code, name, stock combo, type and release date, and the parts it came with
 * (Own / Missing). "Save combo" saves the stock combo when I own every part; "Add missing part" adds only the
 * parts I don't have. [combo] preselects a Bey of a set or random booster. [onPart] opens a part I own.
 */
@Composable
fun ProductLookupScreen(
    vm: AppViewModel,
    code: String,
    combo: String?,
    onBack: () -> Unit,
    onPart: (Long) -> Unit,
) {
    val parts by vm.parts.collectAsStateWithLifecycle()
    val combos by vm.combos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val product = remember(code) { Products.byKey(vm.products, code) }

    if (product == null) {
        Scaffold(contentWindowInsets = NoInsets, topBar = { BxTopBar(stringResource(R.string.prod_lookup_only), onBack = onBack) }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                NotInCatalog(
                    if (vm.products.isEmpty()) stringResource(R.string.prod_catalog_empty_body)
                    else stringResource(R.string.prod_unknown_code_body, code),
                    null,
                )
            }
        }
        return
    }

    var buildCombo by rememberSaveable(product.label) {
        mutableStateOf(product.builds.firstOrNull { combo != null && it.combo.equals(combo, ignoreCase = true) }?.combo ?: product.builds.first().combo)
    }
    val build = product.builds.firstOrNull { it.combo == buildCombo } ?: product.builds.first()
    val own = remember(build, parts) { Products.ownership(build, parts) }
    val ownedCount = own.count { it.owns }
    val missing = own.size - ownedCount
    val ownsAll = missing == 0
    val ownedIds = own.mapNotNull { o -> o.owned?.id?.takeIf { o.owns } }.sorted()
    val alreadySaved = ownsAll && combos.any { it.partIds().sorted() == ownedIds }
    var busy by remember { mutableStateOf(false) }
    val missingLabel = stringResource(R.string.prod_missing)
    val openPartLabel = stringResource(R.string.prod_open_part)

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { BxTopBar(stringResource(R.string.prod_lookup_only), onBack = onBack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        vm.addProductBuild(product, build, saveCombo = true, onlyMissing = true) {
                            busy = false
                            Toast.makeText(context, context.getString(R.string.prod_combo_saved_toast, build.combo), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = ownsAll && !alreadySaved && !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    FitText(stringResource(if (alreadySaved) R.string.prod_combo_already_saved else R.string.prod_save_combo), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        busy = true
                        vm.addProductBuild(product, build, saveCombo = false, onlyMissing = true) { added ->
                            busy = false
                            Toast.makeText(context, context.resources.getQuantityString(R.plurals.prod_added_parts, added, added), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = missing > 0 && !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    FitText(
                        if (missing > 0) pluralStringResource(R.plurals.prod_add_missing, missing, missing)
                        else stringResource(R.string.prod_nothing_missing),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header") {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(product.code, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.prod_stock_combo_x, build.combo), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeTag(build.type)
                        if (build.shape != ComboShape.BASIC) {
                            Text(build.shape.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        product.released?.takeIf { it.isNotBlank() }?.let { raw ->
                            Text(
                                stringResource(R.string.prod_released_x, releaseText(raw)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (product.builds.size > 1) {
                item(key = "builds") {
                    BuildPicker(product, build, { buildCombo = it.combo }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            item(key = "came_with") { SectionLabel(stringResource(R.string.prod_came_with_label)) }
            items(own.size, key = { i -> "part_$i" }) { i ->
                val o = own[i]
                val owned = o.owned?.takeIf { o.owns }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    StockPartRow(
                        o, missingLabel, photo = true,
                        onClick = owned?.let { p -> { onPart(p.id) } },
                        onClickLabel = openPartLabel,
                    )
                }
            }
            item(key = "summary") {
                Text(
                    stringResource(R.string.prod_you_own_x_of_y, ownedCount, own.size),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (ownsAll) winColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
