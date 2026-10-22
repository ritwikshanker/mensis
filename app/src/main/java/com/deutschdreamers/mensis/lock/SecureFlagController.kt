package com.deutschdreamers.mensis.lock

import android.view.Window
import android.view.WindowManager

/** Privacy rule 5: FLAG_SECURE blocks screenshots and blanks the Recents preview; "Hide in recent apps" toggles it. */
object SecureFlagController {
    fun apply(
        window: Window,
        hideInRecents: Boolean,
    ) {
        if (hideInRecents) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
