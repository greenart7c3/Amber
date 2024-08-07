package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notification` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `time` INTEGER NOT NULL)",
            )
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pkKey` TEXT NOT NULL, `type` TEXT NOT NULL, `kind` INTEGER, `time` INTEGER NOT NULL, `accepted` INTEGER NOT NULL, FOREIGN KEY(`pkKey`) REFERENCES `application`(`key`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_pk_key` ON `history` (`pkKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_id` ON `history` (`id`)")
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `amber_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `type` TEXT NOT NULL, `message` TEXT NOT NULL, `time` INTEGER NOT NULL)")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `application` ADD COLUMN `signPolicy` INTEGER NOT NULL DEFAULT 1")
        }
    }

@Database(
    entities = [
        ApplicationEntity::class,
        ApplicationPermissionsEntity::class,
        NotificationEntity::class,
        HistoryEntity::class,
        LogEntity::class,
    ],
    version = 5,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao

    companion object {
        fun getDatabase(
            context: Context,
            npub: String,
        ): AppDatabase {
            return synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        "amber_db_$npub",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .addMigrations(MIGRATION_2_3)
                        .addMigrations(MIGRATION_3_4)
                        .addMigrations(MIGRATION_4_5)
                        .build()
                instance
            }
        }
    }
}
