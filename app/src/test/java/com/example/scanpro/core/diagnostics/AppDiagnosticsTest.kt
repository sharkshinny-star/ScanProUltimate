package com.example.scanpro.core.diagnostics

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.BuildConfig
import com.example.scanpro.R
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppDiagnosticsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun recordEventStoresEntriesInOrder() {
        AppDiagnostics.recordEvent("TestTag", "Diagnostic test event 1")
        AppDiagnostics.recordEvent("TestTag", "Diagnostic test event 2")

        val logs = AppDiagnostics.getInMemoryLogs()
        assertTrue(logs.size >= 2)
        assertTrue(logs.any { it.contains("[TestTag] Diagnostic test event 1") })
        assertTrue(logs.any { it.contains("[TestTag] Diagnostic test event 2") })
    }

    @Test
    fun buildSystemInfoIncludesAppAndHardwareDetails() {
        val info = AppDiagnostics.buildSystemInfo(app)
        assertNotNull(info)
        assertTrue(info.contains("ScanPro ${BuildConfig.VERSION_NAME}"))
        assertTrue(info.contains("[APPLICATION]"))
        assertTrue(info.contains("[DEVICE & PLATFORM]"))
        assertTrue(info.contains("[DISPLAY & ENVIRONMENT]"))
        assertTrue(info.contains("[MEMORY & STORAGE]"))
    }

    @Test
    fun buildFullReportIncludesDelimitersAndSections() {
        AppDiagnostics.recordEvent("ReportTest", "Triggering full report")
        val report = AppDiagnostics.buildFullReport(app)
        assertNotNull(report)
        assertTrue(report.contains("SCANPRO SYSTEM & DIAGNOSTIC REPORT"))
        assertTrue(report.contains("RECENT CRASH / UNCAUGHT EXCEPTION"))
        assertTrue(report.contains("IN-APP EVENT TRAIL"))
        assertTrue(report.contains("END OF REPORT"))
    }

    @Test
    fun generateReportFileCreatesFileInExports() {
        val file = AppDiagnostics.generateReportFile(app)
        assertTrue("Report file must exist", file.exists())
        assertTrue("Report file must have content", file.length() > 0)
        assertEquals("scanpro_issue_report.txt", file.name)
        val text = file.readText()
        assertTrue(text.contains("SCANPRO SYSTEM & DIAGNOSTIC REPORT"))
    }

    @Test
    fun contactSupportWithIssueGeneratesReportAndDelivers() {
        val delivery = contactSupport(app, SupportTopic.ISSUE)
        assertNotNull(delivery)
        val exportFile = File(app.cacheDir, "exports/scanpro_issue_report.txt")
        assertTrue("Exported diagnostic report file must exist after reporting an issue", exportFile.exists())
        val content = exportFile.readText()
        assertTrue(content.contains("ScanPro ${BuildConfig.VERSION_NAME}"))
    }

    @Test
    fun reportIssueSubtitleMentionsAppLogs() {
        assertEquals("Report an issue", app.getString(R.string.settings_report_issue_title))
        val subtitle = app.getString(R.string.settings_report_issue_subtitle)
        assertTrue("Subtitle should mention app logs", subtitle.contains("App logs"))
    }
}
