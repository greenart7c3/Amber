package com.greenart7c3.nostrsigner.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greenart7c3.nostrsigner.Amber
import java.util.concurrent.Executors

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

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `application` ADD COLUMN `closeApplication` INTEGER NOT NULL DEFAULT 1")
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_eventId` ON `notification` (`eventId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_time` ON `notification` (`time`)")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `history2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pkKey` TEXT NOT NULL, `type` TEXT NOT NULL, `kind` INTEGER, `time` INTEGER NOT NULL, `accepted` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_pk_key2` ON `history2` (`pkKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_id2` ON `history2` (`id`)")
            db.execSQL("INSERT INTO history2 SELECT * FROM history")
            db.execSQL("DELETE FROM history")
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `applicationPermission` ADD COLUMN `rememberType` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `applicationPermission` ADD COLUMN `acceptUntil` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `applicationPermission` ADD COLUMN `rejectUntil` INTEGER NOT NULL DEFAULT 0")
            val cursor = db.query("SELECT * FROM applicationPermission")
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val acceptable = cursor.getInt(cursor.getColumnIndexOrThrow("acceptable"))
                val until = Long.MAX_VALUE / 1000
                if (acceptable == 1) {
                    db.execSQL("UPDATE applicationPermission SET acceptUntil = $until, rejectUntil = 0, rememberType = 4 WHERE id = $id")
                } else {
                    db.execSQL("UPDATE applicationPermission SET rejectUntil = $until, acceptUntil = 0, rememberType = 4 WHERE id = $id")
                }
            }
        }
    }

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE application ADD COLUMN deleteAfter INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_time` ON `history2` (`time`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `history_by_key_and_time` ON `history2` (`pkKey`, `time`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("DROP TABLE notification")
        } catch (e: Exception) {
            Log.e(Amber.TAG, "No notification table", e)
        }
    }
}

@Database(
    entities = [
        ApplicationEntity::class,
        ApplicationPermissionsEntity::class,
        HistoryEntity::class,
        LogEntity::class,
        HistoryEntity2::class,
    ],
    version = 12,
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
                val executor = Executors.newCachedThreadPool()
                val transactionExecutor = Executors.newCachedThreadPool()

                val instance =
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        "amber_db_$npub",
                    )
                        .setQueryExecutor(executor)
                        .setTransactionExecutor(transactionExecutor)
                        .addMigrations(MIGRATION_1_2)
                        .addMigrations(MIGRATION_2_3)
                        .addMigrations(MIGRATION_3_4)
                        .addMigrations(MIGRATION_4_5)
                        .addMigrations(MIGRATION_5_6)
                        .addMigrations(MIGRATION_6_7)
                        .addMigrations(MIGRATION_7_8)
                        .addMigrations(MIGRATION_8_9)
                        .addMigrations(MIGRATION_9_10)
                        .addMigrations(MIGRATION_10_11)
                        .addMigrations(MIGRATION_11_12)
                        .build()
                instance.openHelper.writableDatabase.execSQL("VACUUM")

                instance
            }
        }
    }
}
