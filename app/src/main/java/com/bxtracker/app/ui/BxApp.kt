package com.bxtracker.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bxtracker.app.AppViewModel
import com.bxtracker.app.R
import com.bxtracker.app.data.PartKind
import com.bxtracker.app.ui.screens.AddPartScreen
import com.bxtracker.app.ui.screens.BattlesScreen
import com.bxtracker.app.ui.screens.ComboBuilderScreen
import com.bxtracker.app.ui.screens.CombosScreen
import com.bxtracker.app.ui.screens.DeckEditScreen
import com.bxtracker.app.ui.screens.DecksScreen
import com.bxtracker.app.ui.screens.MatchScreen
import com.bxtracker.app.ui.screens.NewMatchScreen
import com.bxtracker.app.ui.screens.PartDetailScreen
import com.bxtracker.app.ui.screens.PartsScreen
import com.bxtracker.app.ui.screens.ProductLookupScreen
import com.bxtracker.app.ui.screens.ProductSearchScreen
import com.bxtracker.app.ui.screens.SettingsScreen
import com.bxtracker.app.ui.screens.StatsScreen
import com.bxtracker.app.ui.screens.TournamentEditScreen
import com.bxtracker.app.ui.screens.TournamentScreen
import com.bxtracker.app.ui.screens.EventMatchScreen
import com.bxtracker.app.ui.screens.EventPlayersScreen
import com.bxtracker.app.ui.screens.EventScreen
import com.bxtracker.app.ui.screens.EventSetupScreen
import com.bxtracker.app.data.EventStage
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("parts", R.string.tab_parts, Icons.Outlined.GridView),
    Tab("combos", R.string.tab_combos, Icons.Outlined.Layers),
    Tab("decks", R.string.tab_decks, Icons.Outlined.Style),
    Tab("battles", R.string.tab_battles, Icons.Outlined.EmojiEvents),
)

