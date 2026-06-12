package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.TutorRepository

class TutorApplication : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var repository: TutorRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "tutor_database"
        ).fallbackToDestructiveMigration().build()
        repository = TutorRepository(database.studentDao())
    }
}
