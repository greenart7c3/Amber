package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(
    entities = [
        LogEntity::class,
    ],
    version = 2,
)
@TypeConverters(Converters::class)
abstract class LogDatabase : RoomDatabase() {
    abstract fun dao(): LogDao

    companion object {
        val migration_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `log_by_time_key` ON `amber_log` (`time` DESC)")
            }
        }

        fun getDatabase(
            context: Context,
            npub: String,
        ): LogDatabase = synchronized(this) {
            val executor = Executors.newCachedThreadPool()
            val transactionExecutor = Executors.newCachedThreadPool()

            val instance =
                Room.databaseBuilder(
                    context,
                    LogDatabase::class.java,
                    "log_db_$npub",
                )
                    .setQueryExecutor(executor)
                    .setTransactionExecutor(transactionExecutor)
                    .addMigrations(migration_1_2)
                    .build()

            instance
        }
    }
}
