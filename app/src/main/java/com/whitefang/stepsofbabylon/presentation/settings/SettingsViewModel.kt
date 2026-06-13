package com.whitefang.stepsofbabylon.presentation.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.whitefang.stepsofbabylon.data.DataDeletionManager
import com.whitefang.stepsofbabylon.data.NotificationPreferences
import com.whitefang.stepsofbabylon.data.SoundPreferences
import com.whitefang.stepsofbabylon.presentation.audio.MusicPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsState(
    val persistentSteps: Boolean = true,
    val supplyDrops: Boolean = true,
    val smartReminders: Boolean = true,
    val milestoneAlerts: Boolean = true,
    val soundMuted: Boolean = false,
    val musicMuted: Boolean = false,
    val musicVolume: Float = 0.5f,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: NotificationPreferences,
    private val soundPrefs: SoundPreferences,
    private val musicPrefs: MusicPreferences,
    private val dataDeletionManager: DataDeletionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(
        persistentSteps = prefs.isPersistentEnabled(),
        supplyDrops = prefs.isSupplyDropsEnabled(),
        smartReminders = prefs.isSmartRemindersEnabled(),
        milestoneAlerts = prefs.isMilestoneAlertsEnabled(),
        soundMuted = soundPrefs.isMuted(),
        musicMuted = musicPrefs.isMuted(),
        musicVolume = musicPrefs.getVolume(),
    ))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun setPersistent(enabled: Boolean) { prefs.setPersistentEnabled(enabled); _state.update { it.copy(persistentSteps = enabled) } }
    fun setSupplyDrops(enabled: Boolean) { prefs.setSupplyDropsEnabled(enabled); _state.update { it.copy(supplyDrops = enabled) } }
    fun setSmartReminders(enabled: Boolean) { prefs.setSmartRemindersEnabled(enabled); _state.update { it.copy(smartReminders = enabled) } }
    fun setMilestoneAlerts(enabled: Boolean) { prefs.setMilestoneAlertsEnabled(enabled); _state.update { it.copy(milestoneAlerts = enabled) } }
    fun setSoundMuted(muted: Boolean) { soundPrefs.setMuted(muted); _state.update { it.copy(soundMuted = muted) } }
    fun setMusicMuted(muted: Boolean) { musicPrefs.setMuted(muted); _state.update { it.copy(musicMuted = muted) } }
    fun setMusicVolume(volume: Float) { musicPrefs.setVolume(volume); _state.update { it.copy(musicVolume = volume) } }

    fun deleteAllData(activity: Activity) {
        dataDeletionManager.deleteAllData(activity)
    }
}
