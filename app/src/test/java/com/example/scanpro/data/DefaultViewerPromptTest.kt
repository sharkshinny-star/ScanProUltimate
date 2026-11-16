package com.example.scanpro.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultViewerPromptTest {
    private val due = DefaultViewerPromptState(
        alreadyShown = false,
        pdfOpened = false,
        completedScans = 1,
        alreadyDefault = false,
        onHome = true,
        otherPromptDue = false,
        promptShownThisSession = false
    )

    @Test fun dueOnHomeAfterTheFirstScanOrTheFirstPdfOpened() {
        assertTrue(DefaultViewerPrompt.isDue(due))
        assertTrue(DefaultViewerPrompt.isDue(due.copy(completedScans = 0, pdfOpened = true)))
    }

    @Test fun notAtFirstLaunch() {
        assertFalse(DefaultViewerPrompt.isDue(due.copy(completedScans = 0, pdfOpened = false)))
    }

    @Test fun neverTwiceNorWhenAlreadyDefault() {
        assertFalse(DefaultViewerPrompt.isDue(due.copy(alreadyShown = true)))
        assertFalse(DefaultViewerPrompt.isDue(due.copy(alreadyDefault = true)))
    }

    @Test fun neverStackedWithAnotherPromptOrAwayFromHome() {
        assertFalse(DefaultViewerPrompt.isDue(due.copy(onHome = false)))
        assertFalse(DefaultViewerPrompt.isDue(due.copy(otherPromptDue = true)))
        assertFalse(DefaultViewerPrompt.isDue(due.copy(promptShownThisSession = true)))
    }

    @Test fun promptsAreRememberedSeparately() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(FilesAccessPrompts.importTipShown(context))
        assertFalse(FilesAccessPrompts.defaultViewerShown(context))
        FilesAccessPrompts.markImportTipShown(context)
        FilesAccessPrompts.markPdfOpened(context)
        FilesAccessPrompts.dismissHomeFilesCard(context)
        assertTrue(FilesAccessPrompts.importTipShown(context))
        assertTrue(FilesAccessPrompts.pdfOpened(context))
        assertTrue(FilesAccessPrompts.homeFilesCardDismissed(context))
        assertFalse(FilesAccessPrompts.defaultViewerShown(context))
        assertFalse(FilesAccessPrompts.phonePdfsCardDismissed(context))
    }
}
