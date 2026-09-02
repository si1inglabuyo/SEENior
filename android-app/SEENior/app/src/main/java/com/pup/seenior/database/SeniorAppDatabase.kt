package com.pup.seenior.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pup.seenior.database.dao.AlertDao
import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.ContactDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.dao.FalsePositiveDao
import com.pup.seenior.database.dao.MlModelMetadataDao
import com.pup.seenior.database.dao.PendingAlertDao
import com.pup.seenior.database.dao.SeniorDao
import com.pup.seenior.database.dao.SeniorOnboardingDao
import com.pup.seenior.database.dao.SensorDataDao
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.Contact
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.FalsePositive
import com.pup.seenior.database.entities.MlModelMetadata
import com.pup.seenior.database.entities.PendingAlert
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.database.entities.SensorData

@Database(
    entities = [
        Senior::class,
        Contact::class,
        SensorData::class,
        DailyAggregate::class,
        Baseline::class,
        Alert::class,
        PendingAlert::class,
        FalsePositive::class,
        MlModelMetadata::class,
        SeniorOnboarding::class
    ],
    version = 3,
    exportSchema = true
)
abstract class SeniorAppDatabase : RoomDatabase() {

    abstract fun seniorDao(): SeniorDao
    abstract fun contactDao(): ContactDao
    abstract fun sensorDataDao(): SensorDataDao
    abstract fun dailyAggregateDao(): DailyAggregateDao
    abstract fun baselineDao(): BaselineDao
    abstract fun alertDao(): AlertDao
    abstract fun pendingAlertDao(): PendingAlertDao
    abstract fun falsePositiveDao(): FalsePositiveDao
    abstract fun mlModelMetadataDao(): MlModelMetadataDao
    abstract fun seniorOnboardingDao(): SeniorOnboardingDao

    companion object {
        @Volatile
        private var INSTANCE: SeniorAppDatabase? = null

        /** Adds the cloud_sync_id column (real migration, so existing onboarded-senior data survives). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Seniors ADD COLUMN cloud_sync_id TEXT")
            }
        }

        /**
         * Adds the relationship label to Contacts, so a cached family contact can be shown the
         * way the SOS screen shows it -- "Agatha A. / Daughter", not a bare name.
         *
         * Adding rather than recreating even though the table is empty on every device today:
         * nothing had ever written to Contacts before this change, but a migration that drops a
         * table is a migration that can lose data if that ever stops being true.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Contacts ADD COLUMN relationship_label TEXT")
            }
        }

        fun getInstance(context: Context): SeniorAppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeniorAppDatabase::class.java,
                    "senior_app.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
