package com.victorarana.leo.support

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Snapshot references live in one folder per test class (`src/test/snapshots/<TestClass>/`), so two test files with
 * the same name would overwrite each other's images. Port of `SnapshotLayoutTests.testFileNamesAreUnique`.
 */
class SnapshotLayoutTests {
    @Test
    fun testFileNamesAreUnique() {
        val sources = File("src/test/java")
        assertTrue("Run from the :app module, ${sources.absolutePath} not found", sources.isDirectory)
        val duplicates =
            sources
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .groupBy { it.nameWithoutExtension }
                .filterValues { it.size > 1 }
        assertTrue(
            "Test files must have unique names: ${duplicates.keys}",
            duplicates.isEmpty(),
        )
    }
}
