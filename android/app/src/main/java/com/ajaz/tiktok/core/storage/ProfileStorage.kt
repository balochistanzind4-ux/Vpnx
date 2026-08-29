package com.ajaz.tiktok.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.parser.ClashYamlParser
import com.ajaz.tiktok.core.parser.NetworkProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfileStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ajaz_profiles_db", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<NetworkProfile>>(emptyList())
    val profiles: StateFlow<List<NetworkProfile>> = _profiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val jsonString = prefs.getString("profiles_json", "[]") ?: "[]"
        val activeId = prefs.getString("active_profile_id", null)

        val list = mutableListOf<NetworkProfile>()
        try {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val sourceUrl = if (obj.has("sourceUrl") && !obj.isNull("sourceUrl")) obj.getString("sourceUrl") else null
                val rawConfig = obj.getString("rawConfig")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                val selectedProxyId = obj.optString("selectedProxyId", null)

                val parsed = ClashYamlParser.parse(rawConfig, name, sourceUrl)
                val fullProfile = parsed.copy(
                    id = id,
                    name = name,
                    sourceUrl = sourceUrl,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    selectedProxyId = selectedProxyId ?: parsed.selectedProxyId
                )
                list.add(fullProfile)
            }
        } catch (e: Exception) {
            AppLogger.e("ProfileStorage", "Failed to deserialize profiles from disk: ${e.message}")
        }

        _profiles.value = list
        _activeProfileId.value = if (list.any { it.id == activeId }) activeId else list.firstOrNull()?.id
        AppLogger.i("ProfileStorage", "Loaded ${list.size} profiles from storage")
    }

    private fun saveToDisk() {
        try {
            val arr = JSONArray()
            for (p in _profiles.value) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("sourceUrl", p.sourceUrl)
                    put("rawConfig", p.rawConfig)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                    put("selectedProxyId", p.selectedProxyId)
                }
                arr.put(obj)
            }
            prefs.edit()
                .putString("profiles_json", arr.toString())
                .putString("active_profile_id", _activeProfileId.value)
                .apply()
        } catch (e: Exception) {
            AppLogger.e("ProfileStorage", "Failed to save profiles to disk: ${e.message}")
        }
    }

    fun addOrUpdateProfile(profile: NetworkProfile): NetworkProfile {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        val updated = if (index >= 0) {
            profile.copy(updatedAt = System.currentTimeMillis())
        } else {
            profile
        }

        if (index >= 0) {
            current[index] = updated
            AppLogger.i("ProfileStorage", "Updated profile '${updated.name}' (${updated.proxyCount} providers)")
        } else {
            current.add(0, updated)
            AppLogger.i("ProfileStorage", "Saved new profile '${updated.name}' (${updated.proxyCount} providers)")
        }

        _profiles.value = current
        if (_activeProfileId.value == null || current.size == 1) {
            _activeProfileId.value = updated.id
        }
        saveToDisk()
        return updated
    }

    fun setActiveProfile(profileId: String) {
        if (_profiles.value.any { it.id == profileId }) {
            _activeProfileId.value = profileId
            prefs.edit().putString("active_profile_id", profileId).apply()
            AppLogger.i("ProfileStorage", "Active profile switched to id: $profileId")
        }
    }

    fun getActiveProfile(): NetworkProfile? {
        val id = _activeProfileId.value ?: return null
        return _profiles.value.find { it.id == id }
    }

    fun selectNodeInProfile(profileId: String, nodeId: String) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            val prof = current[index]
            val updated = prof.copy(selectedProxyId = nodeId)
            current[index] = updated
            _profiles.value = current
            saveToDisk()
            AppLogger.d("ProfileStorage", "Selected provider node '$nodeId' in profile '${prof.name}'")
        }
    }

    fun deleteProfile(profileId: String) {
        val current = _profiles.value.toMutableList()
        val target = current.find { it.id == profileId }
        current.removeAll { it.id == profileId }
        _profiles.value = current

        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = current.firstOrNull()?.id
        }
        saveToDisk()
        AppLogger.w("ProfileStorage", "Deleted profile '${target?.name ?: profileId}'")
    }

    fun renameProfile(profileId: String, newName: String) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            val old = current[index]
            val updated = old.copy(name = newName.trim(), updatedAt = System.currentTimeMillis())
            current[index] = updated
            _profiles.value = current
            saveToDisk()
            AppLogger.i("ProfileStorage", "Renamed profile '${old.name}' -> '$newName'")
        }
    }

    fun duplicateProfile(profileId: String) {
        val target = _profiles.value.find { it.id == profileId } ?: return
        val dup = target.copy(
            id = UUID.randomUUID().toString(),
            name = "${target.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        addOrUpdateProfile(dup)
    }

    fun clearAll() {
        _profiles.value = emptyList()
        _activeProfileId.value = null
        prefs.edit().clear().apply()
        AppLogger.w("ProfileStorage", "All profiles purged from device")
    }
}
