package com.example

import com.example.tdlib.*
import com.example.ui.i18n.EnStrings
import com.example.ui.i18n.RuStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.screens.sampleCountries
import org.junit.Assert.*
import org.junit.Test

class TelegramLogicTest {

    @Test
    fun testCountryCodesList() {
        assertNotNull(sampleCountries)
        assertTrue("Expected full world countries list, got ${sampleCountries.size}", sampleCountries.size >= 230)
        val us = sampleCountries.find { it.code == "US" }
        val ru = sampleCountries.find { it.code == "RU" }
        val ua = sampleCountries.find { it.code == "UA" }
        val anon = sampleCountries.find { it.code == "FT" }
        assertNotNull(us)
        assertNotNull(ru)
        assertNotNull(ua)
        assertNotNull(anon)
        assertEquals("+1", us?.dialCode)
        assertEquals("+7", ru?.dialCode)
        assertEquals("+380", ua?.dialCode)
        assertEquals("+888", anon?.dialCode)
        assertEquals("RU", WorldCountries.matchByDialCode("7")?.code)
    }

    @Test
    fun testChatModelAndSecretProperties() {
        val chat = TelegramChatModel(
            id = 101L,
            title = "Rust Developers",
            type = "secret",
            lastMessageText = "Encrypted!",
            lastMessageTime = "12:00",
            unreadCount = 5,
            isPinned = true,
            isMuted = false,
            isArchived = true,
            isSecret = true,
            selfDestructSec = 15,
            username = "@rust_dev",
            memberCount = 1200,
            category = ChatCategory.SECRET
        )

        assertEquals("Rust Developers", chat.title)
        assertEquals(5, chat.unreadCount)
        assertTrue(chat.isPinned)
        assertTrue(chat.isArchived)
        assertTrue(chat.isSecret)
        assertEquals(15, chat.selfDestructSec)
        assertEquals(ChatCategory.SECRET, chat.category)
    }

    @Test
    fun testPollDataAndTotals() {
        val poll = PollData(
            question = "Best UI?",
            options = listOf(
                PollOption("Liquid Glass", 12),
                PollOption("Minimal", 8)
            ),
            isQuiz = true,
            correctOptionIndex = 0,
            selectedIndices = setOf(0)
        )
        assertEquals(20, poll.totalVotes)
        assertTrue(poll.isQuiz)
        assertEquals(0, poll.correctOptionIndex)
        assertEquals(MediaType.POLL, MediaType.valueOf("POLL"))
    }

    @Test
    fun testLocalizationAndThemeEnums() {
        assertEquals("Чаты", stringsForLanguage(AppLanguage.RU).tabChats)
        assertEquals("Chats", stringsForLanguage(AppLanguage.EN).tabChats)
        assertEquals(RuStrings.langCode, "ru")
        assertEquals(EnStrings.langCode, "en")
        assertEquals(4, TelegramThemePreset.values().size)
        assertEquals(5, WallpaperStyle.values().size)
        assertEquals(7, ChatCategory.values().size)
    }

    @Test
    fun testStoriesAccountsAndEntities() {
        val story = StoryItem(
            id = 1L,
            authorName = "Pavel",
            chatId = 101L,
            avatarColorIndex = 0,
            slides = listOf(StorySlide(10L, "Hello", 0xFF007AFF, 0xFF34AADC, "now"))
        )
        val account = AccountProfile(1, "Pavel", "@rust", "+79990001122", "Bio", 0, true)
        val span = TextEntitySpan(offset = 0, length = 4, type = "Bold")
        val msg = TelegramMessageModel(
            id = 1L,
            chatId = 101L,
            senderName = "You",
            isOutgoing = true,
            text = "RustGram",
            timestamp = "12:05",
            isRead = true,
            isSilent = true,
            mediaType = MediaType.VIDEO_NOTE,
            entities = listOf(span)
        )

        assertEquals(1, story.slides.size)
        assertTrue(account.isActive)
        assertTrue(msg.isSilent)
        assertEquals(MediaType.VIDEO_NOTE, msg.mediaType)
        assertEquals("Bold", msg.entities.first().type)
        assertEquals(4, MainTab.values().size)
    }
}
