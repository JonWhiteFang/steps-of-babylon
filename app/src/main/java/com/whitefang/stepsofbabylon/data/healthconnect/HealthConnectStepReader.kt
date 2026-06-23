package com.whitefang.stepsofbabylon.data.healthconnect

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectStepReader
    @Inject
    constructor(
        private val wrapper: HealthConnectClientWrapper,
    ) {
        suspend fun getStepsForDate(date: String): Long? {
            val client = wrapper.getClient() ?: return null
            if (!wrapper.hasPermissions()) return null

            val localDate = LocalDate.parse(date)
            val zone = ZoneId.systemDefault()
            val start = localDate.atStartOfDay(zone).toInstant()
            val end = localDate.plusDays(1).atStartOfDay(zone).toInstant()

            return try {
                val response =
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                        ),
                    )
                response[StepsRecord.COUNT_TOTAL]
            } catch (_: Exception) {
                null
            }
        }
    }
