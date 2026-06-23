package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.CosmeticCategory
import com.whitefang.stepsofbabylon.domain.model.CosmeticItem
import com.whitefang.stepsofbabylon.domain.repository.CosmeticRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeCosmeticRepository : CosmeticRepository {
    val items = MutableStateFlow<List<CosmeticItem>>(emptyList())

    override fun observeAll(): Flow<List<CosmeticItem>> = items

    override fun observeOwned(): Flow<List<CosmeticItem>> = items.map { it.filter { c -> c.isOwned } }

    override fun observeEquipped(): Flow<List<CosmeticItem>> = items.map { it.filter { c -> c.isEquipped } }

    override suspend fun purchase(cosmeticId: String) {
        items.update { list -> list.map { if (it.cosmeticId == cosmeticId) it.copy(isOwned = true) else it } }
    }

    override suspend fun equip(cosmeticId: String) {
        items.update { list -> list.map { if (it.cosmeticId == cosmeticId) it.copy(isEquipped = true) else it } }
    }

    override suspend fun unequip(cosmeticId: String) {
        items.update { list -> list.map { if (it.cosmeticId == cosmeticId) it.copy(isEquipped = false) else it } }
    }

    override suspend fun ensureSeedData() {}

    override suspend fun idExists(cosmeticId: String): Boolean = items.value.any { it.cosmeticId == cosmeticId }
}
