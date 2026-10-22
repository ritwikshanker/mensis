package com.deutschdreamers.mensis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DayEntryEntity::class,
        ContraceptionPeriodEntity::class,
        TrackerSettingsEntity::class,
        ImportBatchEntity::class,
        ImportUndoRowEntity::class,
    ],
    version = MensisDatabase.VERSION,
    exportSchema = true,
)
abstract class MensisDatabase : RoomDatabase() {
    abstract fun dayEntryDao(): DayEntryDao

    abstract fun contraceptionDao(): ContraceptionDao

    abstract fun trackerSettingsDao(): TrackerSettingsDao

    abstract fun importDao(): ImportDao

    companion object {
        const val VERSION: Int = 1
    }
}
