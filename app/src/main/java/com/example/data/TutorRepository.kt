package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TutorRepository(private val dao: StudentDao) {

    val statsFlow: Flow<StudentStats?> = dao.getStats()
    val quizHistoryFlow: Flow<List<QuizHistory>> = dao.getQuizHistory()
    val roadmapsFlow: Flow<List<CareerRoadmap>> = dao.getCareerRoadmaps()

    suspend fun ensureStatsCreated() {
        val current = dao.getStats().firstOrNull()
        if (current == null) {
            dao.insertStats(StudentStats(id = 1, xp = 0, level = 1))
        }
    }

    suspend fun addXp(amount: Int, quizCompleted: Boolean = false, questionAsked: Boolean = false, roadmapGenerated: Boolean = false) {
        // Find existing stats
        var current: StudentStats? = null
        dao.getStats().collect {
            current = it
            return@collect
        }
        val stats = current ?: StudentStats(id = 1)
        val newXp = stats.xp + amount
        val newLevel = (newXp / 100) + 1

        dao.insertStats(
            stats.copy(
                xp = newXp,
                level = newLevel,
                quizzesCompleted = stats.quizzesCompleted + (if (quizCompleted) 1 else 0),
                questionsAsked = stats.questionsAsked + (if (questionAsked) 1 else 0),
                roadmapsGenerated = stats.roadmapsGenerated + (if (roadmapGenerated) 1 else 0)
            )
        )
    }

    fun getChatMessages(persona: String): Flow<List<ChatMessage>> {
        return dao.getMessagesForPersona(persona)
    }

    suspend fun insertChatMessage(persona: String, role: String, text: String) {
        dao.insertMessage(
            ChatMessage(
                persona = persona,
                role = role,
                text = text
            )
        )
    }

    suspend fun clearChatHistory(persona: String) {
        dao.clearMessagesForPersona(persona)
    }

    suspend fun insertQuizResult(topic: String, score: Int, xpEarned: Int) {
        dao.insertQuizHistory(
            QuizHistory(
                topic = topic,
                score = score,
                xpEarned = xpEarned
            )
        )
        addXp(xpEarned, quizCompleted = true)
    }

    suspend fun insertRoadmap(title: String, content: String) {
        dao.insertCareerRoadmap(
            CareerRoadmap(
                careerTitle = title,
                content = content
            )
        )
        addXp(30, roadmapGenerated = true)
    }

    suspend fun deleteRoadmap(id: Long) {
        dao.deleteCareerRoadmap(id)
    }
}
