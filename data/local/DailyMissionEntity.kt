@Entity(
    tableName = "daily_mission",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyMissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    // ... other fields
)