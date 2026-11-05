package com.kai.assistant

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String,
    val status: String,
    val createdAt: Long
)

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(item: ConversationEntity): Long

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(item: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id")
    fun observe(id: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :id ORDER BY id DESC LIMIT :limit) ORDER BY id")
    suspend fun recent(id: Long, limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteFor(id: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(item: MemoryEntity): Long

    @Update
    suspend fun update(item: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY id DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY id")
    suspend fun all(): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clear()
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(item: ActivityEntity): Long

    @Query("SELECT * FROM activity ORDER BY id DESC LIMIT 200")
    fun observeRecent(): Flow<List<ActivityEntity>>

    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        ActivityEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KaiDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun memories(): MemoryDao
    abstract fun activity(): ActivityDao

    companion object {
        @Volatile
        private var instance: KaiDatabase? = null

        fun get(context: Context): KaiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaiDatabase::class.java,
                    "kai.db"
                ).build().also { instance = it }
            }
    }
}
