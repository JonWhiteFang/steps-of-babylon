package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.DailyStepRecordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [StepRepositoryImpl] — entity→domain mapping for daily step records,
 * upsert composition (read-existing + copy + write), and escrow lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepRepositoryImplTest {

    @Test
    fun `observeTodayRecord maps entity to domain when present`() = runTest {
        val dao = mock<DailyStepDao>()
        val entity = DailyStepRecordEntity(
            date = "2026-05-28",
            sensorSteps = 5000,
            healthConnectSteps = 4800,
            creditedSteps = 5000,
        )
        whenever(dao.getByDate("2026-05-28")).thenReturn(MutableStateFlow(entity))
        val repo = StepRepositoryImpl(dao)

        val summary = repo.observeTodayRecord("2026-05-28").first()

        assertNotNull(summary)
        assertEquals("2026-05-28", summary!!.date)
        assertEquals(5000L, summary.sensorSteps)
        assertEquals(4800L, summary.healthConnectSteps)
        assertEquals(5000L, summary.creditedSteps)
    }

    @Test
    fun `observeTodayRecord emits null when no entity exists`() = runTest {
        val dao = mock<DailyStepDao>()
        whenever(dao.getByDate("2026-05-28")).thenReturn(MutableStateFlow(null))
        val repo = StepRepositoryImpl(dao)

        assertNull(repo.observeTodayRecord("2026-05-28").first())
    }

    @Test
    fun `getDailyRecord returns mapped domain or null`() = runTest {
        val dao = mock<DailyStepDao>()
        whenever(dao.getByDateOnce("2026-05-28")).thenReturn(
            DailyStepRecordEntity(date = "2026-05-28", sensorSteps = 1000)
        )
        whenever(dao.getByDateOnce("2026-05-29")).thenReturn(null)
        val repo = StepRepositoryImpl(dao)

        assertNotNull(repo.getDailyRecord("2026-05-28"))
        assertEquals(1000L, repo.getDailyRecord("2026-05-28")!!.sensorSteps)
        assertNull(repo.getDailyRecord("2026-05-29"))
    }

    @Test
    fun `updateDailySteps creates new record when none exists`() = runTest {
        val dao = mock<DailyStepDao>()
        whenever(dao.getByDateOnce("2026-05-28")).thenReturn(null)
        val repo = StepRepositoryImpl(dao)

        repo.updateDailySteps("2026-05-28", sensorSteps = 100, creditedSteps = 100)

        val captor = argumentCaptor<DailyStepRecordEntity>()
        verify(dao).upsert(captor.capture())
        assertEquals("2026-05-28", captor.firstValue.date)
        assertEquals(100L, captor.firstValue.sensorSteps)
        assertEquals(100L, captor.firstValue.creditedSteps)
    }

    @Test
    fun `updateDailySteps preserves other fields when updating existing record`() = runTest {
        val dao = mock<DailyStepDao>()
        val existing = DailyStepRecordEntity(
            date = "2026-05-28",
            sensorSteps = 100,
            creditedSteps = 100,
            healthConnectSteps = 95,
            activityMinutes = mapOf("WALKING" to 10),
        )
        whenever(dao.getByDateOnce("2026-05-28")).thenReturn(existing)
        val repo = StepRepositoryImpl(dao)

        repo.updateDailySteps("2026-05-28", sensorSteps = 200, creditedSteps = 195)

        val captor = argumentCaptor<DailyStepRecordEntity>()
        verify(dao).upsert(captor.capture())
        // sensorSteps + creditedSteps updated; healthConnectSteps + activityMinutes preserved
        assertEquals(200L, captor.firstValue.sensorSteps)
        assertEquals(195L, captor.firstValue.creditedSteps)
        assertEquals(95L, captor.firstValue.healthConnectSteps)
        assertEquals(mapOf("WALKING" to 10), captor.firstValue.activityMinutes)
    }

    @Test
    fun `updateEscrow merges escrow fields onto existing record`() = runTest {
        val dao = mock<DailyStepDao>()
        val existing = DailyStepRecordEntity(date = "2026-05-28", sensorSteps = 500)
        whenever(dao.getByDateOnce("2026-05-28")).thenReturn(existing)
        val repo = StepRepositoryImpl(dao)

        repo.updateEscrow("2026-05-28", escrowSteps = 250, syncCount = 2)

        val captor = argumentCaptor<DailyStepRecordEntity>()
        verify(dao).upsert(captor.capture())
        assertEquals(250L, captor.firstValue.escrowSteps)
        assertEquals(2, captor.firstValue.escrowSyncCount)
        assertEquals(500L, captor.firstValue.sensorSteps) // preserved
    }

    @Test
    fun `releaseEscrow delegates to clearEscrow`() = runTest {
        val dao = mock<DailyStepDao>()
        val repo = StepRepositoryImpl(dao)

        repo.releaseEscrow("2026-05-28")

        verify(dao).clearEscrow("2026-05-28")
    }

    @Test
    fun `discardEscrow delegates to clearEscrow`() = runTest {
        val dao = mock<DailyStepDao>()
        val repo = StepRepositoryImpl(dao)

        repo.discardEscrow("2026-05-28")

        verify(dao).clearEscrow("2026-05-28")
    }
}
