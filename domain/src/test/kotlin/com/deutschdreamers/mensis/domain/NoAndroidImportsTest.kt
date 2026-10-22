package com.deutschdreamers.mensis.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The domain module must stay a pure JVM module: no Android, Hilt or Compose imports. */
class NoAndroidImportsTest {
    private val forbiddenPrefixes =
        listOf("import android.", "import androidx.", "import dagger.", "import javax.inject.")

    @Test
    fun `main sources contain no android imports`() {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "expected ${root.absolutePath} to exist")
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (forbiddenPrefixes.any {
                                line.trimStart().startsWith(
                                    it,
                                )
                            }
                        ) {
                            "${file.path}:${index + 1}: $line"
                        } else {
                            null
                        }
                    }
                }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
