# BX Tracker

An Android app to track your Beyblade X parts, combos, 3on3 decks, matches and tournaments.

## Open it in Android Studio

1. Open Android Studio and choose **File › Open**, then pick this folder (the one with `settings.gradle.kts`).
2. **Set the Gradle JDK to 21.** The build uses Gradle 8.14.3, which can't run on JDK 25 (the JDK bundled with the newest Android Studio). Go to **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** and pick `jbr-21` (already installed in `~/.jdks`).
3. Let the Gradle sync finish, then plug in your phone with USB debugging on (or start an emulator) and press **Run ▶**.

From a terminal: set `JAVA_HOME` to a JDK 17–24 and run `gradlew assembleDebug`. Unit tests: `gradlew testDebugUnitTest`.

## Versions

AGP 8.11.1 · Gradle 8.14.3 · Kotlin 2.2.0 (Compose compiler plugin) · KSP 2.2.0-2.0.2 · Room 2.7.2 · Compose BOM 2025.06.00 · CameraX 1.4.2 · compileSdk/targetSdk 36 · minSdk 26.

## What's in it (0.2)

- **Parts:** Blades, Ratchets, Bits and CX parts (Lock Chip, Main Blade, Assist Blade). Adding a part opens the in-app camera (round guide, kind selector, flash, gallery, skip). Photos are cropped square around the guide. The phone's camera app is a fallback if the in-app camera can't start. Type filters on Blades, a photo grid on CX, Line (Takara Tomy or Hasbro) and "Got it from" on each part. Duplicates merge into copies.
- **Combos:** Basic, CX or CX Metal (Metal Blade + Over Blade), with generated names like `Phoenix Wing 9-60GF`, `Dran Brave S6-60V` and `Bahamut Blitz BK1-50I`. Blades with a built-in ratchet (`Bullet Griffon H`) and ratchets that include the bit (`Pegasus Blast ATr`) skip that slot. "Tested <date>", sort by win rate, recently used or name.
- **Decks:** flags repeated parts, suggests a swap that uses an owned part that's free ("Swap into slot N" builds or reuses the variant combo), shows the deck mix, and warns about parts you own 0 of.
- **Battles:** Overview, Matches and Tournaments tabs. Quick battle for casual matches. Live scoring with the opponent's combo type per battle, the opponent's last combo remembered, and haptic feedback.
- **Tournaments:** name, date, location, format, Swiss rounds, top cut, players and deck. "Start round" creates the next match (R1, R2… then QF, SF, F). Results page with placing, stats, rounds, battles won per combo, notes, and a Share button.
- **Organize a tournament:** run a whole event from Battles › Organize. Swiss + top cut, elimination or round robin; add players with check-in; Swiss pairing by record with no rematches and byes; score each match battle by battle or type the final score; standings with tiebreakers (point difference, opponents' win %, head-to-head); a seeded top cut bracket with an optional 3rd place match; final placings and a shareable summary. If you play in it, your matches and placing go into your own results.
- **Stats:** this season, tournaments only or all time. Win rate, matches, best finish, how you score, best combos (under 3 battles hidden by default), win rate vs each opponent type, and recent placings.
- **Settings:** theme with previews, points to win, points per finish, season start, photo size, back up and restore (a .zip with JSON and photos), and CSV export.

Everything is stored on the phone. Nothing is uploaded.

## Languages

English and Latin American Spanish. Settings › Language picks one (or follows the phone). Game terms such as Blade, Ratchet, Bit, Attack, Xtreme, 3on3 and Swiss stay in English in both. Strings live in `app/src/main/res/values/` (English) and `values-es/` (Spanish), one `strings_*.xml` file per screen group; any new text needs an entry in both.

## Part name catalog

`app/src/main/assets/catalog.json` feeds the name suggestions in Add part: about 265 Takara Tomy parts with codes, types and spin direction, compiled from the Beyblade Fandom wiki and Takara Tomy's lineup pages (listed in its `_sources` key). Each kind has its own array of `{ "name", "code", "type", "leftSpin" }` entries; edit it freely and rebuild. `gradlew testDebugUnitTest` checks that it still parses.

## Project layout

```
app/src/main/java/com/bxtracker/app/
  MainActivity.kt         entry point, applies the theme
  AppViewModel.kt         all reads and writes go through here
  data/                   Room entities + DAO, settings, photos, backup/CSV, catalog, rules (Logic.kt)
  ui/theme/Theme.kt       OLED and light color schemes, type colors, Montserrat
  ui/components/          shared pieces (top bar, cards, photo tile, stepper, stat tile, charts, date field)
  ui/screens/             Parts, Combos, Decks, Battles, Tournaments, Stats, Settings, camera
  ui/BxApp.kt             navigation and bottom tabs
app/src/test/             unit tests for the rules in Logic.kt
app/schemas/              exported Room schema (the baseline for future migrations)
```
