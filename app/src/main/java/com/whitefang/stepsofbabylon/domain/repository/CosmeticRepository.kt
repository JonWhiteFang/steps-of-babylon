package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import kotlinx.coroutines.flow.Flow

interface CosmeticRepository {
    fun observeAll(): Flow<List<CosmeticItem>>

    fun observeOwned(): Flow<List<CosmeticItem>>

    fun observeEquipped(): Flow<List<CosmeticItem>>

    suspend fun purchase(cosmeticId: String)

    suspend fun equip(cosmeticId: String)

    suspend fun unequip(cosmeticId: String)

    suspend fun ensureSeedData()

    /**
     * Returns `true` iff a cosmetic with the given id is present in the seeded catalogue.
     *
     * Introduced in C.4 so [com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestone]
     * can pre-flight check [com.whitefang.stepsofbabylon.domain.model.MilestoneReward.Cosmetic]
     * reward ids before running the atomic credit transaction. Previously the use case
     * silently dropped unknown ids; this surfaces them as
     * [com.whitefang.stepsofbabylon.domain.usecase.ClaimMilestoneResult.UnknownCosmetic]
     * so the 3 currently-mismatched milestone cosmetic ids (`garden_ziggurat_skin`,
     * `lapis_lazuli_skin`, `sandals_of_gilgamesh`) stop dropping silently.
     *
     * Implementations MUST seed the catalogue before returning (call [ensureSeedData]
     * internally) so the method is reliable even on cold boots where the Store hasn't
     * been visited yet. Resolution of the 3 mismatched ids is content work deferred to
     * C.2 PR 3+ \u2014 C.4 covers detection only.
     */
    suspend fun idExists(cosmeticId: String): Boolean
}
