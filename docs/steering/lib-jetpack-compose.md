# Jetpack Compose — Reference Guide

## State Management

- Use `StateFlow` from ViewModels, collected in Compose via `collectAsStateWithLifecycle()`
- Use `remember` and `mutableStateOf` for local composable state
- Hoist state to the caller when multiple composables need it or when the ViewModel owns it
- `snapshotFlow {}` converts Compose state into a `Flow` for use in ViewModels

```kotlin
// ViewModel exposing StateFlow
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel() {
    val uiState: StateFlow<MyUiState> = repository.observeData()
        .map { data -> MyUiState(data) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyUiState())
}

// Composable collecting it
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
}
```

## Side Effects

- `LaunchedEffect(key)` — runs a coroutine when `key` changes; cancels and restarts on rekey
- `DisposableEffect(key)` — for setup/teardown (e.g., lifecycle observers); must call `onDispose {}`
- `rememberUpdatedState(value)` — captures the latest value inside a long-lived effect without restarting it

```kotlin
LaunchedEffect(pulseRateMs) {
    while (isActive) {
        delay(pulseRateMs)
        alpha.animateTo(0f)
        alpha.animateTo(1f)
    }
}

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) currentOnStart()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

## Navigation

- Use Navigation Compose (`NavHost`, `composable()` routes)
- Pass simple args via route strings; complex data via ViewModels or saved state
- Single Activity architecture (`MainActivity` hosts all Compose content)

## Activity Setup

- `@AndroidEntryPoint` for Hilt injection
- `enableEdgeToEdge()` for full-screen rendering
- `setContent { StepsOfBabylonTheme { ... } }` wraps all screens in the app theme

## Project Conventions

- All screens are `@Composable` functions named `*Screen.kt`
- ViewModels expose `StateFlow`, never `LiveData`
- Battle renderer uses custom `SurfaceView`, not Compose
- Theme defined in `presentation/ui/theme/` (Material3)
- Use `stateIn` with `WhileSubscribed(5000)` to share flows in ViewModels
