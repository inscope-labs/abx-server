package com.inscopelabs.abx.server.workspace.chat

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Entities
@Entity(tableName = "sessions")
@TypeConverters(Converters::class)
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val provider: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val settings: ChatSettings,
    val messages: List<Message> // stored as JSON string via converter
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // enum name
    val content: String,
    val attachments: String, // JSON of List<Attachment>
    val tokenCount: Int,
    val timestamp: Long,
    val status: String // enum name
)

// Converters
class Converters {
    @TypeConverter
    fun fromSettings(settings: ChatSettings): String = settings.toJson()

    @TypeConverter
    fun toSettings(json: String): ChatSettings = ChatSettings.fromJson(json)

    @TypeConverter
    fun fromMessages(messages: List<Message>): String = messages.toJson()

    @TypeConverter
    fun toMessages(json: String): List<Message> = Message.fromJsonArray(json)

    @TypeConverter
    fun fromAttachments(attachments: List<Attachment>): String = attachments.toJson()

    @TypeConverter
    fun toAttachments(json: String): List<Attachment> = Attachment.fromJsonArray(json)
}

// DAO
@Dao
interface ChatDao {
    @Query("SELECT * FROM sessions")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    // Messages are part of SessionEntity, so we don't need separate CRUD
    // But we can add convenience for adding a message via session update
}

@Database(entities = [SessionEntity::class], version = 1)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository implementation using Room
class ChatRepository(private val dao: ChatDao) {

    suspend fun getSession(sessionId: String): ChatSession? {
        val entity = dao.getSession(sessionId) ?: return null
        return entity.toDomain()
    }

    suspend fun saveSession(session: ChatSession) {
        dao.saveSession(session.toEntity())
    }

    suspend fun deleteSession(sessionId: String) {
        val entity = dao.getSession(sessionId) ?: return
        dao.deleteSession(entity)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    fun observeSessions(): Flow<List<ChatSession>> {
        return dao.observeAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addMessage(sessionId: String, message: Message) {
        val session = getSession(sessionId) ?: return
        val updatedMessages = session.messages + message
        val updated = session.copy(messages = updatedMessages, updatedAt = System.currentTimeMillis())
        saveSession(updated)
    }
}

// Extension functions for conversion
fun SessionEntity.toDomain(): ChatSession {
    return ChatSession(
        id = id,
        title = title,
        provider = provider,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        settings = settings,
        messages = messages
    )
}

fun ChatSession.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        title = title,
        provider = provider,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        settings = settings,
        messages = messages
    )
}