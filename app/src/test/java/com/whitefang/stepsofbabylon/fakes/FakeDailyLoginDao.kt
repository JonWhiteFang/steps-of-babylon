package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.data.local.DailyLoginDao
import com.whitefang.stepsofbabylon.data.local.DailyLoginEntity

class FakeDailyLoginDao : DailyLoginDao {
    private val data = mutableMapOf<String, DailyLoginEntity>()

    override suspend fun getByDate(date: String): DailyLoginEntity? = data[date]

    override suspend fun upsert(entity: DailyLoginEntity) {
        data[entity.date] = entity
    }
}
