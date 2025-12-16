package com.gobex.smartreadingassistant.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gobex.smartreadingassistant.feature.conversation.data.source.ConversationDAO
import com.gobex.smartreadingassistant.feature.conversation.data.source.entities.MessageEntity

@Database(entities = [MessageEntity::class] , version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase()
{
    abstract fun conversationDao() : ConversationDAO
}