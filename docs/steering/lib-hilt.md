# Dagger Hilt — Reference Guide

## Setup

- Application class annotated with `@HiltAndroidApp` (`StepsOfBabylonApp.kt`)
- Activities annotated with `@AndroidEntryPoint`
- Uses KSP for annotation processing (not kapt)

## Modules

- Annotate with `@Module` and `@InstallIn(ComponentClass::class)`
- Use `@Provides` for third-party or complex object creation
- Use `@Binds` for binding an interface to its implementation
- Scope with `@Singleton` when installed in `SingletonComponent`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "steps_of_babylon.db")
            .build()

    @Provides
    fun providePlayerDao(db: AppDatabase): PlayerDao = db.playerDao()
}

// Binding interface to implementation
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository
}
```

## ViewModels

- Annotate with `@HiltViewModel` and use `@Inject constructor`
- Hilt provides `SavedStateHandle` automatically
- Use `@ViewModelScoped` to scope dependencies to a ViewModel's lifecycle

```kotlin
@HiltViewModel
class WorkshopViewModel @Inject constructor(
    private val calculateUpgradeCost: CalculateUpgradeCost,
    private val savedStateHandle: SavedStateHandle
) : ViewModel()
```

## Component Scopes

| Annotation | Component | Lifetime |
|---|---|---|
| `@Singleton` | `SingletonComponent` | App lifetime |
| `@ViewModelScoped` | `ViewModelComponent` | ViewModel lifetime (survives config changes) |
| `@ActivityRetainedScoped` | `ActivityRetainedComponent` | Activity lifetime (survives config changes) |
| `@ActivityScoped` | `ActivityComponent` | Activity lifetime |

## Project Conventions

- All Hilt modules live in `di/` package
- Database and DAO providers in `DatabaseModule`
- Repository bindings in separate module(s)
- Domain layer has no Hilt annotations — inject use cases via constructor
