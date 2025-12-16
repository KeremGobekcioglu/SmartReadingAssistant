package com.gobex.smartreadingassistant.feature.conversation.data.source

import com.gobex.smartreadingassistant.feature.conversation.data.source.entities.toDomain
import com.gobex.smartreadingassistant.feature.conversation.data.source.entities.toEntity
import com.gobex.smartreadingassistant.feature.conversation.domain.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DBImpl @Inject constructor(
    private val conversationDAO: ConversationDAO
) : ConversationLocalDataSource
{
    override suspend fun addMessage(message: Message) {
        conversationDAO.insertMessage(message.toEntity())
    }

    override suspend fun getCurrentHistory(): List<Message> {
        return conversationDAO.getHistoryList().map {
            it.toDomain()
        }
    }

    override fun getMessagesFlow(): Flow<List<Message>> {
        return conversationDAO.getAllMessages().map {
            entities -> entities.map { it.toDomain() }
        }
    }

    override suspend fun clearHistory() {
        conversationDAO.clearHistory()
    }

}