package com.memex.app.di

import android.content.Context
import com.memex.app.data.db.MemexDatabase
import com.memex.app.data.db.MemoryDao
import com.memex.app.data.repository.MemoryRepository
import com.memex.app.util.CryptoUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for the data layer.
 *
 * Binding graph:
 *   CryptoUtil (object) → passphrase: ByteArray
 *   passphrase + Context → MemexDatabase (singleton)
 *   MemexDatabase        → MemoryDao
 *   MemoryDao            → MemoryRepository (singleton)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the SQLCipher-encrypted Room database.
     *
     * The 32-byte AES passphrase is derived from the Android Keystore key
     * via [CryptoUtil.getOrCreatePassphrase]. The key is generated on first
     * launch and retrieved on subsequent launches — it never leaves the device.
     *
     * This function is only called once per process lifetime ([Singleton]).
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MemexDatabase {
        val passphrase = CryptoUtil.getOrCreatePassphrase(context)
        return MemexDatabase.create(context, passphrase)
    }

    /**
     * Provides the [MemoryDao] extracted from the singleton database instance.
     * Room generates the implementation at compile time via kapt.
     */
    @Provides
    @Singleton
    fun provideMemoryDao(database: MemexDatabase): MemoryDao =
        database.memoryDao()

    /**
     * Provides the [MemoryRepository] as a singleton.
     *
     * Note: Hilt can auto-inject [MemoryRepository] via its [@Inject] constructor,
     * so this explicit [@Provides] is kept for clarity and testability, making it
     * easy to swap in a fake repository during unit tests.
     */
    @Provides
    @Singleton
    fun provideMemoryRepository(memoryDao: MemoryDao): MemoryRepository =
        MemoryRepository(memoryDao)
}
