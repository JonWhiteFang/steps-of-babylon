---
inclusion: fileMatch
fileMatchPattern: "**/data/local/**,**/data/repository/**,**/di/DatabaseModule*,**/AppDatabase*"
---

# Room Database — Reference Guide

## Core Annotations

- `@Entity` — defines a table; use `@PrimaryKey`, `@ColumnInfo` for customization
- `@Dao` — interface with query methods
- `@Database` — abstract class extending `RoomDatabase`, lists entities and version

## DAO Patterns

- `@Query` returning `Flow<T>` — re-emits when observed tables change (reactive)
- `@Upsert` — insert if new, update if exists (preferred approach in this project)
- `@Update`, `@Delete` — standard mutations
- Use `suspend` for one-shot write operations; `Flow` for observable reads
- Atomic field updates via `@Query("UPDATE ... SET col = col + :delta")` for currency adjustments

```kotlin
@Dao
interface PlayerProfileDao {
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun get(): Flow<PlayerProfileEntity?>

    @Upsert
    suspend fun upsert(entity: PlayerProfileEntity)

    @Query("UPDATE player_profile SET currentStepBalance = currentStepBalance + :delta, totalStepsEarned = CASE WHEN :delta > 0 THEN totalStepsEarned + :delta ELSE totalStepsEarned END WHERE id = 1")
    suspend fun adjustStepBalance(delta: Long)
}
```

## TypeConverters

- Use `@TypeConverter` for types Room can't store natively
- Register via `@TypeConverters` on the `@Database` class
- This project converts `Map<Int, Int>` and `Map<String, Int>` to/from JSON strings

```kotlin
class Converters {
    @TypeConverter
    fun fromIntIntMap(map: Map<Int, Int>): String =
        JSONObject(map.mapKeys { it.key.toString() }).toString()

    @TypeConverter
    fun toIntIntMap(json: String): Map<Int, Int> =
        JSONObject(json).let { obj ->
            obj.keys().asSequence().associate { it.toInt() to obj.getInt(it) }
        }
}
```

## Database Setup

- SQLCipher encryption via `SupportOpenHelperFactory` with Android Keystore-managed passphrase
- v1.0.0: no destructive migration — future schema changes require proper Migration objects
- Schema exported to `app/schemas/` — commit these files
- Schema location configured via Room Gradle plugin: `room { schemaDirectory("$projectDir/schemas") }`

## Migrations

- Auto migrations: `@Database(autoMigrations = [@AutoMigration(from = 1, to = 2)])`
- Manual migrations for complex changes (column renames, data transforms)
- Current schema version: 8

## Flow Integration

- `Flow` queries automatically re-emit when underlying table data changes
- Collect in ViewModel, expose as `StateFlow` to Compose
- No manual invalidation needed — Room handles it
- Repository pattern: DAO Flow → `filterNotNull()` → `map { entity.toDomain() }`

```kotlin
class PlayerRepositoryImpl @Inject constructor(
    private val dao: PlayerProfileDao,
) : PlayerRepository {
    override fun observeWallet(): Flow<PlayerWallet> =
        dao.get().filterNotNull().map { it.toDomain().toWallet() }
}
```

## Project Conventions

- Entity files: `*Entity.kt` in `data/local/`
- DAO files: `*Dao.kt` in `data/local/`
- Database: `AppDatabase.kt` in `data/local/` (12 entities, 12 DAOs)
- Room is the single source of truth for all game state
- Player profile uses single-row pattern (id=1)
- Prefer `@Upsert` over `@Insert(onConflict = REPLACE)`
- Room handles its own threading — no need to wrap DAO calls in `withContext(IO)`
