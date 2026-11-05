package com.mechy007.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DomainSmokeTest {
    @Test
    fun sessionIdsAreUnique() {
        val first = SessionId.new()
        val second = SessionId.new()

        assertNotEquals(first, second)
    }

    @Test
    fun newSessionHasExpectedDefaults() {
        val session = NexusSession()

        assertNotNull(session.id.value)
        assertEquals("New conversation", session.title)
        assertEquals(emptyList(), session.messages)
    }
}
