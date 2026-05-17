package com.neldasi.dafscanner.extras

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

// Device utility functions

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
