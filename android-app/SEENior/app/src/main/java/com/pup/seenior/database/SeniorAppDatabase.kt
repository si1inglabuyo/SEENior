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
    version = 7,
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
         * Adds the relationship label to Contacts, so a cached family contact shows like the SOS
         * screen does ("Agatha A. / Daughter"). Adds a column rather than recreating the table,
         * so the migration can't lose data.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Contacts ADD COLUMN relationship_label TEXT")
            }
        }

        /**
         * Records how many raw readings each `Daily_Aggregates` row was built from. Raw
         * `Sensor_Data` is purged the night it is rolled up, so the count can only be taken then.
         *
         * Nullable and not backfilled: existing rows keep `null` for "unknown", since 0 or 52
         * would be a fabrication. See [com.pup.seenior.database.entities.DailyAggregate.sampleCount].
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Daily_Aggregates ADD COLUMN sample_count INTEGER")
            }
        }

        /**
         * Adds the fall-gate evidence to Alerts. Nullable and not backfilled: earlier falls
         * keep `null` for "not recorded".
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Alerts ADD COLUMN fall_evidence TEXT")
            }
        }

        /**
         * Adds the fall tolerance note to Alerts. Nullable and not backfilled: earlier falls keep
         * `null` for "not recorded".
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Alerts ADD COLUMN fall_tolerance TEXT")
            }
        }

        /**
         * 6 -> 7: the pook the senior lives in, a label for the barangay dashboard. Both
         * columns are null for "not chosen", which is also what every existing row gets.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Seniors ADD COLUMN pook_id INTEGER")
                db.execSQL("ALTER TABLE Seniors ADD COLUMN pook_name TEXT")
            }
        }

        fun getInstance(context: Context): SeniorAppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SeniorAppDatabase::class.java,
                    "senior_app.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
