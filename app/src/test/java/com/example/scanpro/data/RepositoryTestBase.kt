package com.example.scanpro.data

import android.app.Application
import android.content.Context
import com.example.scanpro.imaging.OcrEngine
import com.example.scanpro.imaging.OcrResult
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** One call to the stand-in page renderer. */
data class Render(
    val file: File,
    val source: File,
    val filter: PageFilter,
    val rotation: Int,
    val crop: CropQuad,
    val adjustments: PageAdjustments,
    val annotations: List<PageAnnotation>
)

/** Text recognition that never touches ML Kit: returns [text], or fails while [failing]. */
class FakeOcr(context: Context) : OcrEngine(context) {
    var text = "fresh text"
    var failing = false
    var calls = 0

    /** Fails only for the files this accepts, so one bad page can be told from the rest. */
    var failFor: (File) -> Boolean = { false }

    /** Thrown as it is from every recognition, e.g. a CancellationException for a job that was stopped. */
    var readFailure: Throwable? = null

    /** Every script the engine was asked to prepare, and an error to throw from it (a model that can't be fetched). */
    val prepared = mutableListOf<OcrScript>()
    var prepareFailure: Throwable? = null

    override suspend fun prepare(script: OcrScript) {
        prepared += script
        prepareFailure?.let { throw it }
    }

    override suspend fun recognizeDetailed(file: File, script: OcrScript): OcrResult {
        calls++
        readFailure?.let { throw it }
        if (failing || failFor(file)) throw IllegalStateException("recognition failed")
        return OcrResult(text, emptyList())
    }
}

/**
 * A real Room database and real files on the JVM, with the native image pipeline and ML Kit replaced by stand-ins
 * that record what they were asked to do. Every page written by [newDocument] has real (text) files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
abstract class RepositoryTestBase {
    protected lateinit var context: Context
    protected lateinit var db: DocumentDatabase
    protected lateinit var repository: DocumentRepository
    protected lateinit var ocr: FakeOcr

    /** Every render, in order, including the re-draws that undo a failed edit. */
    protected val renders = CopyOnWriteArrayList<Render>()

    /** Return an exception to make this render fail. */
    protected var renderFailure: (Render) -> Throwable? = { null }

    @Before fun setUpRepository() {
        context = RuntimeEnvironment.getApplication()
        db = DocumentDatabase(context)
        ocr = FakeOcr(context)
        repository = DocumentRepository(context, db, ocr, SettingsStore(context)).also { repo ->
            repo.pageRenderer = PageRenderer { source, destination, filter, rotation, crop, adjustments, annotations ->
                val render = Render(destination, source, filter, rotation, crop, adjustments, annotations)
                renders += render
                renderFailure(render)?.let { throw it }
                destination.parentFile?.mkdirs()
                destination.writeText("drawn $filter $rotation ${annotations.size}")
            }
            repo.pdfWriter = { _, destination, _ -> destination.writeText("%PDF fake") }
        }
    }

    @After fun tearDownRepository() {
        File(context.filesDir, "documents").deleteRecursively()
    }

    /** Saves a document of [pageCount] pages whose image files really exist. */
    protected fun newDocument(pageCount: Int, title: String = "Doc", trashedAt: Long = 0L): ScanDocument {
        val id = UUID.randomUUID().toString()
        val folder = File(context.filesDir, "documents/$id").apply { mkdirs() }
        File(folder, "source").mkdirs()
        File(folder, "rendered").mkdirs()
        val pages = (0 until pageCount).map { index ->
            val pageId = UUID.randomUUID().toString()
            val source = File(folder, "source/s_$pageId.jpg").apply { writeText("source $index") }
            val image = File(folder, "rendered/p_$pageId.jpg").apply { writeText("page $index") }
            ScanPage(pageId, id, index, source.absolutePath, image.absolutePath, ocrText = "text $index")
        }
        val pdf = File(folder, "document.pdf").apply { writeText("%PDF") }
        val doc = ScanDocument(
            id = id, title = title, createdAt = 1L, updatedAt = 1L, pdfPath = pdf.absolutePath,
            pages = pages, sizeBytes = pdf.length(), isTrashed = trashedAt > 0, trashedAt = trashedAt
        )
        runBlocking(Dispatchers.IO) { db.saveDocument(doc) }
        return doc
    }

    protected fun load(id: String): ScanDocument? = runBlocking { repository.loadDocument(id) }
    protected fun ScanDocument.order() = pages.sortedBy { it.position }.map { it.id }
    protected fun ScanDocument.page(index: Int) = pages.first { it.position == index }
}
