package com.whitefang.stepsofbabylon.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class ExerciseSessionInfo(
    val exerciseType: Int,
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Int,
)

@Singleton
class ExerciseSessionReader
    @Inject
    constructor(
        private val wrapper: HealthConnectClientWrapper,
    ) {
        suspend fun getSessionsForDate(date: String): List<ExerciseSessionInfo> {
            val client = wrapper.getClient() ?: return emptyList()
            if (!wrapper.hasPermissions()) return emptyList()

            val localDate = LocalDate.parse(date)
            val zone = ZoneId.systemDefault()
            val start = localDate.atStartOfDay(zone).toInstant()
            val end = localDate.plusDays(1).atStartOfDay(zone).toInstant()

            return try {
                val response =
                    client.readRecords(
                        ReadRecordsRequest(
                            ExerciseSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                        ),
                    )
                response.records.map { record ->
                    ExerciseSessionInfo(
                        exerciseType = record.exerciseType,
                        startTime = record.startTime,
                        endTime = record.endTime,
                        durationMinutes = Duration.between(record.startTime, record.endTime).toMinutes().toInt(),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
