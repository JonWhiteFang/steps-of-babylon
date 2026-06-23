package com.whitefang.stepsofbabylon.presentation.weapons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitefang.stepsofbabylon.domain.model.OwnedWeapon
import com.whitefang.stepsofbabylon.domain.model.UWPath
import com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import com.whitefang.stepsofbabylon.domain.repository.UltimateWeaponRepository
import com.whitefang.stepsofbabylon.domain.usecase.UnlockUltimateWeapon
import com.whitefang.stepsofbabylon.domain.usecase.UpgradeUltimateWeapon
import com.whitefang.stepsofbabylon.presentation.ui.SCREEN_LOAD_ERROR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** R4-06: per-path display row backing the 3 Upgrade buttons in [UltimateWeaponScreen]. */
data class UWPathDisplay(
    val path: UWPath,
    val level: Int,
    val cost: Int,
    val canAfford: Boolean,
    val isMaxed: Boolean,
)

/**
 * R4-06: per-UW display state. Pre-R4-06 this was a flat data class with a single
 * `level` axis; post-R4-06 it carries the [isUnlocked] flag plus a [paths] map keyed by
 * [UWPath] so the screen renders one Upgrade row per path. The map is empty when the
 * UW is locked (no level state to show).
 */
data class UWDisplayInfo(
    val type: UltimateWeaponType,
    val isUnlocked: Boolean,
    val isEquipped: Boolean,
    val canAffordUnlock: Boolean,
    val paths: Map<UWPath, UWPathDisplay>,
)

data class UltimateWeaponUiState(
    val weapons: List<UWDisplayInfo> = emptyList(),
    val powerStones: Long = 0,
    val equippedCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UltimateWeaponViewModel
    @Inject
    constructor(
        private val uwRepository: UltimateWeaponRepository,
        private val playerRepository: PlayerRepository,
    ) : ViewModel() {
        private val unlockUW = UnlockUltimateWeapon(uwRepository)
        private val upgradeUW = UpgradeUltimateWeapon(uwRepository, playerRepository)
        private var ownedList: List<OwnedWeapon> = emptyList()

        // #194: bump to re-subscribe the data flow after a load error (retry).
        private val _retry = MutableStateFlow(0)

        val uiState: StateFlow<UltimateWeaponUiState> =
            _retry
                .flatMapLatest {
                    combine(
                        uwRepository.observeUnlockedWeapons(),
                        playerRepository.observeWallet(),
                    ) { owned, wallet ->
                        ownedList = owned
                        val ownedMap = owned.associateBy { it.type }
                        UltimateWeaponUiState(
                            weapons =
                                UltimateWeaponType.entries.map { type ->
                                    val ow = ownedMap[type]
                                    val isUnlocked = ow?.isUnlocked == true
                                    val paths =
                                        if (isUnlocked) {
                                            UWPath.ALL.associateWith { path ->
                                                val level = ow!!.levelOf(path)
                                                val cost = type.costForPath(level)
                                                UWPathDisplay(
                                                    path = path,
                                                    level = level,
                                                    cost = cost,
                                                    canAfford =
                                                        level < UltimateWeaponType.MAX_PATH_LEVEL &&
                                                            wallet.powerStones >= cost,
                                                    isMaxed = level >= UltimateWeaponType.MAX_PATH_LEVEL,
                                                )
                                            }
                                        } else {
                                            emptyMap()
                                        }
                                    UWDisplayInfo(
                                        type = type,
                                        isUnlocked = isUnlocked,
                                        isEquipped = ow?.isEquipped == true,
                                        canAffordUnlock = !isUnlocked && wallet.powerStones >= type.unlockCost,
                                        paths = paths,
                                    )
                                },
                            powerStones = wallet.powerStones,
                            equippedCount = owned.count { it.isEquipped && it.isUnlocked },
                            isLoading = false,
                        )
                    }
                        // #194: surface a source-flow throw as an error state, not a silent spinner. .catch INSIDE
                        // flatMapLatest so retry() re-subscribes.
                        .catch { emit(UltimateWeaponUiState(isLoading = false, error = SCREEN_LOAD_ERROR)) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UltimateWeaponUiState())

        /** #194: re-subscribe the data flow after a load error. */
        fun retry() {
            _retry.value++
        }

        fun unlock(type: UltimateWeaponType) {
            viewModelScope.launch { unlockUW(type, uiState.value.powerStones, ownedList) }
        }

        fun upgrade(
            type: UltimateWeaponType,
            path: UWPath,
        ) {
            val info = uiState.value.weapons.find { it.type == type } ?: return
            val pathInfo = info.paths[path] ?: return
            viewModelScope.launch {
                upgradeUW(type, path, pathInfo.level, uiState.value.powerStones)
            }
        }

        fun toggleEquip(type: UltimateWeaponType) {
            val info = uiState.value.weapons.find { it.type == type } ?: return
            if (!info.isUnlocked) return
            viewModelScope.launch {
                if (info.isEquipped) {
                    uwRepository.unequipWeapon(type)
                } else if (uiState.value.equippedCount < 3) {
                    uwRepository.equipWeapon(type)
                }
            }
        }
    }
