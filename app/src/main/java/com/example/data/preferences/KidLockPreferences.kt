package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kidLockDataStore: DataStore<Preferences> by preferencesDataStore(name = "kid_lock_preferences")

data class KidLockSettings(
    val isKidLockEnabled: Boolean = false,
    val parentPin: String = "",
    val autoplayNext: Boolean = true,
    val sleepTimerDurationMinutes: Int = 0,
    val sleepTimerEndTimestampMs: Long = 0L
)

class KidLockPreferences(private val context: Context) {

    private object Keys {
        val KID_LOCK_ENABLED = booleanPreferencesKey("kid_lock_enabled")
        val PARENT_PIN = stringPreferencesKey("parent_pin")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val SLEEP_TIMER_DURATION_MIN = intPreferencesKey("sleep_timer_duration_min")
        val SLEEP_TIMER_END_TS = longPreferencesKey("sleep_timer_end_ts")
    }

    val settingsFlow: Flow<KidLockSettings> = context.kidLockDataStore.data.map { prefs ->
        KidLockSettings(
            isKidLockEnabled = prefs[Keys.KID_LOCK_ENABLED] ?: false,
            parentPin = prefs[Keys.PARENT_PIN] ?: "",
            autoplayNext = prefs[Keys.AUTOPLAY_NEXT] ?: true,
            sleepTimerDurationMinutes = prefs[Keys.SLEEP_TIMER_DURATION_MIN] ?: 0,
            sleepTimerEndTimestampMs = prefs[Keys.SLEEP_TIMER_END_TS] ?: 0L
        )
    }

    suspend fun setKidLockEnabled(enabled: Boolean) {
        context.kidLockDataStore.edit { prefs ->
            prefs[Keys.KID_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setParentPin(pin: String) {
        val sanitized = pin.filter { it.isDigit() }.take(4)
        context.kidLockDataStore.edit { prefs ->
            prefs[Keys.PARENT_PIN] = if (sanitized.length == 4) sanitized else ""
        }
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        context.kidLockDataStore.edit { prefs ->
            prefs[Keys.AUTOPLAY_NEXT] = enabled
        }
    }

    suspend fun setSleepTimer(minutes: Int) {
        context.kidLockDataStore.edit { prefs ->
            if (minutes > 0) {
                prefs[Keys.SLEEP_TIMER_DURATION_MIN] = minutes
                prefs[Keys.SLEEP_TIMER_END_TS] = System.currentTimeMillis() + minutes * 60_000L
            } else {
                prefs[Keys.SLEEP_TIMER_DURATION_MIN] = 0
                prefs[Keys.SLEEP_TIMER_END_TS] = 0L
            }
        }
    }
}
