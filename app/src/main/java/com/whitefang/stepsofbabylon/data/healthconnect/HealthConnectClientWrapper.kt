package com.whitefang.stepsofbabylon.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectClientWrapper
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            val REQUIRED_PERMISSIONS =
                setOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                )
        }

        fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        fun getClient(): HealthConnectClient? = if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

        suspend fun hasPermissions(): Boolean {
            val client = getClient() ?: return false
            val granted = client.permissionController.getGrantedPermissions()
            return granted.containsAll(REQUIRED_PERMISSIONS)
        }

        fun getRequiredPermissions(): Set<String> = REQUIRED_PERMISSIONS

        fun getPermissionContract() = PermissionController.createRequestPermissionResultContract()
    }
