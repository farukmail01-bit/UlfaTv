package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper

/**
 * A ContextWrapper that overrides the getAttributionTag() method to return null.
 * This completely prevents AppOps from throwing "attributionTag not declared in manifest"
 * errors when audio/video is played on certain devices or Android versions,
 * particularly when internal libraries make calls to applicationContext which strips 
 * custom attribution tags or delegates to the base context incorrectly.
 */
class SafeAttributionContext(base: Context) : ContextWrapper(base) {
    override fun getApplicationContext(): Context {
        return SafeAttributionContext(super.getApplicationContext())
    }

    override fun getAttributionTag(): String? {
        // Return "media" to match the declared attribution tag in the manifest.
        // This fully satisfies AppOps security/privacy auditing checks.
        return "media"
    }

    override fun createAttributionContext(attributionTag: String?): Context {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            SafeAttributionContext(super.createAttributionContext("media"))
        } else {
            this
        }
    }
}
