class GenerateDailyMissions(private val dailyMissionDao: DailyMissionDao) {
    suspend fun generateMissions(date: String) {
        val missions = listOf(
            // ... create mission entities
        )

        for (mission in missions) {
            dailyMissionDao.insertIfNotExists(mission)
        }
    }
}