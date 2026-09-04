package com.example.characterization

import com.example.core.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobMultiSelectionActionCharacterizationTest {

    enum class SafeAction {
        COMPLETE,
        CLOSE,
        ARCHIVE,
        DELETE
    }

    private fun computeAvailableActions(statuses: Set<JobStatus>): Set<SafeAction> {
        if (statuses.isEmpty()) return emptySet()

        // Per spec §14:
        // All ACTIVE -> COMPLETE, ARCHIVE, DELETE
        // All COMPLETED -> CLOSE, ARCHIVE, DELETE
        // All CLOSED -> ARCHIVE, DELETE
        // Mixed -> only shared safe actions: ARCHIVE, DELETE
        val allActive = statuses.size == 1 && statuses.contains(JobStatus.ACTIVE)
        val allCompleted = statuses.size == 1 && statuses.contains(JobStatus.COMPLETED)

        return buildSet {
            if (allActive) add(SafeAction.COMPLETE)
            if (allCompleted) add(SafeAction.CLOSE)
            add(SafeAction.ARCHIVE)
            add(SafeAction.DELETE)
        }
    }

    @Test
    fun testAllActiveSelection_allowsCompleteArchiveDelete() {
        val actions = computeAvailableActions(setOf(JobStatus.ACTIVE))
        assertTrue(actions.contains(SafeAction.COMPLETE))
        assertFalse(actions.contains(SafeAction.CLOSE))
        assertTrue(actions.contains(SafeAction.ARCHIVE))
        assertTrue(actions.contains(SafeAction.DELETE))
    }

    @Test
    fun testAllCompletedSelection_allowsCloseArchiveDelete() {
        val actions = computeAvailableActions(setOf(JobStatus.COMPLETED))
        assertFalse(actions.contains(SafeAction.COMPLETE))
        assertTrue(actions.contains(SafeAction.CLOSE))
        assertTrue(actions.contains(SafeAction.ARCHIVE))
        assertTrue(actions.contains(SafeAction.DELETE))
    }

    @Test
    fun testAllClosedSelection_allowsArchiveDeleteOnly() {
        val actions = computeAvailableActions(setOf(JobStatus.CLOSED))
        assertFalse(actions.contains(SafeAction.COMPLETE))
        assertFalse(actions.contains(SafeAction.CLOSE))
        assertTrue(actions.contains(SafeAction.ARCHIVE))
        assertTrue(actions.contains(SafeAction.DELETE))
    }

    @Test
    fun testMixedStatusSelection_allowsOnlySafeIntersection() {
        val actions = computeAvailableActions(setOf(JobStatus.ACTIVE, JobStatus.COMPLETED))
        assertFalse(actions.contains(SafeAction.COMPLETE))
        assertFalse(actions.contains(SafeAction.CLOSE))
        assertEquals(setOf(SafeAction.ARCHIVE, SafeAction.DELETE), actions)
    }
}