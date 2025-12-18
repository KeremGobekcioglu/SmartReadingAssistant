package com.gobex.smartreadingassistant.core.di

import com.gobex.smartreadingassistant.feature.conversation.data.LLMApiService
import com.gobex.smartreadingassistant.feature.conversation.data.repository.LLMRepositoryImpl
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationDAO
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationLocalDataSource
import com.gobex.smartreadingassistant.feature.conversation.data.source.DBImpl
import com.gobex.smartreadingassistant.feature.conversation.domain.LLMRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // 1. Bind LLMRepository to LLMRepositoryImpl
    @Binds
    @Singleton
    abstract fun bindLLMRepository(
        impl: LLMRepositoryImpl
    ): LLMRepository

    // 2. Bind ConversationLocalDataSource to DBImpl
    @Binds
    @Singleton
    abstract fun bindConversationLocalDataSource(
        impl: DBImpl
    ): ConversationLocalDataSource
}
//@Module
//@InstallIn(SingletonComponent::class)
//object RepositoryModule {
//
//    @Provides
//    @Singleton
//    fun provideLLMRepository(
//        apiService: LLMApiService,
//        gson: Gson,
//        db : ConversationLocalDataSource
//    ): LLMRepository {
//        return LLMRepositoryImpl(apiService , gson , db)
//    }
//
//    @Provides
//    @Singleton
//    fun provideConversationLocalDataSource(
//        dao: ConversationDAO
//    ) : ConversationLocalDataSource
//    {
//        return DBImpl(dao)
//    }
//}