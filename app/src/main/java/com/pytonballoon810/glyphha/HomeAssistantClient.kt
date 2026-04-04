package com.pytonballoon810.glyphha

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HomeAssistantClient(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun fetchState(entityId: String): SensorState {
        val request = Request.Builder()
            .url("$baseUrl/api/states/$entityId")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Home Assistant returned ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val rawState = json.optString("state", "unknown")
            val numericValue = parseNumeric(rawState)
            val unit = json.optJSONObject("attributes")?.optString("unit_of_measurement")

            return SensorState(value = numericValue, rawState = rawState, unit = unit)
        }
    }

    private fun parseNumeric(rawState: String): Double? {
        val cleaned = rawState
            .trim()
            .replace("%", "")
            .replace(",", ".")
            .replace(Regex("[^0-9+\\-\\.]"), "")
        return cleaned.toDoubleOrNull()
    }
}