/** Route to the product lookup (screen 22). [combo] picks a Bey of a set or random booster; both are URL-encoded. */
fun productRoute(code: String, combo: String? = null): String =
    "product/${Uri.encode(code)}" + (combo?.let { "?combo=${Uri.encode(it)}" } ?: "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BxApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "parts",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable("parts") {
                PartsScreen(
                    vm = vm,
                    onPart = { nav.navigate("part/$it") },
                    onAdd = { nav.navigate("addPart") },
                    onSettings = { nav.navigate("settings") },
                    onLookup = { nav.navigate("productSearch") },
                )
            }
            composable(
                "addPart?kind={kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e ->
                val kind = e.arguments?.getString("kind")?.let { runCatching { PartKind.valueOf(it) }.getOrNull() }
                AddPartScreen(vm = vm, initialKind = kind, onDone = { nav.popBackStack() })
            }
            composable("part/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PartDetailScreen(
                    vm = vm,
                    partId = e.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onCombo = { nav.navigate("combo?id=$it") },
                    onBuildWith = { nav.navigate("combo?part=$it") },
                    onProduct = { code, combo -> nav.navigate(productRoute(code, combo)) },
                )
            }
            // Product lookup (22): search, then one product with the parts it came with. Adds nothing by itself.
            composable("productSearch") {
                ProductSearchScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onProduct = { code, combo -> nav.navigate(productRoute(code, combo)) },
                )
            }
            composable(
                "product/{code}?combo={combo}",
                arguments = listOf(
                    navArgument("code") { type = NavType.StringType },
                    navArgument("combo") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                ProductLookupScreen(
                    vm = vm,
                    code = e.arguments?.getString("code").orEmpty(),
                    combo = e.arguments?.getString("combo"),
                    onBack = { nav.popBackStack() },
                    onPart = { nav.navigate("part/$it") },
                )
            }
            composable("combos") {
                CombosScreen(
                    vm = vm,
                    onNew = { nav.navigate("combo") },
                    onCombo = { nav.navigate("combo?id=$it") },
                    onAddPart = { nav.navigate("addPart") },
                )
            }
            composable(
                "combo?id={id}&part={part}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("part") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                ComboBuilderScreen(
                    vm = vm,
                    comboId = e.arguments?.getLong("id")?.takeIf { it > 0 },
                    startPartId = e.arguments?.getLong("part")?.takeIf { it > 0 },
                    onDone = { nav.popBackStack() },
                    onAddPart = { kind -> nav.navigate("addPart?kind=${kind.name}") },
                    onSeeProduct = { code, combo -> nav.navigate(productRoute(code, combo)) },
                )
            }
            composable("decks") {
                DecksScreen(vm = vm, onDeck = { nav.navigate("deck/$it") })
            }
            composable("deck/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                DeckEditScreen(vm = vm, deckId = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("battles") {
                BattlesScreen(
                    vm = vm,
                    onQuickBattle = { nav.navigate("newMatch") },
                    onMatch = { nav.navigate("match/$it") },
                    onSettings = { nav.navigate("settings") },
                    onStats = { nav.navigate("stats") },
                    onTournament = { nav.navigate("tournament/$it") },
                    onNewTournament = { nav.navigate("tournamentEdit") },
                    onOrganize = { nav.navigate("organize/new") },
                    onEvent = { nav.navigate("organize/$it") },
                )
            }
            // Organizer mode: format (15) → players (16) → event with round, standings and bracket (17–19).
            composable("organize/new") {
                EventSetupScreen(
                    vm = vm,
                    tournamentId = null,
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate("organize/$id/players") { popUpTo("organize/new") { inclusive = true } } },
                )
            }
            composable("organize/{id}/setup", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                EventSetupScreen(
                    vm = vm,
                    tournamentId = e.arguments?.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable("organize/{id}/players", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                EventPlayersScreen(
                    vm = vm,
                    tournamentId = id,
                    onBack = { nav.popBackStack() },
                    onEditFormat = { nav.navigate("organize/$id/setup") },
                    onStarted = { nav.navigate("organize/$id") { popUpTo("organize/{id}/players") { inclusive = true } } },
                )
            }
            composable("organize/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                // Still adding players: go to the players screen instead.
                val t = vm.tournaments.collectAsStateWithLifecycle().value.firstOrNull { it.id == id }
                if (t != null && t.stage == EventStage.SETUP) {
                    EventPlayersScreen(
                        vm = vm,
                        tournamentId = id,
                        onBack = { nav.popBackStack() },
                        onEditFormat = { nav.navigate("organize/$id/setup") },
                        onStarted = { },
                    )
                } else {
                    EventScreen(
                        vm = vm,
                        tournamentId = id,
                        onBack = { nav.popBackStack() },
                        onScoreBattles = { nav.navigate("organize/pairing/$it") },
                        onScoreMine = { nav.navigate("match/$it") },
                        onPlayers = { nav.navigate("organize/$id/players") },
                        onMyResults = { nav.navigate("tournament/$id") },
                    )
                }
            }
            composable("organize/pairing/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                EventMatchScreen(vm = vm, pairingId = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("stats") { StatsScreen(vm = vm, onBack = { nav.popBackStack() }) }
            composable(
                "tournamentEdit?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { e ->
                TournamentEditScreen(
                    vm = vm,
                    tournamentId = e.arguments?.getLong("id")?.takeIf { it > 0 },
                    onBack = { nav.popBackStack() },
                    onSaved = { id -> nav.navigate("tournament/$id") { popUpTo("tournamentEdit?id={id}") { inclusive = true } } },
                )
            }
            composable("tournament/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                TournamentScreen(
                    vm = vm,
                    tournamentId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("tournamentEdit?id=$id") },
                    onMatch = { nav.navigate("match/$it") },
                    onOpenEvent = { nav.navigate("organize/$id") },
                )
            }
            composable("newMatch") {
                NewMatchScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onStarted = { id ->
                        nav.navigate("match/$id") { popUpTo("newMatch") { inclusive = true } }
                    },
                )
            }
            composable("match/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                MatchScreen(vm = vm, matchId = e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
