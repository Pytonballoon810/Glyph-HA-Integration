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
            val useCaseRaw = item.optString("useCase", "")
            if (useCaseRaw.isNotBlank()) {
                output += SensorMapping(
                    useCase = UseCaseType.entries.firstOrNull { it.name == useCaseRaw }
                        ?: UseCaseType.TRACK_3D_PRINTER_PROGRESS,
                    progressEntityId = item.optString("progressEntityId", "").ifBlank {
                        item.optString("entityId", "")
                    },
                    maxValue = item.optDouble("maxValue", 100.0),
                    remainingTimeEntityId = item.optString("remainingTimeEntityId", "").ifBlank {
                        item.optString("secondaryTextEntityId", "")
                    }.ifBlank { null },
                    interruptedEntityId = item.optString("interruptedEntityId", "").ifBlank { null },
                    genericDisplayMode = GenericDisplayMode.entries.firstOrNull {
                        it.name == item.optString("genericDisplayMode", GenericDisplayMode.NUMBER.name)
                    } ?: GenericDisplayMode.NUMBER,
                    turnOffValue = item.optString("turnOffValue", "").ifBlank { null },
                    resetValue = item.optString("resetValue", "").ifBlank { null },
                    genericErrorEntityId = item.optString("genericErrorEntityId", "").ifBlank { null },
                    genericErrorTriggerValue = item.optString("genericErrorTriggerValue", "").ifBlank { null }
                )
                continue
            }

            // Legacy migration path from mode-based records.
            val legacyEntity = item.optString("entityId", "")
            if (legacyEntity.isBlank()) continue
            output += SensorMapping(
                useCase = UseCaseType.TRACK_3D_PRINTER_PROGRESS,
                progressEntityId = legacyEntity,
                maxValue = item.optDouble("maxValue", 100.0),
                remainingTimeEntityId = item.optString("secondaryTextEntityId", "").ifBlank { null },
                interruptedEntityId = null,
                genericDisplayMode = GenericDisplayMode.entries.firstOrNull {
                    it.name == item.optString("mode", "")
                } ?: GenericDisplayMode.NUMBER,
                turnOffValue = null,
                resetValue = null,
                genericErrorEntityId = null,
                genericErrorTriggerValue = null
            )
        }
        return output
    }

    fun saveMappings(mappings: List<SensorMapping>) {
        val array = JSONArray()
        mappings.forEach {
            val obj = JSONObject()
                .put("useCase", it.useCase.name)
                .put("progressEntityId", it.progressEntityId)
                .put("maxValue", it.maxValue)
                .put("remainingTimeEntityId", it.remainingTimeEntityId ?: "")
                .put("interruptedEntityId", it.interruptedEntityId ?: "")
                .put("genericDisplayMode", it.genericDisplayMode.name)
                .put("turnOffValue", it.turnOffValue ?: "")
                .put("resetValue", it.resetValue ?: "")
                .put("genericErrorEntityId", it.genericErrorEntityId ?: "")
                .put("genericErrorTriggerValue", it.genericErrorTriggerValue ?: "")
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

    fun saveErrorIconType(type: CompletionIconType) {
        prefs.edit().putString(KEY_ERROR_ICON_TYPE, type.name).apply()
    }

    fun loadErrorIconType(): CompletionIconType {
        val raw = prefs.getString(KEY_ERROR_ICON_TYPE, CompletionIconType.CHECK.name)
            ?: CompletionIconType.CHECK.name
        return CompletionIconType.entries.firstOrNull { it.name == raw } ?: CompletionIconType.CHECK
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
        private const val KEY_ERROR_ICON_TYPE = "error_icon_type"
        private const val KEY_CUSTOM_ICON_DATA = "custom_icon_data"
    }
}
