package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.repository.TelegramRepository
import com.example.rustgram.NativeBridge

class RustGramApp : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_MESSAGES = "telegram_messages"
        const val NOTIFICATION_CHANNEL_SYNC = "telegram_sync"
        lateinit var instance: RustGramApp
            private set
    }

    lateinit var repository: TelegramRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i("RustGramApp", "Initializing RustGram for Android 16...")
        createNotificationChannels()

        // Init repository
        repository = TelegramRepository(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MESSAGES,
                "Telegram Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming Telegram messages"
                enableVibration(true)
            }

            val syncChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_SYNC,
                "Telegram Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Telegram MTProto connection alive"
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }
}
