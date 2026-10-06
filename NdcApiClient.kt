package com.campmeds.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client for the openFDA NDC Directory API. This is the ONLY network call the app makes
 * (spec section 2/7) — one lookup per medication, whose result is cached in Room afterward so
 * the app works fully offline from then on.
 */
class NdcApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val BASE_URL = "https://api.fda.gov/drug/ndc.json"
    }

    /** Normalizes an NDC to the plain digit/dash form openFDA expects for product_ndc search. */
    private fun normalize(ndc: String): String = ndc.trim()

    suspend fun lookup(ndc: String): NdcLookupResult = withContext(Dispatchers.IO) {
        val normalized = normalize(ndc)
        if (normalized.isBlank()) return@withContext NdcLookupResult.Error("NDC is empty")

        val url = "$BASE_URL?search=product_ndc:\"$normalized\"&limit=1"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    // openFDA returns 404 with a NOT_FOUND error body for zero matches.
                    return@withContext NdcLookupResult.NotFound
                }
                if (!response.isSuccessful) {
                    return@withContext NdcLookupResult.Error("HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: return@withContext NdcLookupResult.Error("Empty response body")

                parse(normalized, body)
            }
        } catch (e: IOException) {
            NdcLookupResult.Error(e.message ?: "Network error")
        }
    }

    private fun parse(ndc: String, body: String): NdcLookupResult {
        val json = JSONObject(body)
        val results = json.optJSONArray("results") ?: return NdcLookupResult.NotFound
        if (results.length() == 0) return NdcLookupResult.NotFound

        val first = results.getJSONObject(0)
        val name = first.optString("brand_name").ifBlank {
            first.optString("generic_name", "Unknown medication")
        }
        val form = first.optString("dosage_form", "Unknown form")

        val strength = buildString {
            val ingredients = first.optJSONArray("active_ingredients")
            if (ingredients != null && ingredients.length() > 0) {
                for (i in 0 until ingredients.length()) {
                    val ing = ingredients.getJSONObject(i)
                    val strengthVal = ing.optString("strength", "")
                    if (strengthVal.isNotBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(strengthVal)
                    }
                }
            }
            if (isEmpty()) append("Not specified")
        }

        return NdcLookupResult.Found(ndc = ndc, name = name, strength = strength, form = form)
    }
}
