package com.garam.hackathon.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.garam.hackathon.data.db.dao.AppStateDao
import com.garam.hackathon.data.db.dao.CapabilityDao
import com.garam.hackathon.data.db.dao.EraseDao
import com.garam.hackathon.data.db.dao.IntervalDao
import com.garam.hackathon.data.db.dao.SummaryDao
import com.garam.hackathon.data.db.dao.TripDao
import com.garam.hackathon.data.db.entity.AppStateEntity
import com.garam.hackathon.data.db.entity.CapabilitySnapshotEntity
import com.garam.hackathon.data.db.entity.CounterEventEntity
import com.garam.hackathon.data.db.entity.DailySummaryEntity
import com.garam.hackathon.data.db.entity.FeatureIntervalEntity
import com.garam.hackathon.data.db.entity.TripEntity
import com.garam.hackathon.data.db.entity.VehicleSnapshotEntity

@Database(
    entities = [
        FeatureIntervalEntity::class,
        AppStateEntity::class,
        CounterEventEntity::class,
        TripEntity::class,
        VehicleSnapshotEntity::class,
        CapabilitySnapshotEntity::class,
        DailySummaryEntity::class,
    ],
    version = 3,
    exportSchema = true,
    // Versions 2 and 3 only add tables, so Room can generate the migrations.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class CarMeterDatabase : RoomDatabase() {
    abstract fun intervalDao(): IntervalDao

    abstract fun appStateDao(): AppStateDao

    abstract fun tripDao(): TripDao

    abstract fun capabilityDao(): CapabilityDao

    abstract fun summaryDao(): SummaryDao

    abstract fun eraseDao(): EraseDao

    companion object {
        fun create(context: Context): CarMeterDatabase =
            Room.databaseBuilder(context.applicationContext, CarMeterDatabase::class.java, "carmeter.db").build()
    }
}
