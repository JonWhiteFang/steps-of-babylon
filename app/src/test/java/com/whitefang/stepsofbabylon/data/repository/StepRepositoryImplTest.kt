package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.DailyStepDao
import com.whitefang.stepsofbabylon.data.local.DailyStepRecordEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [StepRepositoryImpl] — entity→domain mapping for daily step records,
 * delegation to the column-targeted DAO upserts (#121), and escrow lifecycle.
 *
 * #121: the per-field updates no longer read-copy-upsert the whole row; each delegates to a
 * column-targeted DAO method (`setSensorAndCreditedSteps` / `setHealthConnectSteps` /
 * `setActivityMinutes` / `setEscrow`) so concurrent writers touch disjoint columns and can't
 * clobber each other. These tests verify the delegation contract; the actual
 * column-preservation behaviour against real SQLite is proven in `DailyStepDaoTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StepRepositoryImplTest {
    @Test
    fun `observeTodayRecord maps entity to domain when present`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val entity =
                DailyStepRecordEntity(
                    date = "2026-05-28",
                    sensorSteps = 5000,
                    healthConnectSteps = 4800,
                    creditedSteps = 5000,
                )
            whenever(dao.getByDate("2026-05-28")).thenReturn(MutableStateFlow(entity))
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            val summary = repo.observeTodayRecord("2026-05-28").first()

            assertNotNull(summary)
            assertEquals("2026-05-28", summary!!.date)
            assertEquals(5000L, summary.sensorSteps)
            assertEquals(4800L, summary.healthConnectSteps)
            assertEquals(5000L, summary.creditedSteps)
        }

    @Test
    fun `observeTodayRecord emits null when no entity exists`() =
        runTest {
            val dao = mock<DailyStepDao>()
            whenever(dao.getByDate("2026-05-28")).thenReturn(MutableStateFlow(null))
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            assertNull(repo.observeTodayRecord("2026-05-28").first())
        }

    @Test
    fun `getDailyRecord returns mapped domain or null`() =
        runTest {
            val dao = mock<DailyStepDao>()
            whenever(dao.getByDateOnce("2026-05-28")).thenReturn(
                DailyStepRecordEntity(date = "2026-05-28", sensorSteps = 1000),
            )
            whenever(dao.getByDateOnce("2026-05-29")).thenReturn(null)
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            assertNotNull(repo.getDailyRecord("2026-05-28"))
            assertEquals(1000L, repo.getDailyRecord("2026-05-28")!!.sensorSteps)
            assertNull(repo.getDailyRecord("2026-05-29"))
        }

    @Test
    fun `updateDailySteps delegates to the column-targeted setSensorAndCreditedSteps`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.updateDailySteps("2026-05-28", sensorSteps = 200, creditedSteps = 195)

            // #121: no read-copy-upsert — the repo calls the column-targeted DAO query directly,
            // so it touches only sensorSteps/creditedSteps and never the whole row.
            verify(dao).setSensorAndCreditedSteps("2026-05-28", 200, 195)
            verify(dao, never()).upsert(any())
        }

    @Test
    fun `updateHealthConnectSteps delegates to the column-targeted setHealthConnectSteps`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.updateHealthConnectSteps("2026-05-28", healthConnectSteps = 4800)

            verify(dao).setHealthConnectSteps("2026-05-28", 4800)
            verify(dao, never()).upsert(any())
        }

    @Test
    fun `updateActivityMinutes delegates to the column-targeted setActivityMinutes`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.updateActivityMinutes("2026-05-28", activityMinutes = mapOf("WALKING" to 10), stepEquivalents = 1000)

            verify(dao).setActivityMinutes("2026-05-28", mapOf("WALKING" to 10), 1000)
            verify(dao, never()).upsert(any())
        }

    @Test
    fun `updateEscrow delegates to the column-targeted setEscrow`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.updateEscrow("2026-05-28", escrowSteps = 250, syncCount = 2)

            verify(dao).setEscrow("2026-05-28", 250, 2)
            verify(dao, never()).upsert(any())
        }

    @Test
    fun `releaseEscrow delegates to clearEscrow`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.releaseEscrow("2026-05-28")

            verify(dao).clearEscrow("2026-05-28")
        }

    @Test
    fun `discardEscrow delegates to clearEscrow`() =
        runTest {
            val dao = mock<DailyStepDao>()
            val repo = StepRepositoryImpl(dao, mock<PlayerProfileDao>())

            repo.discardEscrow("2026-05-28")

            verify(dao).clearEscrow("2026-05-28")
        }
}
