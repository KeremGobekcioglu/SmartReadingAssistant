package com.gobex.smartreadingassistant.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gobex.smartreadingassistant.core.db.AppDatabase
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationDAO
import com.gobex.smartreadingassistant.feature.device.data.local.DeviceConnectionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smart_glasses_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideConversationDAO(database: AppDatabase): ConversationDAO {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideDeviceConnectionDao(database: AppDatabase): DeviceConnectionDao {
        return database.deviceConnectionDao()
    }
}

// Migration lives OUTSIDE the module
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS device_connections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                device_ip TEXT NOT NULL,
                ssid TEXT NOT NULL,
                connected_at INTEGER NOT NULL,
                disconnected_at INTEGER,
                is_active INTEGER NOT NULL DEFAULT 1,
                connection_method TEXT NOT NULL,
                last_health_check INTEGER NOT NULL
            )
        """)
    }
}