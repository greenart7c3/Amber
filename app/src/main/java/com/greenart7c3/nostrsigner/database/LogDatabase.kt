package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.concurrent.Executors

@Database(
    entities = [
        LogEntity::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        fun getDatabase(
            context: Context,
            npub: String,
        ): LogDatabase {
            return synchronized(this) {
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
                        .build()
                instance.openHelper.writableDatabase.execSQL("VACUUM")

                instance
            }
        }
    }
}
