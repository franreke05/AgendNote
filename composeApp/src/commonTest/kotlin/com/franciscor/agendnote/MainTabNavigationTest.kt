package com.franciscor.agendnote

import com.franciscor.agendnote.app.navigation.MainTab
import com.franciscor.agendnote.app.navigation.SwipeDirection
import com.franciscor.agendnote.app.navigation.next
import com.franciscor.agendnote.app.navigation.previous
import com.franciscor.agendnote.app.navigation.tabSlideDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainTabNavigationTest {
    @Test
    fun `next steps through the bottom bar order`() {
        assertEquals(MainTab.DAY, MainTab.AGENDA.next())
        assertEquals(MainTab.LABELS, MainTab.DAY.next())
        assertEquals(MainTab.SETTINGS, MainTab.LABELS.next())
    }

    @Test
    fun `next returns null past the last tab`() {
        assertNull(MainTab.SETTINGS.next())
    }

    @Test
    fun `previous steps backwards through the bottom bar order`() {
        assertEquals(MainTab.LABELS, MainTab.SETTINGS.previous())
        assertEquals(MainTab.DAY, MainTab.LABELS.previous())
        assertEquals(MainTab.AGENDA, MainTab.DAY.previous())
    }

    @Test
    fun `previous returns null before the first tab`() {
        assertNull(MainTab.AGENDA.previous())
    }

    @Test
    fun `tabSlideDirection is NEXT when moving to a later tab`() {
        assertEquals(
            SwipeDirection.NEXT,
            tabSlideDirection(fromRoute = "agenda", toRoute = "labels"),
        )
    }

    @Test
    fun `tabSlideDirection is PREVIOUS when moving to an earlier tab`() {
        assertEquals(
            SwipeDirection.PREVIOUS,
            tabSlideDirection(fromRoute = "settings", toRoute = "day"),
        )
    }

    @Test
    fun `tabSlideDirection is null for the same tab`() {
        assertNull(tabSlideDirection(fromRoute = "agenda", toRoute = "agenda"))
    }

    @Test
    fun `tabSlideDirection is null when a route is unknown or missing`() {
        assertNull(tabSlideDirection(fromRoute = null, toRoute = "agenda"))
        assertNull(tabSlideDirection(fromRoute = "agenda", toRoute = "not-a-tab"))
    }
}
