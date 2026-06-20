package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.MilestoneEntity
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mockito.kotlin.mock

/**
 * In-memory fake for [MilestoneRepository] (#227). Delegates to a wrapped [FakeMilestoneDao] so the
 * atomic-claim emulation + `linkedPlayer` credit routing + [claimMilestoneAtomicCallCount] accessor
 * are reused verbatim (the test surface the rewired use-case tests assert on). The decoy
 * `playerDao` the DAO ignores is supplied internally.
 */
class FakeMilestoneRepository(
    linkedPlayer: FakePlayerRepository? = null,
    val dao: FakeMilestoneDao = FakeMilestoneDao(linkedPlayer),
) : MilestoneRepository {

    /** Exposes the wrapped DAO's atomic-path counter for the credit-exactly-once assertions. */
    val claimMilestoneAtomicCallCount: Int get() = dao.claimMilestoneAtomicCallCount

    /** Seed a claimed/unclaimed milestone row (mirrors the former direct-DAO test seeding). */
    suspend fun upsert(entity: MilestoneEntity) = dao.upsert(entity)

    override suspend fun getClaimedMilestoneIds(): List<String> =
        dao.getAllOnce().filter { it.claimed }.map { it.milestoneId }

    override fun observeClaimedMilestoneIds(): Flow<Set<String>> =
        dao.getAll().map { list -> list.filter { it.claimed }.map { it.milestoneId }.toSet() }

    override suspend fun claimMilestoneAtomic(
        milestoneId: String,
        gems: Long,
        powerStones: Long,
        claimedAt: Long,
    ): Boolean = dao.claimMilestoneAtomic(milestoneId, gems, powerStones, claimedAt, playerDao = decoyPlayerDao)

    // The wrapped FakeMilestoneDao ignores this and routes credits via linkedPlayer (it exists only
    // to satisfy the DAO @Transaction signature — same decoy the pre-#227 tests passed explicitly).
    private val decoyPlayerDao: PlayerProfileDao = mock()
}
