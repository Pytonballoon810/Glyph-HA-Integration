package com.pytonballoon810.glyphha

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SensorMappingStore(context: Context) {
    private val prefs = context.getSharedPreferences("glyph_ha_store", Context.MODE_PRIVATE)

    fun saveConnection(baseUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun loadBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""

    fun loadToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""

    fun loadMappings(): MutableList<SensorMapping> {
        val encoded = prefs.getString(KEY_MAPPINGS, null) ?: return mutableListOf()
        val jsonArray = JSONArray(encoded)
        val output = mutableListOf<SensorMapping>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            output += SensorMapping(
                entityId = item.getString("entityId"),
                mode = DisplayMode.valueOf(item.getString("mode")),
                maxValue = item.optDouble("maxValue", 100.0)
            )
        }
        return output
    }

    fun saveMappings(mappings: List<SensorMapping>) {
        val array = JSONArray()
        mappings.forEach {
            val obj = JSONObject()
                .put("entityId", it.entityId)
                .put("mode", it.mode.name)
                .put("maxValue", it.maxValue)
            array.put(obj)
        }
        prefs.edit().putString(KEY_MAPPINGS, array.toString()).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_MAPPINGS = "mappings"
    }
}
