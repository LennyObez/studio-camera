package com.studiocamera.core.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockModeManagerTest {

    private val manager = MockModeManager()

    @Test
    fun initialState_isFalse() {
        assertFalse(manager.isMockActive.value)
    }

    @Test
    fun setMockActive_true_updatesState() {
        manager.setMockActive(true)
        assertTrue(manager.isMockActive.value)
    }

    @Test
    fun setMockActive_false_updatesState() {
        manager.setMockActive(true)
        assertTrue(manager.isMockActive.value)

        manager.setMockActive(false)
        assertFalse(manager.isMockActive.value)
    }

    @Test
    fun toggle_multipleTimesIsConsistent() {
        assertFalse(manager.isMockActive.value)

        manager.setMockActive(true)
        assertTrue(manager.isMockActive.value)

        manager.setMockActive(true)
        assertTrue(manager.isMockActive.value)

        manager.setMockActive(false)
        assertFalse(manager.isMockActive.value)

        manager.setMockActive(false)
        assertFalse(manager.isMockActive.value)
    }
}
