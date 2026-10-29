package com.kalaiselvan.ride.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory mock holder for Achievements.
 * Values are NEVER persisted to SharedPreferences or disk.
 * When the app process is terminated and reopened, isMocked resets to false
 * and actual user preferences are displayed automatically.
 */
object MockAchievementsHolder {
    var isMocked: Boolean by mutableStateOf(false)

    // User requested mock values:
    // 1. Top Speed - 246 date- 18th Oct
    // 2. 0-100 - 3.4 sec
    // 3. max G-Force 0.99
    const val MOCK_TOP_SPEED: Float = 246f
    const val MOCK_TOP_SPEED_DATE: String = "18th Oct, 2026"

    const val MOCK_ZERO_TO_HUNDRED: Float = 3.4f
    const val MOCK_ZERO_TO_HUNDRED_DATE: String = "18th Oct, 2026"

    const val MOCK_MAX_G_FORCE: Float = 0.99f
    const val MOCK_MAX_G_FORCE_DATE: String = "18th Oct, 2026"
}
