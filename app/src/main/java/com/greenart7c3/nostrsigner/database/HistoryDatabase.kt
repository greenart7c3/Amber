package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

val migration_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN translatedPermission TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        HistoryEntity::class,
    ],
    version = 2,
)
@TypeConverters(Converters::class)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        fun getDatabase(
            context: Context,
            npub: String,
        ): HistoryDatabase = synchronized(this) {
            val executor = Executors.newCachedThreadPool()
            val transactionExecutor = Executors.newCachedThreadPool()

            val instance =
                Room.databaseBuilder(
                    context,
                    HistoryDatabase::class.java,
                    "history_db_$npub",
                )
                    .setQueryExecutor(executor)
                    .setTransactionExecutor(transactionExecutor)
                    .addMigrations(migration_1_2)
                    .build()
            instance.openHelper.writableDatabase.execSQL("VACUUM")

            instance
        }
    }
}
