package com.deutschdreamers.mensis.data.db

import android.content.ContextWrapper
import java.io.File

/** Only [getDatabasePath] is used by the code under JVM test. */
class FakeContext : ContextWrapper(null) {
    override fun getDatabasePath(name: String): File = File("/data/data/test/databases", name)
}
