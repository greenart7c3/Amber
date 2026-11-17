package com.greenart7c3.nostrsigner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import java.util.concurrent.Executors

val migration_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN translatedPermission TEXT NOT NULL DEFAULT ''")
    }
}

val migration_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN content TEXT NOT NULL DEFAULT ''")
    }
}

val migration_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history ADD COLUMN encryptionPubKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE history ADD COLUMN encryptionScope TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        HistoryEntity::class,
    ],
    version = 4,
)
@TypeConverters(Converters::class)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    // Set by getDatabase: HistoryDao.addHistory honors
    // the privacy mode and refreshes the cached permission DAO.
    lateinit var settingsStore: SettingsStore
    lateinit var accountRepository: AccountRepository

    companion object {
        /** On-disk name of [npub]'s database, also used to delete it at logout. */
        fun fileName(npub: String) = "history_db_$npub"

        fun getDatabase(
            context: Context,
            npub: String,
            settingsStore: SettingsStore,
            accountRepository: AccountRepository,
        ): HistoryDatabase = synchronized(this) {
            val executor = Executors.newCachedThreadPool()
            val transactionExecutor = Executors.newCachedThreadPool()

            val instance =
                Room.databaseBuilder(
                    context,
                    HistoryDatabase::class.java,
                    fileName(npub),
                )
                    .setQueryExecutor(executor)
                    .setTransactionExecutor(transactionExecutor)
                    .addMigrations(migration_1_2, migration_2_3, migration_3_4)
                    .build()

            instance.also {
                it.settingsStore = settingsStore
                it.accountRepository = accountRepository
            }
        }
    }
}
