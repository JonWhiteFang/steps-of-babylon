package com.whitefang.stepsofbabylon.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies an application-lifetime [CoroutineScope] that **outlives ViewModel cancellation**.
 *
 * Use for fire-and-forget work that must complete even if the originating ViewModel is cleared
 * mid-operation. Canonical example: [com.whitefang.stepsofbabylon.presentation.battle.BattleViewModel.onCleared]
 * uses this scope to finish persisting an in-flight round when the user navigates away via a
 * deep-link mid-battle — `viewModelScope` would be cancelled by `onCleared()` itself, silently
 * discarding the round (RO-03 B.3 PR 2 gap closure).
 *
 * **Scope semantics:**
 *  - Singleton — one instance per Hilt [SingletonComponent].
 *  - [SupervisorJob] — a child failure doesn't cancel sibling coroutines.
 *  - [Dispatchers.Default] — CPU-oriented pool. Room DAO calls handle their own thread hop, so
 *    IO isn't required. Matches the [com.whitefang.stepsofbabylon.service.StepCounterService]
 *    precedent in the same project.
 *  - **Not cancelled** on its own — the scope lives for the JVM process lifetime. Acceptable
 *    because the launched work is short-lived (one end-of-round persistence fan-out) and
 *    idempotent (RO-02 transactions + RO-03 roundEnded guard).
 *
 * **Why not `ProcessLifecycleOwner.lifecycleScope`?** The RO-03 spec suggested that, but:
 *  - It requires `androidx.lifecycle:lifecycle-process`, which is NOT on the current classpath.
 *  - Its default dispatcher is `Dispatchers.Main` — wrong for DB writes.
 *  - Hilt-injected scope is more testable (tests inject a TestScope-backed CoroutineScope).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
