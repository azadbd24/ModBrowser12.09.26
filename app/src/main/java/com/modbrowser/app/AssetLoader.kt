package com.modbrowser.app

import android.content.Context

object AssetLoader {
    private val cache = HashMap<String, String>()

    fun readAsset(context: Context, name: String): String {
        cache[name]?.let { return it }
        val text = try {
            context.assets.open(name).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
        cache[name] = text
        return text
    }
}
