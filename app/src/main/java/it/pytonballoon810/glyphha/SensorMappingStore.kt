package it.pytonballoon810.glyphha

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
                maxValue = item.optDouble("maxValue", 100.0),
                secondaryTextEntityId = item.optString("secondaryTextEntityId", "").ifBlank { null }
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
                .put("secondaryTextEntityId", it.secondaryTextEntityId ?: "")
            array.put(obj)
        }
        prefs.edit().putString(KEY_MAPPINGS, array.toString()).apply()
    }

    fun saveCompletionIconType(type: CompletionIconType) {
        prefs.edit().putString(KEY_COMPLETION_ICON_TYPE, type.name).apply()
    }

    fun loadCompletionIconType(): CompletionIconType {
        val raw = prefs.getString(KEY_COMPLETION_ICON_TYPE, CompletionIconType.PRINTER.name)
            ?: CompletionIconType.PRINTER.name
        return CompletionIconType.entries.firstOrNull { it.name == raw } ?: CompletionIconType.PRINTER
    }

    fun saveCustomIconData(data: CustomIconData) {
        val array = JSONArray()
        data.activePixels.sorted().forEach { array.put(it) }

        val obj = JSONObject()
            .put("size", data.size)
            .put("pixels", array)

        prefs.edit().putString(KEY_CUSTOM_ICON_DATA, obj.toString()).apply()
    }

    fun loadCustomIconData(): CustomIconData? {
        val raw = prefs.getString(KEY_CUSTOM_ICON_DATA, null) ?: return null
        val obj = JSONObject(raw)
        val size = obj.optInt("size", 0)
        if (size <= 0) return null

        val pixelsArray = obj.optJSONArray("pixels") ?: JSONArray()
        val pixels = mutableSetOf<Int>()
        for (i in 0 until pixelsArray.length()) {
            pixels += pixelsArray.optInt(i)
        }

        return CustomIconData(size = size, activePixels = pixels)
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_MAPPINGS = "mappings"
        private const val KEY_COMPLETION_ICON_TYPE = "completion_icon_type"
        private const val KEY_CUSTOM_ICON_DATA = "custom_icon_data"
    }
}
