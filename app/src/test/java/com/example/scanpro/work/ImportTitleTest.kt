package com.example.scanpro.work

import android.app.Application
import android.net.Uri
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The name typed on the review screen travels with the scan to the worker, whichever way the request is stored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportTitleTest {
    private fun request(title: String?) = ImportScanWorker.inputFor(
        listOf(Uri.parse("file:///data/user/0/com.scanpro.app/files/pro_scanner_session/s1/raw_1.jpg")),
        ScanMode.DOCUMENT, listOf(CropQuad.DEFAULT), listOf(PageFilter.AUTO), title = title
    )

    @Test
    fun `a typed name is trimmed and kept in WorkManager data and in the saved JSON`() {
        val input = request("  Tax return 2026 ")
        assertEquals("Tax return 2026", input.getString(ImportScanWorker.KEY_TITLE))
        assertEquals("Tax return 2026", ImportRequest.fromData(input.toWorkData()!!).getString(ImportScanWorker.KEY_TITLE))
        assertEquals("Tax return 2026", ImportRequest.fromJson(input.toJson()).getString(ImportScanWorker.KEY_TITLE))
    }

    @Test
    fun `no name or a blank one leaves the title to the automatic naming`() {
        assertNull(request(null).getString(ImportScanWorker.KEY_TITLE))
        assertNull(request("   ").getString(ImportScanWorker.KEY_TITLE))
    }
}
