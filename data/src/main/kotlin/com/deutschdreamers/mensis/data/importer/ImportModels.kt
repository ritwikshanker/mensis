package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.domain.model.DayEntry

/** Where a file comes from (design §14 step 1). Stored with each import batch by name. */
enum class ImportSource {
    /** Any spreadsheet with one row per day, including Mensis's own CSV export. */
    CSV,

    /** Mensis's JSON export, lossless. */
    MENSIS,

    /** drip. (bloodyhealth/drip), CSV export of database schema 4. */
    DRIP,

    /** Clue "download your data" JSON. Experimental: built against an assumed structure. */
    CLUE,
}

/** What happens to a day that already holds data on this phone (design: Merge / Replace / Skip). */
enum class ConflictStrategy {
    /** Keeps what was logged; fills only empty fields from the file. */
    MERGE,

    /** The file's entry replaces the phone's entry for that day. */
    REPLACE,

    /** Days that already hold data are left untouched. */
    SKIP,
}

enum class SkipReason {
    UNREADABLE_DATE,
    FUTURE_DATE,
    NO_DATA,
}

/** A row (CSV line, JSON element) that produced no entry. [row] is 1-based in the file, header included. */
data class SkippedRow(
    val row: Int,
    val reason: SkipReason,
    val value: String? = null,
)

enum class IssueKind {
    /** The source tracks something Mensis does not (drip's cervix, Clue's energy levels). */
    NOT_TRACKED,

    /** A value Mensis could not read, such as an unknown mood word; the rest of the row is imported. */
    UNKNOWN_VALUE,
}

/** Something left out of an otherwise imported row, counted per field. */
data class ImportIssue(
    val field: String,
    val kind: IssueKind,
    val count: Int,
    val example: String? = null,
)

/**
 * Rows kept exactly as a Mensis JSON export had them, so an import onto an empty phone reproduces the
 * stored rows (ids, timestamps, tombstones) and the method history and settings come along.
 */
data class LosslessRows(
    val dayEntries: Map<String, DayEntryEntity>,
    val tombstones: List<DayEntryEntity>,
    val contraception: List<ContraceptionPeriodEntity>,
    val settings: TrackerSettingsEntity?,
)

/** A file read into day entries, before anything touches the database. */
data class ParsedImport(
    val source: ImportSource,
    val rowsRead: Int,
    /** One entry per date, sorted, none empty. Rows sharing a date are combined. */
    val days: List<DayEntry>,
    val skipped: List<SkippedRow>,
    val issues: List<ImportIssue>,
    val lossless: LosslessRows? = null,
) {
    val validRows: Int get() = rowsRead - skipped.size
}

/** Collects the per-row results of a parser into a [ParsedImport]. */
internal class ParseCollector(
    private val source: ImportSource,
) {
    private val byDate = sortedMapOf<java.time.LocalDate, DayEntry>()
    private val skipped = mutableListOf<SkippedRow>()
    private val issueCounts = linkedMapOf<Pair<String, IssueKind>, Int>()
    private val issueExamples = mutableMapOf<Pair<String, IssueKind>, String>()
    var rowsRead: Int = 0
        private set

    fun row() {
        rowsRead++
    }

    fun skip(
        row: Int,
        reason: SkipReason,
        value: String? = null,
    ) {
        skipped += SkippedRow(row, reason, value)
    }

    fun issue(
        field: String,
        kind: IssueKind,
        example: String? = null,
    ) {
        val key = field to kind
        issueCounts[key] = (issueCounts[key] ?: 0) + 1
        if (example != null) issueExamples.putIfAbsent(key, example)
    }

    fun add(entry: DayEntry) {
        byDate[entry.date] = byDate[entry.date]?.let { combine(it, entry) } ?: entry
    }

    fun result(lossless: LosslessRows? = null): ParsedImport =
        ParsedImport(
            source = source,
            rowsRead = rowsRead,
            days = byDate.values.toList(),
            skipped = skipped.sortedBy { it.row },
            issues = issueCounts.map { (key, count) -> ImportIssue(key.first, key.second, count, issueExamples[key]) },
            lossless = lossless,
        )

    /** Two rows for the same day: sets and notes are joined, the first row wins for single values. */
    private fun combine(
        first: DayEntry,
        second: DayEntry,
    ): DayEntry =
        EntryMerge.fillEmpty(first, second).copy(
            symptoms = first.symptoms + second.symptoms,
            moods = first.moods + second.moods,
            notes =
                listOfNotNull(first.notes, second.notes)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("\n")
                    .ifEmpty { null },
        )
}
