package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "student_stats")
data class StudentStats(
    @PrimaryKey val id: Int = 1,
    val xp: Int = 0,
    val level: Int = 1,
    val quizzesCompleted: Int = 0,
    val questionsAsked: Int = 0,
    val roadmapsGenerated: Int = 0
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val persona: String, // "tutor", "voice", "general"
    val role: String, // "user", "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "quiz_history")
data class QuizHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val topic: String,
    val score: Int, // 0 to 5
    val timestamp: Long = System.currentTimeMillis(),
    val xpEarned: Int
)

@Entity(tableName = "career_roadmaps")
data class CareerRoadmap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val careerTitle: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface StudentDao {
    @Query("SELECT * FROM student_stats WHERE id = 1 LIMIT 1")
    fun getStats(): Flow<StudentStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: StudentStats)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE persona = :persona ORDER BY timestamp ASC")
    fun getMessagesForPersona(persona: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE persona = :persona")
    suspend fun clearMessagesForPersona(persona: String)

    @Query("SELECT * FROM quiz_history ORDER BY timestamp DESC")
    fun getQuizHistory(): Flow<List<QuizHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizHistory(item: QuizHistory)

    @Query("SELECT * FROM career_roadmaps ORDER BY timestamp DESC")
    fun getCareerRoadmaps(): Flow<List<CareerRoadmap>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareerRoadmap(roadmap: CareerRoadmap)

    @Query("DELETE FROM career_roadmaps WHERE id = :id")
    suspend fun deleteCareerRoadmap(id: Long)
}

@Database(
    entities = [StudentStats::class, ChatMessage::class, QuizHistory::class, CareerRoadmap::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
}
