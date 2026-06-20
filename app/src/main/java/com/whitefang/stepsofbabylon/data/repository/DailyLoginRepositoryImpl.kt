package com.whitefang.stepsofbabylon.data.repository

import com.whitefang.stepsofbabylon.data.local.DailyLoginDao
import com.whitefang.stepsofbabylon.data.local.DailyLoginEntity
import com.whitefang.stepsofbabylon.domain.model.DailyLogin
import com.whitefang.stepsofbabylon.domain.repository.DailyLoginRepository
import javax.inject.Inject

class DailyLoginRepositoryImpl @Inject constructor(
    private val dao: DailyLoginDao,
) : DailyLoginRepository {

    override suspend fun getByDate(date: String): DailyLogin? = dao.getByDate(date)?.toDomain()

    override suspend fun upsert(login: DailyLogin) = dao.upsert(login.toEntity())

    private fun DailyLoginEntity.toDomain() = DailyLogin(
        date = date, stepsWalked = stepsWalked,
        powerStoneClaimed = powerStoneClaimed, gemsClaimed = gemsClaimed,
    )

    private fun DailyLogin.toEntity() = DailyLoginEntity(
        date = date, stepsWalked = stepsWalked,
        powerStoneClaimed = powerStoneClaimed, gemsClaimed = gemsClaimed,
    )
}
