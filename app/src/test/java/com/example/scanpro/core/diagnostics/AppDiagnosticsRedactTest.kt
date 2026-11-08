package com.example.scanpro.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A support report must not carry the app's folder paths, other apps' links or exported file names. */
class AppDiagnosticsRedactTest {
    private val paths = listOf("/data/user/0/com.example.scanpro/files", "/data/user/0/com.example.scanpro/cache")
    private val pkg = "com.example.scanpro"

    @Test
    fun appFolderPathsAreReplaced() {
        val out = AppDiagnostics.redactPaths("failed: /data/user/0/com.example.scanpro/files/documents/abc/page.jpg", paths, pkg)
        assertEquals("failed: <app>/documents/abc/page.jpg", out)
    }

    @Test
    fun exportedFileNamesComeFromTitlesSoTheyAreHidden() {
        val out = AppDiagnostics.redactPaths("wrote /data/user/0/com.example.scanpro/cache/exports/Passport scan.pdf done", paths, pkg)
        assertTrue(out.contains("exports/<file>"))
        assertFalse(out.contains("Passport"))
    }

    @Test
    fun otherAppsLinksAreHidden() {
        val out = AppDiagnostics.redactPaths("opened content://com.android.providers.media/document/1234 ok", paths, pkg)
        assertEquals("opened <content-uri> ok", out)
    }

    @Test
    fun otherUsersDataFolderIsReplacedToo() {
        val out = AppDiagnostics.redactPaths("/data/user/10/com.example.scanpro/databases/x", paths, pkg)
        assertEquals("<app>/databases/x", out)
    }

    @Test
    fun plainTextIsUntouched() {
        assertEquals("Heap Memory: 40MB used", AppDiagnostics.redactPaths("Heap Memory: 40MB used", paths, pkg))
    }
}
