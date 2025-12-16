package com.gobex.smartreadingassistant.feature.conversation.data.source

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gobex.smartreadingassistant.feature.conversation.data.source.entities.MessageEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface ConversationDAO
{
    // Get all messages, ordered by time. Used for UI Flow and API calls.
    @Query("SELECT * FROM conversation_history ORDER BY timestamp ASC")
    fun getAllMessages() : Flow<List<MessageEntity>>

    // Get all messages as a list (faster for one-time API request building)
    @Query("SELECT * FROM conversation_history ORDER BY timestamp ASC")
    suspend fun getHistoryList() : List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM conversation_history")
    suspend fun clearHistory()
}