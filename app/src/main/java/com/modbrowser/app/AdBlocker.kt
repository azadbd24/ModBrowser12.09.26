package com.modbrowser.app

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Host-based ad/tracker blocker. Loads a hosts-file style list from assets
 * (assets/adblock_hosts.txt) into a HashSet for O(1) lookups per request,
 * including parent-domain matching (e.g. blocking "doubleclick.net" also
 * blocks "ads.g.doubleclick.net").
 *
 * The bundled list is a small starter set. For the strongest possible blocking,
 * replace assets/adblock_hosts.txt with a compiled hosts-file export of
 * EasyList + EasyPrivilege + a mobile ad list (e.g. via a hosts-list aggregator).
 * Any plain "hosts file" or one-domain-per-line list works with this loader.
 */
object AdBlocker {
    private val blockedHosts = HashSet<String>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
                    val parts = line.split(Regex("\\s+"))
                    val host = if (parts.size > 1) parts[1] else parts[0]
                    if (host.isNotEmpty() && host != "0.0.0.0" && host != "127.0.0.1") {
                        blockedHosts.add(host.lowercase())
                    }
                }
            }
        } catch (e: Exception) {
            // Missing/invalid list just means ad blocking is a no-op, not a crash.
        }
    }

    fun isAd(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (blockedHosts.contains(host)) return true
        val labels = host.split(".")
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (blockedHosts.contains(candidate)) return true
        }
        return false
    }

    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
