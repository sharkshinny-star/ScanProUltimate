package com.example.scanpro.feature.document

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanPage
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.intelligence.BusinessCard
import com.example.scanpro.intelligence.DocumentIntelligenceEngine
import com.example.scanpro.intelligence.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The "scan saved" sheet: smart action by document type, one-tap Copy text / Add pages, Scan another. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ScanSavedSheetTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val receiptText = "BLUE BOTTLE COFFEE\nLatte 4.50\nCroissant 3.20\nSubtotal 7.70\nTax 0.67\nTotal $8.37\nThank you\n03/10/2026"

    private fun document(text: String) = ScanDocument(
        id = "d1", title = "Receipt Blue Bottle Coffee", createdAt = 0L, updatedAt = 0L, pdfPath = "",
        pages = listOf(ScanPage("p1", "d1", 0, "", "", ocrText = text)), sizeBytes = 182_000L
    )

    private val copied = mutableListOf<String>()
    private var addedPages = 0
    private var scannedAnother = 0
    private var dismissed = 0

    private fun show(document: ScanDocument, highlight: ScanHighlight?) = compose.setContent {
        ScanProTheme(themeMode = ThemeMode.LIGHT) {
            ScanSavedSheet(
                document = document, busy = false, onRename = {},
                onSharePdf = {}, onShareJpg = {}, onSaveToDevice = {}, onSaveToDrive = {}, onPrint = {},
                onDismiss = { dismissed++ },
                highlight = highlight,
                onCopy = { copied += it },
                onAddPages = { addedPages++ },
                onScanAnother = { scannedAnother++ }
            )
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun aReceiptShowsItsTotalAndCopiesIt() {
        val insights = DocumentIntelligenceEngine.analyze(receiptText)
        val highlight = scanHighlight(insights, null, receiptText)
        assertEquals(ScanHighlight.Bill(DocumentKind.RECEIPT, "$8.37", "03/10/2026"), highlight)
        show(document(receiptText), highlight)
        compose.onNodeWithText(app.getString(R.string.doc_saved_total, "$8.37"), substring = true).assertExists()
        compose.onNodeWithText(app.getString(R.string.doc_searchable_pdf)).assertExists()
        capture("scan_saved_receipt")
        compose.onNodeWithText(app.getString(R.string.doc_saved_copy_total)).performClick()
        assertEquals(listOf("$8.37"), copied)
        compose.onNodeWithText(app.getString(R.string.doc_saved_copy_text)).performClick()
        assertEquals(receiptText, copied.last())
        assertEquals("copying keeps the sheet open", 0, dismissed)
    }

    @Test
    fun aBusinessCardOffersAddToContacts() {
        val card = BusinessCard(name = "Priya Raman", company = "Acme Labs", phones = listOf("+91 98450 12345"))
        val highlight = scanHighlight(DocumentIntelligenceEngine.analyze("x"), card, "Priya Raman\nAcme Labs")
        assertEquals(ScanHighlight.Contact("Priya Raman", "Acme Labs"), highlight)
        show(document("Priya Raman\nAcme Labs\n+91 98450 12345"), highlight)
        compose.onNodeWithText("Priya Raman · Acme Labs").assertExists()
        compose.onNodeWithText(app.getString(R.string.doc_add_to_contacts)).assertExists()
        capture("scan_saved_card")
    }

    @Test
    fun scanAnotherAndAddPagesLeaveTheSheet() {
        show(document("Some letter text"), null)
        compose.onAllNodesWithText(app.getString(R.string.doc_saved_copy_total)).assertCountEquals(0)
        compose.onNodeWithText(app.getString(R.string.doc_saved_add_pages)).performClick()
        assertEquals(1, addedPages)
        assertEquals(1, dismissed)
    }

    @Test
    fun scanAnother() {
        show(document(""), null)
        // No text read: nothing to search or copy, so no Searchable badge.
        compose.onAllNodesWithText(app.getString(R.string.doc_searchable_pdf)).assertCountEquals(0)
        compose.onNodeWithText(app.getString(R.string.scan_code_scan_another)).performScrollTo().performClick()
        assertEquals(1, scannedAnother)
        assertEquals(1, dismissed)
    }

    @Test
    fun highlightRules() {
        // A plain letter, or a receipt with no total line, gets no smart action.
        assertNull(scanHighlight(DocumentIntelligenceEngine.analyze("Dear Sir, thank you for your letter."), null, "Dear Sir"))
        val noTotal = "Receipt\nCash\nThank you"
        assertNull(scanHighlight(DocumentIntelligenceEngine.analyze(noTotal), null, noTotal))
        // The grand total wins over a subtotal; amount due counts.
        assertEquals("1,250.00", DocumentIntelligenceEngine.total("Subtotal 1,100.00\nGST 150.00\nGrand Total 1,250.00"))
        assertEquals("₹ 540", DocumentIntelligenceEngine.total("Items 3\nAmount due ₹ 540"))
        assertTrue(DocumentIntelligenceEngine.total("Sub-total 9.99") == null)
    }
}
