package com.deutschdreamers.mensis.data.db

/** Every row of the health tables as stored, tombstones included: what an export writes and an import starts from. */
data class StoredRows(
    val dayEntries: List<DayEntryEntity>,
    val contraception: List<ContraceptionPeriodEntity>,
    val settings: TrackerSettingsEntity?,
)
