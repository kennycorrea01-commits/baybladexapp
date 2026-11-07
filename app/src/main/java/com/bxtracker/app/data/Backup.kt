package com.bxtracker.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Everything in the database. Photo paths are stored as bare file names inside the zip's photos/ folder. */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val parts: List<Part> = emptyList(),
    val combos: List<Combo> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val matches: List<Match> = emptyList(),
    val points: List<BattlePoint> = emptyList(),
    val eventPlayers: List<EventPlayer> = emptyList(),
    val pairings: List<Pairing> = emptyList(),
    val eventBattles: List<EventBattle> = emptyList(),
)

object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private const val DATA = "data.json"
    private const val PHOTOS = "photos/"

    fun suggestedName(): String = "bx-tracker-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip"

    /** Writes data.json plus every part photo into a zip at [uri]. */
    fun export(context: Context, uri: Uri, data: BackupData) {
        val portable = data.copy(parts = data.parts.map { p -> p.copy(photoPath = p.photoPath?.let { File(it).name }) })
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DATA))
                zip.write(json.encodeToString(BackupData.serializer(), portable).toByteArray())
                zip.closeEntry()
                data.parts.mapNotNull { it.photoPath }.map(::File).filter { it.exists() }.distinctBy { it.name }.forEach { f ->
                    zip.putNextEntry(ZipEntry(PHOTOS + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Reads a backup zip. Photos are unpacked into app storage and the returned data points at them.
     * Nothing in the database changes until the caller saves the result.
     */
    fun read(context: Context, uri: Uri): BackupData {
        val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
        var data: BackupData? = null
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == DATA -> data = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                        entry.name.startsWith(PHOTOS) && !entry.isDirectory -> {
                            // Only the bare file name is used, so a crafted entry can't escape the photos folder.
                            val name = File(entry.name).name
                            if (name.isNotBlank()) File(photoDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val d = data ?: throw NotABackupException()
        return d.copy(parts = d.parts.map { p ->
            p.copy(photoPath = p.photoPath?.let { File(photoDir, File(it).name) }?.takeIf { it.exists() }?.absolutePath)
        })
    }

    // CSV

    /** Writes parts.csv, combos.csv and matches.csv into the folder the user picked. Returns the file names. */
    fun exportCsv(context: Context, treeUri: Uri, data: BackupData): List<String> {
        val parts = data.parts.associateBy { it.id }
        val usage = usageCounts(data.combos)
        val records = comboRecords(data.points)
        val tournaments = data.tournaments.associateBy { it.id }
        val decks = data.decks.associateBy { it.id }
        val combos = data.combos.associateBy { it.id }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val files = mapOf(
            "parts.csv" to csv(
                listOf("Kind", "Name", "Code", "Type", "Left spin", "Copies", "In combos", "Weight (g)", "Line", "Got it from", "Note"),
                data.parts.map { p ->
                    listOf(p.kind.label, p.name, p.code, p.type?.label.orEmpty(), if (p.leftSpin) "yes" else "", p.copies.toString(),
                        (usage[p.id] ?: 0).toString(), p.weight, p.line?.label.orEmpty(), p.source, p.note)
                },
            ),
            "combos.csv" to csv(
                listOf("Name", "Type", "CX", "Favorite", "Battles won", "Battles lost", "Win %", "Notes"),
                data.combos.map { c ->
                    val r = records[c.id] ?: Record()
                    listOf(comboName(c, parts), c.type.label, if (c.isCx()) "yes" else "", if (c.favorite) "yes" else "",
                        r.wins.toString(), r.losses.toString(), if (r.total > 0) r.percent.toString() else "", c.notes)
                },
            ),
            "matches.csv" to csv(
                listOf("Date", "Opponent", "Tournament", "Round", "Played with", "My score", "Their score", "Result", "First to"),
                data.matches.sortedBy { it.startedAt }.map { m ->
                    val s = matchScore(m.id, data.points)
                    val with = m.deckId?.let { decks[it]?.name } ?: m.comboId?.let { combos[it] }?.let { comboName(it, parts) }.orEmpty()
                    listOf(day.format(Date(m.startedAt)), m.opponent, m.tournamentId?.let { tournaments[it]?.name }.orEmpty(), m.round, with,
                        s.me.toString(), s.opponent.toString(),
                        when (matchOutcome(m, s)) { true -> "W"; false -> "L"; null -> "In progress" },
                        m.pointsToWin.toString())
                },
            ),
        )
        val dir = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        files.forEach { (name, text) ->
            val file = DocumentsContract.createDocument(context.contentResolver, dir, "text/csv", name)
                ?: error("Couldn't create $name in that folder.")
            context.contentResolver.openOutputStream(file, "wt")!!.use { it.write(text.toByteArray()) }
        }
        return files.keys.toList()
    }

    private fun csv(header: List<String>, rows: List<List<String>>): String =
        (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } } + "\r\n"

    private fun cell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** The picked zip has no data.json, so it isn't a BX Tracker backup. */
class NotABackupException : Exception("No data.json in this zip")
