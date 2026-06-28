package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.os.Build

/**
 * A ContextWrapper that ensures that the [getApplicationContext] returns an attributed context.
 * This is crucial for ExoPlayer and other media components that internally use the application context
 * instead of the passed context, thereby dropping the attribution tag and causing AppOps errors.
 */
class AttributedContextWrapper(base: Context, private val tag: String) : ContextWrapper(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        base.createAttributionContext(tag)
    } else {
        base
    }
) {
    override fun getApplicationContext(): Context {
        val baseAppContext = super.getApplicationContext()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseAppContext.createAttributionContext(tag)
        } else {
            baseAppContext
        }
    }
}

/**
 * Extension function to easily convert any Context into an attributed Context wrapper.
 */
fun Context.toAttributedContext(tag: String): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AttributedContextWrapper(this, tag)
    } else {
        this
    }
}
