@Dao
interface DailyMissionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DailyMissionEntity)

    @Transaction
    suspend fun insertIfNotExists(entity: DailyMissionEntity) {
        if (getByDateOnce(entity.date).isEmpty()) {
            insert(entity)
        }
    }

    // ... other functions
}