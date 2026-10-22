package com.deutschdreamers.mensis.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deutschdreamers.mensis.data.settings.UiPrefsStore

/** DataStore allows one instance per file and process, so every instrumented test shares this store. */
object TestPrefs {
    val store: UiPrefsStore by lazy { UiPrefsStore(ApplicationProvider.getApplicationContext<Context>()) }
}
