# BX Tracker: handoff for Claude Code

## Status, 2026-11-07 (v0.2.0)

Everything under "To build" is done, including the seed catalog (built from public web sources, see below).

- **Languages (added after v0.2.0):** English and Latin American Spanish (`values/` and `values-es/`, one `strings_*.xml` per screen group). Settings › Language switches it (AppCompat per-app locales, so MainActivity is an AppCompatActivity with an AppCompat theme); Android 13+ also lists it in the system's app-language settings. Game terms stay in English in both: part kinds, types, finishes, 3on3, Swiss, R1/QF/SF/F. Dates and ordinals follow the UI language ("3rd of 24" / "3.º de 24"). CSV headers stay English. **New text must go into both string files.**
- **Catalog:** `assets/catalog.json` has 95 blades, 40 ratchets, 53 bits, 28 lock chips, 16 main blades, 7 metal blades, 7 over blades and 19 assist blades, from the Beyblade Fandom wiki and Takara Tomy's official lineup pages (URLs in its `_sources` key). Takara Tomy names are what gets saved; a `hasbro` field on parts and builds lets search accept Hasbro's names too. It also holds the `PRODUCTS` list (see Dave's section below). A unit test parses the real file, so a broken hand edit fails the tests.
- **Combo shapes:** three shapes, picked at the top of the combo builder: Basic (Blade + Ratchet + Bit), CX (Lock Chip + Main Blade + Assist Blade + Ratchet + Bit) and CX Metal (Lock Chip + Metal Blade + Over Blade + Assist Blade + Ratchet + Bit, named like "Bahamut Blitz BK1-50I"). Blades can be flagged "built-in ratchet" (Bullet Griffon H) and ratchets "includes the bit" (Turbo, Operate: "Pegasus Blast ATr"); the builder then skips that slot. Turbo-style ratchets keep their own code ("Tr"). The rules live in `Logic.kt` (`ComboShape.slots`, `neededSlots`, `comboName`). The catalog includes Metal and Over Blades and the flags.

- **Build:** moved to AGP 8.11.1, Gradle 8.14.3, Kotlin 2.2.0, KSP 2.2.0-2.0.2, Room 2.7.2, Compose BOM 2025.06.00, compileSdk/targetSdk 36 (only SDK 36/37 are installed here). Gradle 8.14 needs JDK 17–24, so use `~/.jdks/jbr-21`, not Android Studio's bundled JDK 25. The original code compiled with no source changes.
- **Tests:** `gradlew testDebugUnitTest` runs the JVM tests: `LogicTest` (combo naming for all shapes, round labels, conflicts, swap suggestions, placings, records, the bundled catalog), `TournamentEngineTest` (organizer rules) and `ProductsTest` (product search and matching, suggestion ranking, manual rows, the bundled products list).
- **Checked on the API 37 emulator** with a restored test backup: restore, Battles overview, start round, live scoring with opponent type, finish with placing, share, Stats, deck suggested fix + swap, CX grid, in-app camera with crop, add-part suggestions, light theme.
- **Schema:** version 3. v1 → v2 (combo shapes, part flags) and v2 → v3 (organizer mode) are Room auto-migrations; both were checked on the emulator by installing over existing data. All schemas are in `app/schemas/`. **Every future change needs a version bump and a migration.**
- **Fixed along the way:** on form screens the keyboard covered the bottom Save buttons (IME padding now goes on the Scaffold).

### Organizer mode (built after v0.2.0)
Screens 15 to 19 are built and were run end to end on the emulator (5 players, 2 Swiss rounds, Top 4 with a 3rd place match, my own matches included).
- **Data model choice:** an organized event *is* a `Tournament` row with `organizer = true` plus the organizer fields (`eventFormat`, `scoring`, `stage`, `pointsToWin`, `thirdPlaceMatch`, `byeScore`, `randomSeeding`), not a separate `Event` table. That way it lists in the Tournaments tab, and when I play, my matches (`Match.tournamentId`), placing and player count land in my personal results (12) with no copying. New tables: `event_players`, `pairings`, `event_battles`.
- **Scores have one source each:** a pairing's score is resolved, in order, from a typed-in score (`manual`), my linked personal match (`matchId` → BattlePoints), or the organizer's `event_battles`. Status and winner are derived, not stored. My own matches always use the normal live scoring screen (11), so my combos and opponent types stay in my stats.
- **Rules:** all in `data/TournamentEngine.kt` (pure Kotlin) with 11 tests in `TournamentEngineTest`: suggested rounds, random round 1, pairing by record with pair-down and no rematches (backtracking), byes, round robin (circle method), bracket seeding (1v4/2v3, 1v8/4v5/2v7/3v6), advancement, tiebreakers (wins, point diff, opponents' win % floored at 33% without byes, head-to-head, points scored) and final placings.
- **Check-in:** if anyone is checked in when round 1 is paired, players who aren't get dropped (they can be un-dropped). If nobody is checked in, everyone plays.
- **Also run on the emulator:** an Elimination event (5 players: seeds 1–3 get byes, an upset, semifinal losers placed by seed) and Round robin events with 4 players (one dropped at check-in) and 5 players (every pair meets once, one bye each). Bye points are set on the format screen (Swiss and round robin): they follow "First to" until changed, and the standings note shows the value.

### NEW, 2026-11-12: tester feedback from Dave (built 2026-11-12)
Dave has been testing the app. Three items, in this order.

**What was built (no schema change, the database stays at version 3):**
- **Bug 1:** there was no multi-row form before, so it couldn't be reproduced as described. The new Manual tab (20) holds its rows in an immutable `ManualRows` model (`data/Products.kt`); every row has a stable id used as the LazyColumn key, and every edit (kind, name, code, type, copies) goes through `update(id)`. `ProductsTest.manualRowsKeepTheirOwnFields` covers it, and it was checked on the emulator with 3 rows.
- **Products:** `assets/catalog.json` has a `PRODUCTS` array (152 products: BX, UX and CX, 259 stock builds, release dates, every random booster and set build), from the Beyblade Fandom wiki, with CC BY-SA attribution in `_attribution`. Parts and builds carry a `hasbro` alias where Hasbro's name differs, so Dave's "Cursed Mummy" (Hasbro: Curse Mummy) finds Takara Tomy's Mummy Curse. Shatter Horus 9-65GB is a Hasbro-only release; its link finds the Takara Tomy Horus Shatter products by blade name. Pasted wiki links work.
- **BX-00 / UX-00 / CX-00** are limited, event and collab releases (43 of them share "BX-00"). `Products.order` puts regular releases first, then oldest, so Dran Sword "comes in BX-01". Screens pass `Product.label` (code + name) between each other, not the code, because codes aren't unique.
- **Screens:** Add parts tabs (20, 21), product lookup (22, from the search icon on Parts and from Part detail's "Came with" line), Suggest sheet (23, light bulb in the combo builder). Adding by product: owned parts get +1 copy, new parts get no photo and `source = "BX-xx Product name"`, and the camera doesn't open. The Camera tab and the camera screen have "Upload image" next to taking a photo.
- **Logic:** `data/Products.kt` (search, ownership, came with) and `data/Suggestions.kt` (can build, best with, missing one), with tests in `ProductsTest`. Suggestions only use stock builds and my battle records.

**Layout from the design (screens 20 to 23):**
- **Add parts** has three tabs: Camera, By product and Manual.
- **20 · Manual:** stacked cards, one per part. Each card has kind chips, a name field and a copies stepper, plus "+ Another part" and an "Add N parts" button.
- **21 · By product:** a search field (name, stock combo or product code). The results are cards showing the product code, name and stock combo. The selected one expands to list its parts, each with an "Own ×N" or "New" badge, plus an "Also save the stock combo" checkbox. Button: "Add 3 parts from X", with the note "Parts you own get +1 copy. Photos can be added later." Below the results: "Not in the list? Add it manually".
- **22 · Product lookup:** a "Lookup only" screen with the product code, name, stock combo and type. A "Came with" list shows each part with an Own, Missing or Add-photo placeholder, and "You own 2 of 3". Buttons: "Save combo" and "Add missing part". Part detail gets a line like "Came with: Dran Sword (BX-01), 2 more".
- **23 · Suggest:** a bottom sheet over the combo builder with three sections: "You can build these" (busy parts marked, not hidden), "Best with <blade>" and, at the bottom, "Missing one part" with "Need: X · comes in BX-xx" and a "See product" button. Footer: "Parts already in a deck are still suggested and marked. Only stock builds and your own battle records, no tier lists."

**1. Bug: the Name field is shared across items when adding manually**
- When you add parts by hand, typing in one Name field fills the same text into every Name field.
- Cause (Kenny's read): the field state lives at the list level instead of per item, from when the form was built for items and not for submitting a combo.
- Fix:
  - Give each row its own state, keyed by a stable item id (`key = item.id` in the LazyColumn, and `rememberSaveable(item.id)` or state held in the item model).
  - Check the code, type and copies fields for the same bug.
- Add a test or a manual check: add 3 parts, type different names, and each one must keep its own.

**2. Add a Beyblade by product, with its stock parts (no photo needed)**
Dave didn't keep the boxes and doesn't want to photograph every part. He wants to search a product, like "Cursed Mummy" or a wiki page such as https://beyblade.fandom.com/wiki/Shatter_Horus_9-65GB, and have the app add all the parts it came with.
- **Product catalog:** extend `assets/catalog.json` with a `PRODUCTS` array, built from the same Beyblade Fandom wiki you already used.
  - Each entry has: product code (e.g. BX-01, UX-…, CX-…), product name, stock combo name, shape (Basic, CX or CX Metal), and the list of stock parts (kind + name + code) pointing at the existing part entries.
  - Also: release date, and the list of products for multi-packs and random boosters (pick one when adding).
  - Keep the `_sources` URLs and CC BY-SA attribution. Don't bundle wiki images.
- **"Add by product"** in Add part (a tab or a button next to the camera):
  - Search by product name, stock combo name or product code.
  - The result shows the stock combo and its parts. "Add all parts" adds each part with copies += 1, reusing the merge-by-name logic, with the photo left empty. Optionally it also creates the stock combo.
- **Photo becomes optional.** Parts added this way show the "Add" photo placeholder, and the No photo filter still finds them. Don't auto-open the camera on this path.
- **Lookup only ("manual query"):** a product search you can use without adding anything, to answer "what parts did Cursed Mummy come with?". Put it on Part detail ("Came with: …") too, by matching the part's name against stock builds.
- Remember the product on each part (`Part.source = "BX-xx Product name"`, the field already exists).
- If a product isn't in the bundled catalog, show "Not in the catalog" with an option to add it manually. No live network lookups for now. The app stays offline. A "refresh catalog" from the wiki can come later.

**3. Combo suggestions from my whole collection (plus parts worth getting)**
Dave asked whether the builder suggests combos from the parts you have. Add a "Suggest" action in the combo builder (screen 23). It uses the whole collection, not just free parts.
- **You can build these:** stock builds and my best win-rate combos where I own every part. Parts already in a deck are still suggested and marked with a note like "9-60 is in Tournament deck" (in the warning color). They are not hidden.
- **Best with <picked blade>:** once a Blade (or Lock Chip + Main Blade) is picked, rank the Ratchets and Bits I own by my own battle records with that blade. If there are no records, fall back to the stock pairing.
- **Missing one part (bottom section, "worth getting"):** stock builds where I own everything except one part. Show "Need: <part> · comes in <product code>" and a "See product" button that opens the product lookup (22). Sort by how many of the other parts I own and by my records with them.
- Explain each suggestion in one line.
- Don't invent meta or tier lists. Only use stock builds from the catalog and my own records.

New text goes into both string files (English and Spanish), and any schema change needs a version bump and a migration.

### Still open
1. **Not tried on a real phone yet**, so real camera resolution, flash and haptics are unverified.
2. If camera permission is denied, the phone-camera fallback can't open either (Android blocks ACTION_IMAGE_CAPTURE for apps that declare CAMERA and don't have it). The gallery still works.
3. The design canvas wasn't available, so spacing and copy follow the written spec below.

## What this is
A personal Android app for tracking my Beyblade X collection, combos, 3on3 decks, matches and tournaments. It was designed and scaffolded in a Claude (Cowork) session. That session could NOT compile anything because Google Maven and Gradle downloads were blocked, so the code has never been built. Expect compile errors.

Project folder: C:\Users\Kenny_Dazos\Claude Projectts\Byablde x Tracker\BXTracker
(The BXTracker.zip one level up is just the delivery copy and can be deleted.)

## Your job, in order
1. **Get it building.** Run a Gradle sync and `gradlew assembleDebug`. Fix every error until the build is clean. If plugin or library versions are the problem, move to stable versions that work together (AGP, Kotlin, the Compose compiler plugin, KSP and Room have to match).
2. **Run it** on my phone or an emulator and smoke-test what already exists (see "Built in v0.1").
3. **Build the missing features** in the order listed under "To build". The spec for every screen is below.
4. After each step, tell me what changed and what's still open.

## Stack, as written
- Kotlin, Jetpack Compose with Material 3, single activity, Navigation Compose with string routes
- Room (with KSP), DataStore Preferences, Coil, ExifInterface
- AGP 8.5.2, Kotlin 2.0.20 with org.jetbrains.kotlin.plugin.compose, KSP 2.0.20-1.0.25, Compose BOM 2024.09.03, Room 2.6.1, navigation-compose 2.8.2, Gradle wrapper 8.9
- compileSdk and targetSdk 34, minSdk 26, JVM 17, package `com.bxtracker.app`
- Everything stays on the device. No accounts, no network.

## Code layout (app/src/main/java/com/bxtracker/app/)
- `MainActivity.kt`: edge-to-edge, applies BxTheme from settings
- `AppViewModel.kt`: one AndroidViewModel. Exposes StateFlows for parts, combos, decks, matches, points and settings, plus all write functions.
- `data/Entities.kt`: Part, Combo, Deck, Match, BattlePoint, and the enums PartKind, BeyType, Finish, Scorer
- `data/Database.kt`: BxDao and AppDatabase (version 1)
- `data/Logic.kt`: combo naming, records, match score and outcome, deck conflicts, finish breakdown
- `data/PhotoStore.kt`: photos go to filesDir/photos and are shared through a FileProvider (`${applicationId}.files`). They're shrunk to about 1080px and rotation is fixed.
- `data/SettingsStore.kt`: ThemeMode (SYSTEM, LIGHT, OLED) and pointsToWin
- `ui/theme/Theme.kt`: OLED and light color schemes, typeTint(), winColor(), lossColor(), warnColor()
- `ui/components/Common.kt`: BxTopBar, AppCard, TypeTag, PartPhoto, Stepper, RecordBar, EmptyState
- `ui/BxApp.kt`: bottom tabs (Parts, Combos, Decks, Battles) and the routes
- `ui/screens/`: PartsScreens.kt, ComboScreens.kt, DeckScreens.kt, BattleScreens.kt (Battles, NewMatch, Match and Settings)

**Insets convention.** The outer Scaffold in BxApp handles the system bars and the bottom nav. It applies padding and consumeWindowInsets to the NavHost. Inner screens use `Scaffold(contentWindowInsets = NoInsets)`, and TopAppBar also uses NoInsets. Forms use imePadding().

## Domain rules
- **Part kinds:** Blade, Ratchet, Bit, plus the CX parts Lock Chip, Main Blade and Assist Blade.
  - Ratchets use their code as the name ("9-60").
  - Bits have a short code ("GF").
  - Assist Blades have a letter ("S").
- **Combos:** a Basic combo is Blade + Ratchet + Bit. A CX combo is Lock Chip + Main Blade + Assist Blade + Ratchet + Bit.
- **Combo names:** Basic is "Phoenix Wing 9-60GF". CX is "Dran Brave S6-60V" (lock chip, main blade, then the assist letter stuck to the ratchet, then the bit code). If a bit has no code, use its full name after a space.
- **Types:** Attack, Defense, Stamina, Balance.
- **Finish points:** Spin 1, Over 2, Burst 2, Xtreme 3. Matches are first to 4 by default. Both should be settings.
- **3on3 decks:** a deck has 3 combos and can't repeat any part. Parts count as the same if they have the same kind and name.
- **Stats source:** win rates come from battles (BattlePoint rows tagged with my combo), not from whole matches. Never store totals. Calculate them.
- **Duplicate parts:** adding a part with the same kind and name adds copies instead of creating a duplicate.

## Theme
- **Dark · true black (default):** background and surface #000000. Containers step up through #0D1117, #161B22 and #1C222A. Outline #30363D, outline variant #21262D. Text #F3F7FB, secondary text #A9B8C7. Primary #179CD8 with #0B1E2B text on it. Accent text #74C6EA.
- **Light:** background #F7F9FB, cards #FFFFFF, containers #EEF2F6 and #E1E7EE. Text #0B1E2B, secondary text #4A5666. Primary #0F729F.
- **Type colors (dark / light text):**
  - Attack #FF8A8D / #C4282D
  - Defense #74C6EA / #0F729F
  - Stamina #4FD3A0 / #0E8A5C
  - Balance #F7BE5C / #9A5B00
  - Each is drawn on a tinted background of the same hue.
- **Theme setting:** Match phone setting, Light, or Dark · true black.
- **Camera screen:** stays dark in both themes.
- **Font:** Montserrat for UI, with monospace for codes and scores like "9-60" and "4–2" (not added yet).
- **Accessibility:** touch targets of at least 44dp, and icon-only buttons need content descriptions.

## Screen specs (all 19 designed screens)
Status: [built] means it exists in v0.1, [partial] means some of it exists, [todo] means not started.

1. **Parts list [built]**
   - Summary tiles with the copy counts for Blades, Ratchets, Bits and CX.
   - Segmented tabs (Blades, Ratchets, Bits, CX) and a search field.
   - Filter chips: All, Unused, No photo. **Todo:** add type chips Attack, Defense, Stamina.
   - Each row shows the photo thumbnail, name and code, type tag, "In N combos" or "Unused" (in the warning color), and "×copies".
   - A part with no photo shows a dashed camera "Add" tile.
   - Extended FAB "Add part" with a camera icon.
2. **Combos list [built]**
   - Filter chips: All, Favorites, and each type.
   - Sorted with favorites first, then by win rate.
   - Each card shows the type tag, a CX badge, the star, the generated name, small photos of every part, a W/L bar with %, and the notes.
   - **Todo:**
     - "Tested <date>" (date of the last battle) on each card.
     - A sort menu with best win rate, recently used and name.
3. **Combo builder, Basic [built]**
   - Numbered slots: Blade, Ratchet, Bit. The active slot is highlighted.
   - Below the slots, a picker shows owned parts of the active kind with their photo, "×N owned · free" or "in N other combos". Free parts come first.
   - "+ Add a new <kind>" opens Add Part for that kind and returns.
   - Type chips are auto-filled from the blade's type. Also notes, a favorite star, and "Save <name>".
   - It can open with a starting part (`combo?part=`) and edit an existing combo (`combo?id=`).
4. **3on3 deck check [partial]**
   - A red banner names each repeated part and the combos (slots) using it.
   - The 3 slot cards turn red when they have a conflict.
   - The combo picker shows "Repeats <part>" next to combos that would cause a conflict.
   - **Todo:**
     - **Suggested fix card.** For a conflicting slot, find a variant of the same combo that swaps the repeated part for an owned copy of the same kind that's free (unused in the other slots). Example: "Wizard Rod 3-60 H, you have 3-60 ×2, both free". Add a "Swap into slot N" button that creates or reuses that combo and puts it in the slot.
     - **Deck mix chips:** how many Attack, Stamina and Defense combos are in the deck.
     - Also flag when a part is in the deck but I own 0 copies.
5. **Part detail [built]**
   - A large round photo. Tap to retake, or pick from the gallery.
   - Type chips for blades and main blades.
   - A "Copies owned" stepper with "Used in N combos".
   - A warning when a part is used in more combos than I own copies of: "fine for practice, only one can go in a deck".
   - Name, code, weight (g) and note fields. "Used in" combo cards with win %. "Build a combo with this". Delete with a confirmation.
   - **Todo:** add "Line" (Takara Tomy or Hasbro) and "Got it from" fields.
6. **Add part: camera [partial]**
   - Today this uses the system camera intent (TakePicture) plus the photo picker, and it opens automatically when you add a part.
   - **Todo:** a CameraX in-app camera.
     - Dark viewfinder with a round guide and the hint "Shoot from the top, part centered in the circle. Plain background works best."
     - A kind selector strip above the shutter (Blade, Ratchet, Bit, CX part).
     - Gallery button, shutter, Skip, and a flash toggle.
     - Crop the saved photo to a square centered on the guide.
     - Keep the system-camera path as a fallback.
7. **Add part: details [built]**
   - Photo preview with Retake and Gallery buttons. A 6-way kind chooser. Name. Short code for bits and assist blades. Type and Left spin for blades and main blades. Copies stepper and a note.
   - "You already have X ×N, saving adds copies".
   - Buttons: "Save + next" (saves, resets, reopens the camera) and "Add to collection".
   - **Todo:** name autocomplete from the seed catalog (see To build), so picking "Slash" fills in code "S".
8. **CX parts tab [partial]**
   - Built as a grouped list under the CX tab.
   - **Todo:** the designed layout is a 3-column photo grid grouped by Lock Chips, Main Blades and Assist Blades. Each tile has a copy badge, the name (plus the assist letter), and "In N combos" or "Unused". Filter chips: All, Lock Chips, Main, Assist.
9. **CX combo builder [built]**
   - A Basic/CX switch at the top.
   - In CX mode the Blade slot is replaced by a group of Lock Chip, Main Blade and Assist Blade, followed by Ratchet and Bit.
   - Live name preview, for example "Dran Brave S6-60_".
10. **Battles home [partial]**
    - Built: a stat tiles row (battle win %, matches, Xtremes), a "How you score" bar, best combos, and the match list.
      - The match list shows a W, L or in-progress badge, opponent and round, event and date, and the score.
    - **Todo:**
      - Segmented tabs: Overview, Matches, Tournaments.
      - The header shows the season record ("31W 14L this season").
      - An "In progress" tournament card: name, "Round 3 of 5 · Swiss · you're 2–0", and buttons "Start round N" and "View".
      - The FAB says "Quick battle", for a casual match outside any tournament.
      - A stats icon in the top bar that opens screen 13.
11. **Live match scoring [built]**
    - Header with the opponent and round, plus the event and "first to N". Undo and delete buttons.
    - A big score panel ("You" vs the opponent) with "Battle N".
    - When playing a deck, a 1/2/3 selector for the combo spinning this battle. Optional "Their combo" text.
    - Two 2×2 grids, "You scored" and "They scored", for Spin, Over, Burst and Xtreme. A Draw button. History lines like "B2 Wizard Rod vs Dran Buster · Over · them +2".
    - When either side reaches the target, a win/loss banner appears, the match is marked finished, and undo can reopen it.
    - **Todo:**
      - An optional opponent combo type chip for each battle, which feeds "vs opponent type" on the Stats screen.
      - Remember the last "their combo" for each opponent.
      - Haptic feedback when a point is scored.
12. **Tournament results [todo]**
    - New `Tournament` entity:
      - id, name, date, location
      - format: "3on3", Swiss rounds count, top cut size (0, 4, 8 or 16)
      - player count, deckId, placement (Int?), notes, status (PLANNED, IN_PROGRESS, DONE)
    - Change Match: replace the free-text `event` with `tournamentId: Long?`, keep `round`, and add a `stage` field (SWISS or TOP_CUT).
    - Create tournament: name, date, format, Swiss rounds, top cut, number of players, and the deck I'm bringing.
    - Starting a round creates a Match linked to the tournament, with the round auto-named R1, R2… then QF, SF, F in the top cut. It uses the tournament's deck.
    - Results screen:
      - Placing badge ("3rd of 24"), name, date and format.
      - Stat tiles for matches W–L, points for–against, and Xtreme count.
      - A rounds list, one row per round: R1 to R4, QF, SF. Each row shows a W or L badge, the opponent and the score, and tapping it opens the match.
      - "Deck you brought" with battles won per combo as bars ("Phoenix Wing 9-60GF 8/10").
      - An editable note, and a Share button that shares a text summary through the Android share sheet.
    - Set the placement when finishing a tournament.
    - The Tournaments tab on Battles home lists tournaments with their placing and record.
13. **Stats [todo]**
    - Period filter: This season, Tournaments only, All time. Add a "season start date" setting.
    - KPIs: battle win rate, matches played, best tournament finish.
    - "How you score points": a stacked bar of Xtreme, Over, Spin and Burst with percentages. Tell the segments apart by lightness, not only hue.
    - "Most points against you come from X finishes (N%)".
    - Best combos ranked with win-rate bars. Hide combos with fewer than 3 battles behind a toggle.
    - "Vs opponent type": my win % against Attack, Defense and Stamina opponents, from the opponent type logged per battle.
    - Tournament placings over the last N events as small bars.
14. **Settings [partial]**
    - Built: the theme radio list (Match phone, Light, Dark · true black) and the points-to-win stepper.
    - **Todo:**
      - Small light/dark preview tiles above the theme options.
      - Editable finish point values (Spin, Over, Burst, Xtreme).
      - Season start date.
      - Photo size setting (Small or Standard).
      - "Back up collection": export a .zip of the database as JSON plus the photos through the Storage Access Framework, and import it back.
      - "Export as CSV" for parts, combos and matches.

## Organizer mode: run a full tournament (screens 15 to 19) [built]
This is separate from the personal tournament log (screen 12). Here the app runs the whole event: every player, every pairing, every result, standings, the top cut, and the champion.

**Data model.** Reuse the `Tournament` entity built in v0.2.0 where it fits: add the organizer fields to it (or link to it) instead of creating a duplicate `Event` table. Pick whichever is cleaner and say which you chose. Not on a real install yet, so changing schema version 1 is still OK.

- `Event`
  - id, name, date, place
  - format: SWISS_TOP_CUT, SINGLE_ELIM or ROUND_ROBIN
  - swissRounds, topCut (0, 4, 8 or 16), pointsToWin, scoring (BATTLES or FINAL_SCORE_ONLY)
  - status: SETUP, SWISS, TOP_CUT or DONE
  - myPlayerId (nullable, the player row that is me)
- `EventPlayer`
  - id, eventId, name
  - isMe, deckNote, dropped (Boolean)
  - checkedIn, seed (set when the top cut starts), finalPlacing
- `Pairing`
  - id, eventId, stage (SWISS or TOP_CUT), round (Int), table (Int), bracketSlot (for the top cut)
  - playerAId, playerBId (null means a bye)
  - scoreA, scoreB
  - status: NOT_STARTED, LIVE or DONE
  - winnerId
- `EventBattle`
  - id, pairingId, scorer (A, B or DRAW), finish, points, comboText (optional)
  - Only used when scoring = BATTLES.
- When a pairing involves `isMe`, also write a normal `Match` plus `BattlePoint`s (with my real combo IDs), so my personal stats stay complete.

**Rules (put them in a pure Kotlin `TournamentEngine` with unit tests)**
- **Suggested Swiss rounds:** ceil(log2(players)), and let the organizer change it.
- **Round 1:** random pairings.
- **Later rounds:**
  - Group players by match wins and pair from the top group down.
  - Never pair two players who already played (backtrack if needed).
  - If a group is odd, pair its lowest player into the next group down.
- **Byes:** with an odd player count, the lowest-ranked player who hasn't had a bye gets one. A bye counts as a win, 4–0 for point difference (configurable). Dropped players are never paired.
- **Standings order:**
  1. Match wins
  2. Point difference (points scored minus points conceded across all matches)
  3. Opponents' match win % (Buchholz-style, with each opponent's win % floored at 33%, byes excluded)
  4. Head-to-head
  5. Points scored
- **Top cut:**
  - Seed from the standings: 1v4 and 2v3 for a top 4, and 1v8, 4v5, 2v7, 3v6 for a top 8.
  - Single elimination. The winner advances automatically.
  - Optional 3rd place match.
- **Single elimination format:** seed by registration order or random, and pad to a power of 2 with byes.
- **Round robin format:** everyone plays everyone (circle method), and standings use the same tiebreakers.
- **Who wins a match:** the first player to reach pointsToWin, from the battle-by-battle points or the final score entered.
- **Advancing:** the next round can only be paired when every match in the current round is DONE. The organizer can override (score by hand, or swap a pairing before it starts).
- **Final placings:** top cut results first (champion, finalist, then semifinal losers ranked by Swiss standing, unless a 3rd place match was played), then everyone else by Swiss standing. Save them to `finalPlacing`.

**Screens**
- **15 · Organize: format.** Name, date and place. Format: Swiss + cut, Elimination, or Round robin. A Swiss rounds stepper (with the suggested number shown). Top cut chips: None, Top 4, Top 8, Top 16. Match is 3on3, first to N. Scoring is either "Battle by battle" (full stats for every player) or "Final score only" (faster). Button: "Next: add players".
- **16 · Organize: players.** A text field and + button to add players, with the count in the title. Each row has an avatar initial, the name, a deck note, and a check-in checkbox. My own row is marked "you". Note that odd counts get a bye and that players can be dropped mid-event. Button: "Pair round 1". Remember past player names for autocomplete.
- **17 · Organize: round pairings.** This is the "who's next" view.
  - Header: event name and "Swiss round 2 of 3 · then Top 4". Tabs: Round, Standings, Bracket.
  - A progress bar ("2 of 4 done").
  - One card per table showing both players with their current W–L, the score, and a status pill (Live, Done, Not started). The winner's score is green.
  - Buttons: "Score this match" or "Resume scoring". Tap a pairing to swap players before it starts.
  - The footer button is disabled until every match is done ("Finish 2 matches to pair round 3"), then becomes "Pair round 3" or "Start Top 4".
- **18 · Organize: standings.**
  - A table with columns #, Player, W–L, Pts ± (green or red), and Opp %. My row is highlighted.
  - A dashed top cut line.
  - A box explaining the tiebreakers, including who missed the cut and why.
  - Button: "Start Top N".
- **19 · Organize: top cut bracket.**
  - Semifinal and final cards with seeds, scores and the winner highlighted. The live match gets a blue outline and "Live · battle N".
  - An optional 3rd place match card.
  - A champion card when it's decided.
  - A note on how seeding works. Button: "Resume the final".
  - When the event ends, show the final placings 1st to last, and offer to share a text summary.
- **Scoring a match** reuses screen 11 (live match scoring) with both players' names instead of "You" and "Opponent". With FINAL_SCORE_ONLY, use a quick dialog with two number steppers instead.
- **Entry points:**
  - Battles home gets an "Organize" action.
  - The Tournaments tab lists events I organized next to tournaments I played.
  - If I'm playing in an event I organize, it also shows up in my personal Tournament results (12).

## To build, in this order
1. Clean build and smoke test (job steps 1 and 2).
2. **Tournaments** (screen 12, plus the Battles home changes in screen 10). Includes the schema change. There's no real data yet, so it's fine to change the version 1 schema or use fallbackToDestructiveMigration for now. Once I start using the app for real, every schema change needs a proper Room migration.
3. **Organizer mode** (15 to 19). Start with `TournamentEngine` and its unit tests (pairing, byes, no rematches, tiebreakers, seeding, advancement), then the screens.
4. **Stats screen** (13), plus the opponent type per battle (11).
5. **Deck suggested fix and deck mix** (4).
6. **CameraX capture screen** (6).
7. **CX photo grid** (8), plus the remaining small todos on screens 1, 2, 5 and 14.
8. **Backup, restore and CSV export** (14).
9. **Seed catalog for autocomplete.** A bundled JSON of Beyblade X part names and codes by kind, which I can edit. Ask me for the source before you fill it in. Don't make up part names.
10. **Montserrat font** (bundled from Google Fonts, licensed under OFL).

## Design reference
The full design is a Claude Design canvas: https://claude.ai/code/artifact/692247b3-394c-4c7e-8007-23b8a5844ea7. It has a Dark · OLED page and a Light page, 19 screens each. You may not be able to open it, which is why this file spells everything out.
