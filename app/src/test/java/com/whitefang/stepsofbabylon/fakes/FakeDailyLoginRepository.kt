package com.whitefang.stepsofbabylon.fakes

import com.whitefang.stepsofbabylon.domain.model.DailyLogin
import com.whitefang.stepsofbabylon.domain.repository.DailyLoginRepository

/** In-memory fake for [DailyLoginRepository] (#227). */
class FakeDailyLoginRepository : DailyLoginRepository {
    private val data = mutableMapOf<String, DailyLogin>()

    override suspend fun getByDate(date: String): DailyLogin? = data[date]

    override suspend fun upsert(login: DailyLogin) { data[login.date] = login }
}
