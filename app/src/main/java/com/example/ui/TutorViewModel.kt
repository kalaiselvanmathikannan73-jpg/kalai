package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.TutorApplication
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.api.InlineData
import com.example.data.CareerRoadmap
import com.example.data.ChatMessage
import com.example.data.QuizHistory
import com.example.data.StudentStats
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Quiz data classes
data class LocalQuizQuestion(
    val question: String,
    val A: String,
    val B: String,
    val C: String,
    val D: String,
    val answer: String,
    val explanation: String
)

data class LocalGeneratedQuiz(
    val topic: String,
    val questions: List<LocalQuizQuestion>
)

data class GKQuestion(
    val category: String,
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String,
    val explanation: String
)

class TutorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TutorApplication.repository
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // Onboarding / Login Preferences State
    private val prefs = application.getSharedPreferences("tutor_prefs", android.content.Context.MODE_PRIVATE)
    val isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val studentName = MutableStateFlow(prefs.getString("student_name", "") ?: "")
    val studentClass = MutableStateFlow(prefs.getString("student_class", "") ?: "")
    val studentLanguage = MutableStateFlow(prefs.getString("student_language", "English") ?: "English")

    fun registerStudent(name: String, className: String, lang: String) {
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("student_name", name)
            putString("student_class", className)
            putString("student_language", lang)
            apply()
        }
        isLoggedIn.value = true
        studentName.value = name
        studentClass.value = className
        studentLanguage.value = lang
    }

    fun logoutStudent() {
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            apply()
        }
        isLoggedIn.value = false
    }

    // Story Lesson state ("Lessons as a story")
    val storyTopicInput = MutableStateFlow("")
    val activeStoryContent = MutableStateFlow<String?>(null)
    val isStoryGenerating = MutableStateFlow(false)

    // Exam Important Questions state
    val examTopicInput = MutableStateFlow("")
    val examQuestionsContent = MutableStateFlow<String?>(null)
    val isExamQuestionsGenerating = MutableStateFlow(false)

    // News & Updates hub state
    val selectedNewsCategory = MutableStateFlow("school") // "school", "scholarship", "tnpsc"
    val newsContent = MutableStateFlow<String?>(null)
    val isNewsLoading = MutableStateFlow(false)

    // NEW PREFERENCES & HUB STATE FLOWS
    // Study Planner states
    val plannerSubjectsInput = MutableStateFlow("")
    val plannerHoursInput = MutableStateFlow("2")
    val plannerDurationInput = MutableStateFlow("7 Days")
    val plannerContent = MutableStateFlow<String?>(prefs.getString("planner_content", null))
    val isPlannerLoading = MutableStateFlow(false)
    val completedSlots = MutableStateFlow<Set<String>>(prefs.getStringSet("completed_slots", emptySet()) ?: emptySet())

    // Doubt Solver states
    val doubtInput = MutableStateFlow("")
    val doubtImageBase64 = MutableStateFlow<String?>(null)
    val doubtImageMimeType = MutableStateFlow<String?>(null)
    val doubtSolution = MutableStateFlow<String?>(null)
    val isDoubtLoading = MutableStateFlow(false)
    val scannerStatusMessage = MutableStateFlow("")

    // GK Trivia Game states
    val activeGkQuestion = MutableStateFlow<GKQuestion?>(null)
    val selectedGkAnswer = MutableStateFlow<String?>(null)
    val isGkAnswerChecked = MutableStateFlow(false)
    val isGkGenerating = MutableStateFlow(false)
    val gkCorrectCount = MutableStateFlow(prefs.getInt("gk_correct_count", 0))
    val gkTotalCount = MutableStateFlow(prefs.getInt("gk_total_count", 0))
    val gkAnswerFeedback = MutableStateFlow("")
    val gkCategory = MutableStateFlow("Science")

    // Ask Anything states
    val askAnythingOutput = MutableStateFlow<String?>(null)
    val isAskAnythingLoading = MutableStateFlow(false)

    // UI Tab selection: "dashboard", "tutor", "quiz", "career", "voice", "general", "history"
    val currentTab = MutableStateFlow("dashboard")

    // Stats and reactive data from Room
    val statsState: StateFlow<StudentStats?> = repository.statsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val quizHistoryState: StateFlow<List<QuizHistory>> = repository.quizHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roadmapsState: StateFlow<List<CareerRoadmap>> = repository.roadmapsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat states
    val tutorMessages: StateFlow<List<ChatMessage>> = repository.getChatMessages("tutor")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceMessages: StateFlow<List<ChatMessage>> = repository.getChatMessages("voice")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalMessages: StateFlow<List<ChatMessage>> = repository.getChatMessages("general")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loading flows
    val isTutorLoading = MutableStateFlow(false)
    val isVoiceLoading = MutableStateFlow(false)
    val isGeneralLoading = MutableStateFlow(false)
    val isQuizGenerating = MutableStateFlow(false)
    val isRoadmapGenerating = MutableStateFlow(false)

    // User chats text field state
    val tutorInput = MutableStateFlow("")
    val voiceInput = MutableStateFlow("")
    val generalInput = MutableStateFlow("")

    // Quiz workflow state
    val quizTopicInput = MutableStateFlow("")
    val activeQuiz = MutableStateFlow<LocalGeneratedQuiz?>(null)
    val currentQuestionIndex = MutableStateFlow(0)
    val selectedAnswer = MutableStateFlow<String?>(null) // "A", "B", "C", "D"
    val isAnswerChecked = MutableStateFlow(false)
    val quizScore = MutableStateFlow(0)
    val isQuizFinished = MutableStateFlow(false)
    val gamificationMessage = MutableStateFlow<String?>(null)
    val isGamificationLoading = MutableStateFlow(false)

    // Career Roadmap active state
    val careerInput = MutableStateFlow("")
    val activeRoadmapContent = MutableStateFlow<String?>(null)
    val activeRoadmapTitle = MutableStateFlow("")

    // Moshi instance for Quiz Parsing
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        viewModelScope.launch {
            repository.ensureStatsCreated()
        }
    }

    // 1. MASTER AI TUTOR CHAT
    fun sendTutorMessage() {
        val text = tutorInput.value.trim()
        if (text.isEmpty()) return
        tutorInput.value = ""

        viewModelScope.launch {
            isTutorLoading.value = true
            repository.insertChatMessage("tutor", "user", text)
            repository.addXp(5, questionAsked = true) // award 5 XP for asking a study question!

            // Pull conversation history
            val currentHistory = tutorMessages.value
            val apiHistory = currentHistory.map {
                Content(parts = listOf(Part(text = it.text)))
            } + Content(parts = listOf(Part(text = text)))

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are an AI tutor for school students.
                            You help with: Studies, Homework, General knowledge, Career guidance, and Problem solving.
                            Language rule: Reply ONLY in the same language as the user's input. Do NOT mix languages.
                            Style: Simple words, step-by-step explanation, student-friendly, short sentences. Use examples if needed.
                            Focus strictly on education and learning.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = apiHistory,
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I was unable to understand that. Let's try again!"
                repository.insertChatMessage("tutor", "model", replyText)
            } catch (e: Exception) {
                repository.insertChatMessage("tutor", "model", "Oops! I disconnected from my learning portal. Please check your internet connection and try again.")
            } finally {
                isTutorLoading.value = false
            }
        }
    }

    fun clearTutorChat() {
        viewModelScope.launch {
            repository.clearChatHistory("tutor")
        }
    }

    // 2. GENERAL CHAT (STUDENT ASSISTANT)
    fun sendGeneralMessage() {
        val text = generalInput.value.trim()
        if (text.isEmpty()) return
        generalInput.value = ""

        viewModelScope.launch {
            isGeneralLoading.value = true
            repository.insertChatMessage("general", "user", text)
            repository.addXp(5, questionAsked = true)

            val currentHistory = generalMessages.value
            val apiHistory = currentHistory.map {
                Content(parts = listOf(Part(text = it.text)))
            } + Content(parts = listOf(Part(text = text)))

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are a helpful AI assistant for students.
                            You can answer any student questions: Studies, Knowledge, Life doubts, Career guidance, and general information.
                            Rules: Reply in the same language as the user's input. Simple explanation, step-by-step when needed, student-friendly.
                            Do not give harmful, adult, or unrelated content.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = apiHistory,
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Sorry, let me think and try again."
                repository.insertChatMessage("general", "model", replyText)
            } catch (e: Exception) {
                repository.insertChatMessage("general", "model", "I'm having trouble retrieving knowledge. Let's try again in a bit!")
            } finally {
                isGeneralLoading.value = false
            }
        }
    }

    fun clearGeneralChat() {
        viewModelScope.launch {
            repository.clearChatHistory("general")
        }
    }

    // 3. VOICE AI CHAT
    // On Voice AI chat, we can additionally trigger TTS in MainActivity when a new model message arrives
    val lastSpeechTrigger = MutableSharedFlow<String>(replay = 0)

    fun speakText(text: String) {
        viewModelScope.launch {
            lastSpeechTrigger.emit(text)
        }
    }

    fun sendVoiceMessage() {
        val text = voiceInput.value.trim()
        if (text.isEmpty()) return
        voiceInput.value = ""

        viewModelScope.launch {
            isVoiceLoading.value = true
            repository.insertChatMessage("voice", "user", text)
            repository.addXp(5, questionAsked = true)

            val currentHistory = voiceMessages.value
            val apiHistory = currentHistory.map {
                Content(parts = listOf(Part(text = it.text)))
            } + Content(parts = listOf(Part(text = text)))

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are a voice AI tutor.
                            Rules:
                            Reply in a spoken style (perfect for reading aloud / text-to-speech).
                            Use simple words, short sentences, and a very friendly, enthusiastic teacher tone.
                            Same language as user input only.
                            Make it easy for listening. Avoid fancy markdown formats, bullets, lists, or headers if you can; write in clean, flowing text/paragraphs.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = apiHistory,
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.8f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Hello! I am ready to start learning. Speak to me!"
                repository.insertChatMessage("voice", "model", replyText)
                lastSpeechTrigger.emit(replyText)
            } catch (e: Exception) {
                repository.insertChatMessage("voice", "model", "Excuse me, I missed that because of an internet hiccup. Let's try again!")
            } finally {
                isVoiceLoading.value = false
            }
        }
    }

    fun clearVoiceChat() {
        viewModelScope.launch {
            repository.clearChatHistory("voice")
        }
    }

    // 4. INTERACTIVE QUIZ GENERATOR
    fun generateQuiz() {
        val topic = quizTopicInput.value.trim()
        if (topic.isEmpty()) return

        viewModelScope.launch {
            isQuizGenerating.value = true
            activeQuiz.value = null
            currentQuestionIndex.value = 0
            selectedAnswer.value = null
            isAnswerChecked.value = false
            quizScore.value = 0
            isQuizFinished.value = false
            gamificationMessage.value = null

            val promptText = "Create an school-level interactive quiz on the topic: '$topic'. Generates exactly 5 MCQ questions."

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are a quiz generator for school students.
                            Rules: Create exactly 5 MCQ questions.
                            Each question must have exactly 4 options (A, B, C, D) and a correct answer letter ("A", "B", "C", or "D").
                            Provide a child/student friendly brief explanation.
                            Match the user's language exactly.
                            You MUST return the output strictly as a JSON object of this structure. Do not include any other markdown outside of this JSON structure.
                            Format schema:
                            {
                              "topic": "Topic Name",
                              "questions": [
                                {
                                  "question": "Question text here?",
                                  "A": "Option A value",
                                  "B": "Option B value",
                                  "C": "Option C value",
                                  "D": "Option D value",
                                  "answer": "C",
                                  "explanation": "Friendly explanation why C is the right answer."
                                }
                              ]
                            }
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.6f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                
                // Parse the response
                val parsed = withContext(Dispatchers.Default) {
                    parseQuizResponse(rawResponse)
                }
                
                if (parsed != null && parsed.questions.isNotEmpty()) {
                    activeQuiz.value = parsed
                } else {
                    // Fallback stub quiz if model fails or formatting is bad
                    activeQuiz.value = generateFallbackQuiz(topic)
                }
            } catch (e: Exception) {
                activeQuiz.value = generateFallbackQuiz(topic)
            } finally {
                isQuizGenerating.value = false
            }
        }
    }

    private fun parseQuizResponse(raw: String): LocalGeneratedQuiz? {
        return try {
            // Find JSON start and end
            val startIdx = raw.indexOf("{")
            val endIdx = raw.lastIndexOf("}")
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonStr = raw.substring(startIdx, endIdx + 1)
                
                // Map from dynamic layout to strict Moshi parsing
                val mapAdapter = moshi.adapter(Map::class.java)
                val map = mapAdapter.fromJson(jsonStr) ?: return null
                
                val parsedTopic = map["topic"] as? String ?: "School Quiz"
                val questionsMapList = map["questions"] as? List<*> ?: return null
                
                val qs = questionsMapList.mapNotNull { item ->
                    val qMap = item as? Map<*, *> ?: return@mapNotNull null
                    val questionVal = qMap["question"] as? String ?: qMap["q"] as? String ?: "Question?"
                    val oA = qMap["A"] as? String ?: "Option A"
                    val oB = qMap["B"] as? String ?: "Option B"
                    val oC = qMap["C"] as? String ?: "Option C"
                    val oD = qMap["D"] as? String ?: "Option D"
                    val correctAns = qMap["answer"] as? String ?: qMap["ans"] as? String ?: "A"
                    val expl = qMap["explanation"] as? String ?: "The correct answer is $correctAns"
                    LocalQuizQuestion(
                        question = questionVal,
                        A = oA,
                        B = oB,
                        C = oC,
                        D = oD,
                        answer = correctAns.trim().uppercase(),
                        explanation = expl
                    )
                }
                LocalGeneratedQuiz(parsedTopic, qs)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateFallbackQuiz(topic: String): LocalGeneratedQuiz {
        return LocalGeneratedQuiz(
            topic = topic,
            questions = listOf(
                LocalQuizQuestion(
                    question = "What is the primary core of studying '$topic'?",
                    A = "To read definitions and memorize lists",
                    B = "To understand key concepts and apply them",
                    C = "To pass only exams",
                    D = "None of the above",
                    answer = "B",
                    explanation = "Learning is about core understanding and application!"
                ),
                LocalQuizQuestion(
                    question = "Why is consistent local review of '$topic' helpful?",
                    A = "It builds active brain pathways and strengthens long-term memory",
                    B = "It takes too much time",
                    C = "Only for teachers",
                    D = "None of the above",
                    answer = "A",
                    explanation = "Repeating actions builds myelin in active brain pathways!"
                ),
                LocalQuizQuestion(
                    question = "How can a student master difficult problems of '$topic'?",
                    A = "Skip them completely",
                    B = "Break them into small step-by-step blocks",
                    C = "Wait for answers directly",
                    D = "Never review them again",
                    answer = "B",
                    explanation = "Breaking complex topics down makes them easy to learn!"
                ),
                LocalQuizQuestion(
                    question = "Which active study technique is best for '$topic'?",
                    A = "Studying while sleeping",
                    B = "Explaining things to others and solving mock quizzes",
                    C = "Staring at the book without reading",
                    D = "None of the active options",
                    answer = "B",
                    explanation = "The Feynman technique (explaining things) enhances active recall."
                ),
                LocalQuizQuestion(
                    question = "What is the ultimate benefit of playing educational games?",
                    A = "Earning gamified rewards like XP and learning faster",
                    B = "Wasting study time",
                    C = "Just getting perfect grades",
                    D = "Sleeping better",
                    answer = "A",
                    explanation = "Rewarding your learning milestones forms positive habits!"
                )
            )
        )
    }

    fun selectQuizAnswer(opt: String) {
        if (!isAnswerChecked.value) {
            selectedAnswer.value = opt
        }
    }

    fun checkQuizAnswer() {
        if (isAnswerChecked.value) return
        val currentQ = activeQuiz.value?.questions?.getOrNull(currentQuestionIndex.value) ?: return
        val selected = selectedAnswer.value ?: return

        isAnswerChecked.value = true
        if (selected == currentQ.answer) {
            quizScore.value += 1
        }
    }

    fun nextQuizQuestion() {
        val quiz = activeQuiz.value ?: return
        val nextIndex = currentQuestionIndex.value + 1

        if (nextIndex < quiz.questions.size) {
            currentQuestionIndex.value = nextIndex
            selectedAnswer.value = null
            isAnswerChecked.value = false
        } else {
            // Finished!
            finishQuiz()
        }
    }

    private fun finishQuiz() {
        isQuizFinished.value = true
        val score = quizScore.value
        val xpEarned = score * 20 + 20 // 20 XP per correct answer, 20 XP bonus !

        viewModelScope.launch {
            repository.insertQuizResult(activeQuiz.value?.topic ?: "Quiz", score, xpEarned)
            generateGamificationMessage(activeQuiz.value?.topic ?: "Learning Quiz", score, xpEarned)
        }
    }

    private fun generateGamificationMessage(topic: String, score: Int, xpEarned: Int) {
        viewModelScope.launch {
            isGamificationLoading.value = true
            val prompt = """
                Generate a gamification award and encouragement message for completing a quiz/milestone:
                Topic: '$topic'
                Score: $score out of 5
                XP Earned: $xpEarned XP
                
                Follow these instructions exactly:
                You are a gamification assistant. Give an XP reward message. Encourage the student! Keep it short, thrilling, exciting, and appropriate for school students.
            """.trimIndent()

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.8f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Amazing job! You conquered the '$topic' quiz and claimed your $xpEarned XP reward! Keep striving!"
                gamificationMessage.value = replyText
            } catch (e: Exception) {
                gamificationMessage.value = "Hooray! You Completed the $topic Quiz! Score: $score/5. Captured +$xpEarned XP!"
            } finally {
                isGamificationLoading.value = false
            }
        }
    }

    fun restartQuiz() {
        activeQuiz.value = null
        quizTopicInput.value = ""
        currentQuestionIndex.value = 0
        selectedAnswer.value = null
        isAnswerChecked.value = false
        quizScore.value = 0
        isQuizFinished.value = false
        gamificationMessage.value = null
    }

    // 5. CAREER ROADMAP GENERATION
    fun generateCareerRoadmap() {
        val career = careerInput.value.trim()
        if (career.isEmpty()) return

        viewModelScope.launch {
            isRoadmapGenerating.value = true
            activeRoadmapContent.value = null
            activeRoadmapTitle.value = career

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are a helpful student career guidance AI.
                            Your goal is to give a complete career roadmap for school students.
                            Make sure to include these sections clearly:
                            1. Career Overview
                            2. Study Path
                            3. Skills Needed
                            4. Core Exams & Tests
                            5. Step-by-Step Action Plan (for school onwards)
                            6. Daily Practice Tips
                            7. Encouraging Motivation
                            
                            Language rule: Reply ONLY in the same language as input (do not mix languages).
                            Keep it very simple, engaging, structured, and friendly formatting.
                        """.trimIndent()
                    )
                )
            )

            val promptText = "Please create a detailed, simple career roadmap to become a: '$career'."

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Unable to draft this pathway right now. Let's try another career option!"
                activeRoadmapContent.value = replyText
                
                // Save it into historical career roadmaps
                repository.insertRoadmap(career, replyText)
            } catch (e: Exception) {
                val errorMsg = "Oops! I couldn't reach the career counselor. Please double check that you are online."
                activeRoadmapContent.value = errorMsg
            } finally {
                isRoadmapGenerating.value = false
                careerInput.value = ""
            }
        }
    }

    fun loadHistoricalRoadmap(item: CareerRoadmap) {
        activeRoadmapTitle.value = item.careerTitle
        activeRoadmapContent.value = item.content
    }

    fun deleteHistoricalRoadmap(item: CareerRoadmap) {
        viewModelScope.launch {
            repository.deleteRoadmap(item.id)
        }
    }

    fun closeActiveRoadmap() {
        activeRoadmapContent.value = null
        activeRoadmapTitle.value = ""
    }

    // 6. LESSONS AS STORIES
    fun generateLessonStory(topic: String) {
        val targetTopic = topic.trim()
        if (targetTopic.isEmpty()) return

        viewModelScope.launch {
            isStoryGenerating.value = true
            activeStoryContent.value = null
            
            val promptText = "Transform the school lesson topic '$targetTopic' into an exciting, narrative, highly educational story suitable for a student."

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are an expert children's educational storyteller named Sparky.
                            Take the user's school topic and convert it into a thrilling, immersive story with characters, a clear storyline, and interesting dialogue.
                            Student Profile: Name is ${studentName.value}, Class is ${studentClass.value}.
                            Language rule: You MUST tell the story entirely in the student's selected study language (${studentLanguage.value})! This is extremely important. If they select Tamil, tell the story in clean, simple Tamil.
                            Formatting: Use bold titles, story acts, and clear paragraphs.
                            Ensure the scientific or educational concepts taught are accurate but seamlessly woven into the plot!
                            End with a brief "What did we learn?" standard section summarizing the educational concepts.
                            Created by credit: Mention in a subtle footer or greeting that this story mode is created by Kalaiselvan.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.8f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Oops! I couldn't write the story. Let's try again."
                activeStoryContent.value = replyText
                repository.addXp(15) // award 15 XP for reading an educational lesson story!
            } catch (e: Exception) {
                activeStoryContent.value = "Sorry, Sparky's storytelling book got disconnected. Please verify you are online."
            } finally {
                isStoryGenerating.value = false
            }
        }
    }

    fun closeActiveStory() {
        activeStoryContent.value = null
        storyTopicInput.value = ""
    }

    // 7. EXAM IMPORTANT QUESTIONS
    fun generateExamQuestions(topic: String) {
        val targetTopic = topic.trim()
        if (targetTopic.isEmpty()) return

        viewModelScope.launch {
            isExamQuestionsGenerating.value = true
            examQuestionsContent.value = null

            val promptText = "Draft the most important exam questions with models answers for the subject or topic: '$targetTopic'."

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are an elite academic board paper examiner.
                            Generate the MOST IMPORTANT exam questions for a student in class ${studentClass.value}.
                            Topic/Subject: $targetTopic.
                            
                            Your output should have:
                            1. 3 Crucially Important Short Answer Questions (each with dynamic answers with step-by-step simple calculations/explanations).
                            2. 2 Detailed Descriptive Questions (with standard structures and detailed answers).
                            3. Simple study tips for this exam topic.
                            
                            Language rule: You MUST output in the student's selected study language (${studentLanguage.value})!
                            If the language is Tamil, provide precise questions and explanations in Tamil.
                            Always maintain a supportive and helpful student tone.
                            Created by credit: Mention explicitly in the header or footer that this Exam Prep guide is created by Kalaiselvan.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.6f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Failed to generate exam questions. Let's try again!"
                examQuestionsContent.value = replyText
                repository.addXp(15)
            } catch (e: Exception) {
                examQuestionsContent.value = "Unable to connect to the examination paper compiler. Please try again."
            } finally {
                isExamQuestionsGenerating.value = false
            }
        }
    }

    fun closeExamQuestions() {
        examQuestionsContent.value = null
        examTopicInput.value = ""
    }

    // 8. EDUCATIONAL NEWS & SCHOLARSHIP HUB
    fun fetchEduNews(category: String) {
        selectedNewsCategory.value = category
        viewModelScope.launch {
            isNewsLoading.value = true
            newsContent.value = null

            val promptText = when (category) {
                "school" -> "Provide 3 exciting educational or school news updates for today (simulated or real local news, e.g. school awards, science fairs, sports events)."
                "scholarship" -> "Search or compile 3 critical active scholarship updates for either Government school students or Private school students. Highlight the eligibility, rewards, and how to apply."
                "tnpsc" -> "Provide three crucial TNPSC (Tamil Nadu Public Service Commission) Exam updates, including scheduling, notifications dates, tips, and important syllabus guides."
                else -> "Provide educational news and scholarship updates."
            }

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are an educational news reporter and career guide named Sparky.
                            Provide clear, concise, highly structured, and up-to-date informational notices for students.
                            Category of Request: $category.
                            
                            Format Rules:
                            - Clean titles with emojis
                            - Quick summary points 
                            - Actionable steps or links (e.g. "To apply..." or "To read more...")
                            
                            Language rule: Provide the updates in English & Tamil together (or primarily in ${studentLanguage.value}) so it is accessible to state board students and local Tamil language seekers as well!
                            Created by credit: Emphasize that this news portal is crafted and created by Kalaiselvan.
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No news alerts recorded for today. Please check back soon!"
                newsContent.value = replyText
                repository.addXp(10) // reward 10 XP for staying updated with news!
            } catch (e: Exception) {
                newsContent.value = "Trouble connecting to the school today news servers. Check your internet connection."
            } finally {
                isNewsLoading.value = false
            }
        }
    }

    fun closeEduNews() {
        newsContent.value = null
    }

    // 9. STUDY PLANNER FUNCTIONS
    fun generateStudyPlan() {
        val subjects = plannerSubjectsInput.value.trim()
        val hours = plannerHoursInput.value.trim()
        val duration = plannerDurationInput.value.trim()

        if (subjects.isEmpty()) return

        viewModelScope.launch {
            isPlannerLoading.value = true
            plannerContent.value = null

            val promptText = """
                Generate a highly structured study plan timetable for me.
                Subjects/Topics to cover: $subjects
                Study hours available per day: $hours hours
                Plan duration: $duration
            """.trimIndent()

            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are an educational counselor and master scheduler.
                            Design a highly optimized, day-by-day study schedule based on the student's input.
                            For each day, allocate realistic subject slots matching the hours requested.
                            
                            Format guideline:
                            Format the output with numbered Day headers and precise study slots like:
                            ### [Day 1] (e.g. Day 1, Day 2, etc.)
                            - **[Day 1, Slot 1]**: (e.g. Math - Algebra basics, specific topics)
                            - **[Day 1, Slot 2]**: (e.g. Science - Chemical reaction balance)
                            Ensure you keep the slot labels strictly formatted with brackets like `[Day X, Slot Y]` so our app can dynamically match and build checkboxes for them!
                            
                            At the end, provide 3 simple pro productivity tips.
                            Language rule: Always respond in the student's preference language (${studentLanguage.value}).
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.5f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not build schedule. Please try again."

                plannerContent.value = resultText
                prefs.edit().putString("planner_content", resultText).apply()
                // Clear any old completed slots when a new plan is generated
                completedSlots.value = emptySet()
                prefs.edit().remove("completed_slots").apply()
                
                repository.addXp(15) // +15 XP for making a study schedule plan!
            } catch (e: Exception) {
                plannerContent.value = "Failed to build the schedule. Please check your network and try again."
            } finally {
                isPlannerLoading.value = false
            }
        }
    }

    fun togglePlannerSlot(slotKey: String) {
        val current = completedSlots.value.toMutableSet()
        val added = !current.contains(slotKey)
        if (added) {
            current.add(slotKey)
            viewModelScope.launch {
                repository.addXp(5) // Award +5 XP for completing a study block!
            }
        } else {
            current.remove(slotKey)
        }
        completedSlots.value = current
        prefs.edit().putStringSet("completed_slots", current).apply()
    }

    fun clearStudyPlan() {
        prefs.edit().remove("planner_content").remove("completed_slots").apply()
        plannerContent.value = null
        completedSlots.value = emptySet()
    }

    // 10. SCAN @ TYPING DOUBT SOLVER
    fun solveStudyDoubt() {
        val text = doubtInput.value.trim()
        val imageBase = doubtImageBase64.value
        val imageMime = doubtImageMimeType.value ?: "image/jpeg"

        if (text.isEmpty() && imageBase == null) return

        viewModelScope.launch {
            isDoubtLoading.value = true
            scannerStatusMessage.value = "Calibrating optical scan engine..."
            kotlinx.coroutines.delay(600)

            val promptText = if (imageBase != null) {
                scannerStatusMessage.value = "Analyzing graphic elements & writing patterns..."
                kotlinx.coroutines.delay(800)
                scannerStatusMessage.value = "Processing and feeding to problem solver..."
                "Answer this student homework scanned doubt step-by-step. Break it down using simple calculations, formulas, or logical points. Context text: $text"
            } else {
                scannerStatusMessage.value = "Searching Sparky learning index..."
                "Provide a step-by-step simple explanation for this study doubt: $text"
            }

            val partsList = mutableListOf<Part>()
            partsList.add(Part(text = promptText))

            if (imageBase != null) {
                partsList.add(Part(inlineData = InlineData(mimeType = imageMime, data = imageBase)))
            }

            val contentObj = Content(parts = partsList)
            val systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are Sparky's Intelligent Doubt Solver & Textbook Scanner. You break academic problems down into clear, numbered, bite-sized logical steps. Be friendly and highly readable.")
                )
            )

            try {
                scannerStatusMessage.value = "Synthesizing answer..."
                val request = GenerateContentRequest(
                    contents = listOf(contentObj),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.4f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not solve this doubt. Try making the text clearer or type the question directly."

                doubtSolution.value = result
                repository.addXp(10, questionAsked = true) // Solving custom doubts awards 10 XP!
            } catch (e: Exception) {
                doubtSolution.value = "Trouble connecting to Sparky's math/science solvers: ${e.message}. Please check your connection or spell out the math query manually."
            } finally {
                isDoubtLoading.value = false
                scannerStatusMessage.value = ""
            }
        }
    }

    fun clearDoubtSolver() {
        doubtInput.value = ""
        doubtImageBase64.value = null
        doubtImageMimeType.value = null
        doubtSolution.value = null
    }

    // 11. DYNAMIC REFRESHING GK TRIVIA GAMES
    fun loadGkTrivialQuestion(categoryName: String) {
        gkCategory.value = categoryName
        selectedGkAnswer.value = null
        isGkAnswerChecked.value = false
        gkAnswerFeedback.value = ""

        viewModelScope.launch {
            isGkGenerating.value = true
            val promptText = "Generate a single multiple-choice General Knowledge trivia question under the category of '$categoryName' suitable for a middle-and-high-school student."
            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                            You are the Sparky GK Game Master.
                            Generate exactly one high-quality, engaging trivia question.
                            Provide the details strictly prefixed with these uppercase brackets for parsing:
                            
                            [CATEGORY]: (The category name)
                            [QUESTION]: (A short question statement)
                            [A]: (Option A)
                            [B]: (Option B)
                            [C]: (Option C)
                            [D]: (Option D)
                            [ANSWER]: (Strictly A, B, C, or D)
                            [EXPLANATION]: (A fun, short, 1-2 sentence explanation of why)
                            
                            Do not write any extra conversation before or after. Ensure the [ANSWER] tag is strictly a single character (A, B, C, or D).
                        """.trimIndent()
                    )
                )
            )

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.85f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

                val lines = text.lines()
                var cat = categoryName
                var ques = ""
                var optA = ""
                var optB = ""
                var optC = ""
                var optD = ""
                var ans = "A"
                var expl = ""

                lines.forEach { line ->
                    val cleanLine = line.trim()
                    when {
                        cleanLine.startsWith("[CATEGORY]:", ignoreCase = true) -> cat = cleanLine.substringAfter("[CATEGORY]:").trim()
                        cleanLine.startsWith("[QUESTION]:", ignoreCase = true) -> ques = cleanLine.substringAfter("[QUESTION]:").trim()
                        cleanLine.startsWith("[A]:", ignoreCase = true) -> optA = cleanLine.substringAfter("[A]:").trim()
                        cleanLine.startsWith("[B]:", ignoreCase = true) -> optB = cleanLine.substringAfter("[B]:").trim()
                        cleanLine.startsWith("[C]:", ignoreCase = true) -> optC = cleanLine.substringAfter("[C]:").trim()
                        cleanLine.startsWith("[D]:", ignoreCase = true) -> optD = cleanLine.substringAfter("[D]:").trim()
                        cleanLine.startsWith("[ANSWER]:", ignoreCase = true) -> ans = cleanLine.substringAfter("[ANSWER]:").trim().uppercase().filter { it in 'A'..'D' }
                        cleanLine.startsWith("[EXPLANATION]:", ignoreCase = true) -> expl = cleanLine.substringAfter("[EXPLANATION]:").trim()
                    }
                }

                if (ques.isNotEmpty() && optA.isNotEmpty() && optB.isNotEmpty() && optC.isNotEmpty() && optD.isNotEmpty()) {
                    activeGkQuestion.value = GKQuestion(
                        category = cat,
                        question = ques,
                        optionA = optA,
                        optionB = optB,
                        optionC = optC,
                        optionD = optD,
                        correctAnswer = if (ans.isEmpty()) "A" else ans,
                        explanation = if (expl.isEmpty()) "That's correct!" else expl
                    )
                } else {
                    activeGkQuestion.value = GKQuestion(
                        category = categoryName,
                        question = "Which planet is known as the Red Planet?",
                        optionA = "Venus",
                        optionB = "Jupiter",
                        optionC = "Mars",
                        optionD = "Saturn",
                        correctAnswer = "C",
                        explanation = "Mars is called the Red Planet because iron minerals in its soil rust, giving it a distinct reddish color."
                    )
                }
            } catch (e: Exception) {
                activeGkQuestion.value = GKQuestion(
                    category = categoryName,
                    question = "What is the chemical symbol for Water?",
                    optionA = "O2",
                    optionB = "CO2",
                    optionC = "H2O",
                    optionD = "N2",
                    correctAnswer = "C",
                    explanation = "Water's chemical formula is H2O, meaning each molecule contains two hydrogen atoms bonded to one oxygen atom."
                )
            } finally {
                isGkGenerating.value = false
            }
        }
    }

    fun submitGkAnswer(userChoice: String) {
        val q = activeGkQuestion.value ?: return
        selectedGkAnswer.value = userChoice
        isGkAnswerChecked.value = true

        val isCorr = userChoice.equals(q.correctAnswer, ignoreCase = true)
        val tot = gkTotalCount.value + 1
        val corr = if (isCorr) gkCorrectCount.value + 1 else gkCorrectCount.value

        gkTotalCount.value = tot
        gkCorrectCount.value = corr
        prefs.edit().putInt("gk_total_count", tot).putInt("gk_correct_count", corr).apply()

        if (isCorr) {
            viewModelScope.launch {
                repository.addXp(10) // reward 10 XP for correct GK trivia answers
            }
            gkAnswerFeedback.value = "🎉 CORRECT! Stellar Job! +10 XP has been credited to your Sparky profile!\n\n${q.explanation}"
        } else {
            gkAnswerFeedback.value = "❌ OOPS! The correct answer was (${q.correctAnswer}).\n\n${q.explanation}"
        }
    }

    fun askAnythingQuick(query: String) {
        if (query.trim().isEmpty()) return
        viewModelScope.launch {
            isAskAnythingLoading.value = true
            askAnythingOutput.value = null
            try {
                val prompt = "Give a simple, student friendly explanation with an example of: ${query.trim()}"
                val systemInstruction = Content(
                    parts = listOf(
                        Part(text = "You are Sparky's floating micro explorer assistant. Keep answers to 3 easy bullets, with real-world analogies.")
                    )
                )
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    systemInstruction = systemInstruction,
                    generationConfig = GenerationConfig(temperature = 0.7f)
                )
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val answer = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not gather answer. Try typing again!"
                askAnythingOutput.value = answer
                repository.addXp(5)
            } catch (e: Exception) {
                askAnythingOutput.value = "Trouble connecting to Sparky search index. Check network."
            } finally {
                isAskAnythingLoading.value = false
            }
        }
    }

    fun clearAskAnything() {
        askAnythingOutput.value = null
    }
}
