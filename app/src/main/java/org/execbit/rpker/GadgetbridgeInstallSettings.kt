package org.execbit.rpker

import android.content.Context
import androidx.core.content.edit

internal enum class GadgetbridgeInstallMethod {
    BROADCAST,
    ACTIVITY,
}

internal class GadgetbridgeInstallSettings(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun load(): GadgetbridgeInstallMethod {
        val storedValue = preferences.getString(METHOD_KEY, null)
        return GadgetbridgeInstallMethod.entries.firstOrNull { it.name == storedValue }
            ?: GadgetbridgeInstallMethod.ACTIVITY
    }

    fun save(method: GadgetbridgeInstallMethod) {
        preferences.edit { putString(METHOD_KEY, method.name) }
    }

    private companion object {
        const val PREFERENCES_NAME = "gadgetbridge_install_settings"
        const val METHOD_KEY = "install_method"
    }
}
