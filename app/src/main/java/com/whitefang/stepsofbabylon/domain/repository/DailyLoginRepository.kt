package com.whitefang.stepsofbabylon.domain.repository

import com.whitefang.stepsofbabylon.domain.model.DailyLogin

/** Domain port over the `daily_login` persistence (#227). */
interface DailyLoginRepository {
    suspend fun getByDate(date: String): DailyLogin?

    suspend fun upsert(login: DailyLogin)
}
