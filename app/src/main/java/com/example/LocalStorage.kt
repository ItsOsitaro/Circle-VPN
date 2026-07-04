package com.example

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LocalStorage {

    private const val PREFS_NAME = "v2tunnel_prefs"
    private const val KEY_SUB_URL = "sub_url"
    private const val KEY_CONFIGS = "configs"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val configListType = Types.newParameterizedType(List::class.java, V2RayConfig::class.java)
    private val listAdapter = moshi.adapter<List<V2RayConfig>>(configListType)

    fun saveSubscriptionUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SUB_URL, url).apply()
    }

    fun getSubscriptionUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SUB_URL, null)
    }

    fun saveConfigs(context: Context, configs: List<V2RayConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = listAdapter.toJson(configs)
        prefs.edit().putString(KEY_CONFIGS, json).apply()
    }

    fun getConfigs(context: Context): List<V2RayConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIGS, null) ?: return emptyList()
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
