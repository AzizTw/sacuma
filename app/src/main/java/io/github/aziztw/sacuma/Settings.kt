package io.github.aziztw.sacuma

import android.content.Context

/** User settings, saved in SharedPreferences (a small key-value file private to the app). */
data class Settings(
    val xboxLayout: Boolean = false,
    val stickDeadzone: Int = 6,     // % of full tilt
    val triggerDeadzone: Int = 10,  // analog units above rest
) {
    fun save(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_XBOX, xboxLayout)
            .putInt(KEY_STICK_DZ, stickDeadzone)
            .putInt(KEY_TRIGGER_DZ, triggerDeadzone)
            .apply()
    }

    companion object {
        private const val KEY_XBOX = "xbox_layout"
        private const val KEY_STICK_DZ = "stick_deadzone"
        private const val KEY_TRIGGER_DZ = "trigger_deadzone"

        private fun prefs(context: Context) =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        fun load(context: Context): Settings {
            val p = prefs(context)
            val d = Settings()
            return Settings(
                xboxLayout = p.getBoolean(KEY_XBOX, d.xboxLayout),
                stickDeadzone = p.getInt(KEY_STICK_DZ, d.stickDeadzone),
                triggerDeadzone = p.getInt(KEY_TRIGGER_DZ, d.triggerDeadzone),
            )
        }
    }
}
