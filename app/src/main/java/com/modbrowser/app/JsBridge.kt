package com.modbrowser.app

import android.webkit.JavascriptInterface

/**
 * Exposed to web pages as `window.AndroidBridge`.
 * Only ever called from JS we control (VIDEO_WATCHER_JS), never from arbitrary
 * page script input, so no untrusted data crosses this bridge.
 */
class JsBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun onVideoStateChanged(playing: Boolean) {
        activity.runOnUiThread { activity.onVideoPlayStateChanged(playing) }
    }
}
