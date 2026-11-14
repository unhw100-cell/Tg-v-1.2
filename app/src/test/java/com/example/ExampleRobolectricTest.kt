package com.example

import com.example.tdlib.*
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
    fun testChatModelProperties() {
        val chat = TelegramChatModel(
            id = 101L,
            title = "Rust Developers",
            type = "supergroup",
            lastMessageText = "Hello from Rust!",
            lastMessageTime = "12:00",
            unreadCount = 5,
            isPinned = true,
            isMuted = false,
            username = "@rust_dev",
            memberCount = 1200,
            category = ChatCategory.GROUPS
        )

        assertEquals("Rust Developers", chat.title)
        assertEquals(5, chat.unreadCount)
        assertTrue(chat.isPinned)
        assertFalse(chat.isMuted)
        assertEquals("@rust_dev", chat.username)
        assertEquals(1200, chat.memberCount)
        assertEquals(ChatCategory.GROUPS, chat.category)
    }

    @Test
    fun testMessageModelAndEntities() {
        val span = TextEntitySpan(offset = 0, length = 4, type = "Bold")
        val msg = TelegramMessageModel(
            id = 1L,
            chatId = 101L,
            senderName = "You",
            isOutgoing = true,
            text = "RustGram",
            timestamp = "12:05",
            isRead = true,
            mediaType = MediaType.VIDEO_NOTE,
            entities = listOf(span)
        )

        assertTrue(msg.isOutgoing)
        assertEquals("You", msg.senderName)
        assertEquals("RustGram", msg.text)
        assertEquals(MediaType.VIDEO_NOTE, msg.mediaType)
        assertEquals("Bold", msg.entities.first().type)
    }

    @Test
    fun testSearchHitAndContacts() {
        val contact = ContactModel(
            id = 201L,
            name = "Alexander Ivanov",
            username = "@alex_rust",
            phone = "+44 20 7946 0912",
            bio = "Systems Engineer",
            isOnline = true,
            lastSeen = "online"
        )
        val chat = TelegramChatModel(
            id = contact.id,
            title = contact.name,
            type = "private",
            lastMessageText = "Hi",
            lastMessageTime = "12:10"
        )
        val hit = SearchHitItem(chat = chat)
        assertEquals("Alexander Ivanov", hit.chat.title)
        assertNull(hit.matchedMessage)
        assertEquals(4, MainTab.values().size)
    }
}
