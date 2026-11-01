package com.musajjil.app

import android.app.Application
import androidx.room.Room
import com.musajjil.app.data.RegisterRepository
import com.musajjil.app.data.local.RegisterDatabase
import com.musajjil.app.data.local.MIGRATION_1_2
import com.musajjil.app.data.local.REGISTER_CALLBACK
import com.musajjil.app.backup.BackupManager

class MusajjilApplication : Application() {
    val repository by lazy {
        val database = Room.databaseBuilder(applicationContext, RegisterDatabase::class.java, "musajjil.db")
            .addMigrations(MIGRATION_1_2).addCallback(REGISTER_CALLBACK).build()
        RegisterRepository(database)
    }
    val backups by lazy { BackupManager(this, repository) }
}
