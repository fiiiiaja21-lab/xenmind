package com.xen.xenmind.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("xenmind_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) { sp.edit().putString("api_key", v).apply() }

    var modelChain: List<String>
        get() = sp.getString("model_chain", null)
            ?.split("|")?.filter { it.isNotBlank() }
            ?: ModelChain.default
        set(v) { sp.edit().putString("model_chain", v.joinToString("|")).apply() }

    var mode: String
        get() = sp.getString("mode", "jawab_penjelasan") ?: "jawab_penjelasan"
        set(v) { sp.edit().putString("mode", v).apply() }

    var retryCount: Int
        get() = sp.getInt("retry_count", 4)
        set(v) { sp.edit().putInt("retry_count", v).apply() }
}
