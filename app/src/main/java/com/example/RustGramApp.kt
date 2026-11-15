package com.example

import android.app.Application
import android.util.Log
import com.example.repository.TelegramRepository

class RustGramApp : Application() {
    companion object {
        lateinit var instance: RustGramApp
            private set
    }

    lateinit var repository: TelegramRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i("RustGramApp", "Initializing RustGram for Android 16...")
        // Init repository
        repository = TelegramRepository(this)
    }
}
