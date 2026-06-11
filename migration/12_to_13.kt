class Migration12To13 : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_mission_date ON daily_mission (date)")
        
        // Dedup pre-existing duplicates
        database.execSQL("""
            DELETE FROM daily_mission
            WHERE id IN (
                SELECT id
                FROM (
                    SELECT id,
                    ROW_NUMBER() OVER (PARTITION BY date ORDER BY id) AS row_num
                    FROM daily_mission
                ) AS subquery
                WHERE row_num > 1
            )
        """.trimIndent())
    }
}