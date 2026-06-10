# Kotlin Coroutines & Flow — Reference Guide

## Structured Concurrency

- Every coroutine runs in a `CoroutineScope` — when the scope cancels, all children cancel
- `viewModelScope` in ViewModels — auto-cancelled when ViewModel is cleared
- `coroutineScope {}` — creates a child scope; waits for all children, propagates exceptions

```kotlin
suspend fun loadData() = coroutineScope {
    val profile = async { repository.getProfile() }
    val wallet = async { repository.getWallet() }
    CombinedData(profile.await(), wallet.await())
}
```

## Dispatchers

| Dispatcher | Use |
|---|---|
| `Dispatchers.Main` | UI updates (default in viewModelScope) |
| `Dispatchers.IO` | Disk/network I/O |
| `Dispatchers.Default` | CPU-intensive work |

- Switch with `withContext(Dispatchers.IO) { ... }`
- Room handles its own threading — no need to wrap DAO calls in `withContext(IO)`

## Flow

- `Flow<T>` — cold stream, emits values when collected
- `StateFlow<T>` — hot stream with current value, used for UI state
- `MutableStateFlow<T>` — mutable version for ViewModels

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() {
    val uiState: StateFlow<MyUiState> = repository.observeData()
        .map { data -> MyUiState(data) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyUiState())
}
```

## Key Operators

- `map`, `filter` — transform/filter emissions
- `filterNotNull()` — used after Room DAO flows to skip null emissions
- `combine` — merge multiple flows
- `stateIn` — convert cold Flow to hot StateFlow
- `flatMapLatest` — switch to new flow when upstream emits

## Cancellation

- Coroutines are cooperative — use `isActive` checks in loops
- `ensureActive()` throws `CancellationException` if scope is cancelled
- `finally {}` blocks run on cancellation for cleanup

## Project Conventions

- All async operations use coroutines and Flow (no RxJava, no callbacks)
- ViewModels use `viewModelScope` for launching coroutines
- Repositories return `Flow<T>` for observable data, `suspend` for one-shot writes
- Use `stateIn` with `WhileSubscribed(5000)` to share flows in ViewModels
- Repository pattern: `dao.get().filterNotNull().map { it.toDomain() }`
