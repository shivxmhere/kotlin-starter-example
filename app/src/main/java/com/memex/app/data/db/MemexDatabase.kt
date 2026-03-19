package com.memex.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher-encrypted Room database.
 *
 * Encryption strategy:
 *   - The 32-byte AES passphrase is generated once per install via [CryptoUtil]
 *     (backed by Android Keystore) and never leaves the device.
 *   - [SupportOpenHelperFactory] from net.zetetic:android-database-sqlcipher
 *     transparently encrypts every page of the underlying SQLite file.
 *
 * Usage:
 *   Instantiate exactly once through Hilt's [com.memex.app.di.AppModule].
 *   Never call [create] directly from application code.
 */
@Database(
    entities = [MemoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MemexDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DB_NAME = "memex_vault.db"

        /**
         * Build an encrypted Room database instance.
         *
         * @param context   Application context.
         * @param passphrase 32-byte AES key returned by [com.memex.app.util.CryptoUtil.getOrCreatePassphrase].
         */
        fun create(context: Context, passphrase: ByteArray): MemexDatabase {
            // Load the native SQLCipher library before Room tries to open the DB
            net.zetetic.database.sqlcipher.SQLiteDatabase.loadLibs(context)

            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context,
                MemexDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                // Destructive migration is acceptable for v1 — add proper migrations later
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
