package com.whitefang.stepsofbabylon.di

import android.content.Context
import androidx.room.Room
import com.whitefang.stepsofbabylon.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = DatabaseKeyManager.getPassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        // Downgrades (dev/QA only) reset gracefully; upgrades require explicit Migration objects.
        return Room.databaseBuilder(context, AppDatabase::class.java, "steps_of_babylon.db")
            .openHelperFactory(factory)
            .addMigrations(*AppMigrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    @Provides fun providePlayerProfileDao(db: AppDatabase): PlayerProfileDao = db.playerProfileDao()
    @Provides fun provideWorkshopDao(db: AppDatabase): WorkshopDao = db.workshopDao()
    @Provides fun provideLabDao(db: AppDatabase): LabDao = db.labDao()
    @Provides fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()
    @Provides fun provideUltimateWeaponDao(db: AppDatabase): UltimateWeaponDao = db.ultimateWeaponDao()
    @Provides fun provideDailyStepDao(db: AppDatabase): DailyStepDao = db.dailyStepDao()
    @Provides fun provideWalkingEncounterDao(db: AppDatabase): WalkingEncounterDao = db.walkingEncounterDao()
    @Provides fun provideWeeklyChallengeDao(db: AppDatabase): WeeklyChallengeDao = db.weeklyChallengeDao()
    @Provides fun provideDailyLoginDao(db: AppDatabase): DailyLoginDao = db.dailyLoginDao()
    @Provides fun provideMilestoneDao(db: AppDatabase): MilestoneDao = db.milestoneDao()
    @Provides fun provideDailyMissionDao(db: AppDatabase): DailyMissionDao = db.dailyMissionDao()
    @Provides fun provideCosmeticDao(db: AppDatabase): CosmeticDao = db.cosmeticDao()
}
