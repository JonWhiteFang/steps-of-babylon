package com.whitefang.stepsofbabylon.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PlayerProfileEntity::class,
        WorkshopUpgradeEntity::class,
        LabResearchEntity::class,
        CardInventoryEntity::class,
        UltimateWeaponStateEntity::class,
        DailyStepRecordEntity::class,
        WalkingEncounterEntity::class,
        WeeklyChallengeEntity::class,
        DailyLoginEntity::class,
        MilestoneEntity::class,
        DailyMissionEntity::class,
        CosmeticEntity::class,
        BillingReceiptEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerProfileDao(): PlayerProfileDao

    abstract fun workshopDao(): WorkshopDao

    abstract fun labDao(): LabDao

    abstract fun cardDao(): CardDao

    abstract fun ultimateWeaponDao(): UltimateWeaponDao

    abstract fun dailyStepDao(): DailyStepDao

    abstract fun walkingEncounterDao(): WalkingEncounterDao

    abstract fun weeklyChallengeDao(): WeeklyChallengeDao

    abstract fun dailyLoginDao(): DailyLoginDao

    abstract fun milestoneDao(): MilestoneDao

    abstract fun dailyMissionDao(): DailyMissionDao

    abstract fun cosmeticDao(): CosmeticDao

    abstract fun billingReceiptDao(): BillingReceiptDao
}
