package com.gobex.smartreadingassistant.core.di

import android.content.Context
import androidx.room.Room
import com.gobex.smartreadingassistant.core.db.AppDatabase
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationDAO
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // 1. Tell Hilt how to build the Database
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smart_reading_db" // Name of your database file
        )
            .fallbackToDestructiveMigration() // Optional: Clears DB if you change schema
            .build()
    }

    // 2. Tell Hilt how to get the DAO (This fixes your error!)
    @Provides
    fun provideConversationDAO(database: AppDatabase): ConversationDAO {
        return database.conversationDao()
    }
}