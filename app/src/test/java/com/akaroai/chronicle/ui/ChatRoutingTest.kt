package com.akaroai.chronicle.ui

import org.junit.Assert.*
import org.junit.Test

class ChatRoutingTest {
    @Test
    fun classifiesMessagePresentationWithoutChangingStoredContent() {
        assertEquals(MessagePresentation.PLAYER_ACTION, ChatRouting.presentation("[Story | Actor: Yuki | Intent: Action | Target: Scene]\nRuns.", "user"))
        assertEquals(MessagePresentation.DIALOGUE, ChatRouting.presentation("[Story | Actor: Yuki | Intent: Talking to | Target: Asira]\nHello.", "user"))
        assertEquals(MessagePresentation.NARRATION, ChatRouting.presentation("The moon rises.", "assistant"))
        assertEquals(MessagePresentation.DM, ChatRouting.presentation("[DM Conversation]\nLet's discuss canon.", "assistant"))
    }

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

    @Test fun soloDepartureDoesNotIncludeCharacterWhoStayedBehind() {
        assertEquals(
            "yuki",
            ChatRouting.explicitSoloDepartureSubject("yuki leaves asira to go to moonfall lake by herself")
        )
    }

    @Test fun ordinaryGroupMovementHasNoSoloOverride() {
        assertNull(ChatRouting.explicitSoloDepartureSubject("yuki and asira go to moonfall lake"))
    }
}
