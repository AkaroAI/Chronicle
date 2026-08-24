package com.akaroai.chronicle.ui

import org.junit.Assert.*
import org.junit.Test

class ChatRoutingTest {
    @Test fun dmConversationIsRecognized() {
        assertTrue(ChatRouting.isDmConversation("[DM Conversation]\nHi"))
        assertFalse(ChatRouting.isDmConversation("[Story | Actor: Yuki]\nHi"))
    }

    @Test fun routeMetadataIsHiddenFromVisibleText() {
        assertEquals("Yuki waves.", ChatRouting.visibleContent("[Story | Actor: Yuki]\nYuki waves."))
    }

    @Test fun pairedInteractionFindsBothCharacters() {
        assertEquals(
            PairedInteraction("Yuki", "Asira", "hold hands"),
            ChatRouting.parsePairedInteraction("[Story]\nyuki and asira hold hands")
        )
    }

    @Test fun unrelatedSentenceDoesNotCreatePairedInteraction() {
        assertNull(ChatRouting.parsePairedInteraction("Yuki asks where Asira went."))
    }
}
