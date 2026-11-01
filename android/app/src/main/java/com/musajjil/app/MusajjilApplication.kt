package com.musajjil.app

import android.app.Application
import androidx.room.Room
import com.musajjil.app.data.RegisterRepository
import com.musajjil.app.data.local.RegisterDatabase

class MusajjilApplication : Application() {
    val repository by lazy {
        val database = Room.databaseBuilder(applicationContext, RegisterDatabase::class.java, "musajjil.db").build()
        RegisterRepository(database)
    }
}
