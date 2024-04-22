package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `notification` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `time` INTEGER NOT NULL)")
    }
}

@Database(
    entities = [ApplicationEntity::class, ApplicationPermissionsEntity::class, NotificationEntity::class],
    version = 2
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao

    companion object {
        fun getDatabase(context: Context, npub: String): AppDatabase {
            return synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "amber_db_$npub"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                instance
            }
        }
    }
}
