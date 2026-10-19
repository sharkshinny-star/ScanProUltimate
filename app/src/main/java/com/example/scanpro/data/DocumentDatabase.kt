package com.example.scanpro.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Stay under SQLite's historical 999 bound-variable limit for IN (...) queries.
private const val SQL_VARIABLE_CHUNK = 900

@Entity(tableName = "documents")
data class DocumentEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "pdf_path") val pdfPath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "tags_json") val tagsJson: String,
    @ColumnInfo(name = "folder_name") val folderName: String,
    @ColumnInfo(name = "pdf_quality") val pdfQuality: String,
    @ColumnInfo(name = "pdf_page_size") val pdfPageSize: String,
    @ColumnInfo(name = "searchable_pdf") val searchablePdf: Boolean,
    @ColumnInfo(name = "page_numbers") val pageNumbers: Boolean,
    @ColumnInfo(name = "scan_mode") val scanMode: String,
    @ColumnInfo(name = "ocr_script") val ocrScript: String,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["document_id"]), Index(value = ["document_id", "position"], unique = true)]
)
data class PageEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val position: Int,
    @ColumnInfo(name = "source_path") val sourcePath: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "ocr_text") val ocrText: String,
    val filter: String,
    @ColumnInfo(name = "rotation_degrees") val rotationDegrees: Int,
    @ColumnInfo(name = "crop_json") val cropJson: String,
    @ColumnInfo(name = "adjustments_json") val adjustmentsJson: String,
    @ColumnInfo(name = "annotations_json") val annotationsJson: String
)

/**
 * Separate FTS table keeps OCR search fast even with thousands of pages.
 * We intentionally mirror page/document ids instead of relying on an external-content
 * FTS table so updates remain explicit and easy to reason about.
 */
@Fts4
@Entity(tableName = "page_fts")
data class PageFtsEntity(
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "page_id") val pageId: String,
    val content: String
)

@Dao
internal interface ScanDao {
    @Query("SELECT * FROM documents WHERE trashed_at = 0 ORDER BY updated_at DESC")
    fun loadActiveDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE trashed_at > 0 ORDER BY trashed_at DESC")
    fun loadTrashDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun loadDocument(id: String): DocumentEntity?

    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY position ASC")
    fun loadPages(documentId: String): List<PageEntity>

    @Query("SELECT * FROM documents WHERE id IN (:ids)")
    fun loadDocuments(ids: List<String>): List<DocumentEntity>

    @Query("SELECT * FROM pages WHERE document_id IN (:documentIds) ORDER BY document_id, position ASC")
    fun loadPagesForDocuments(documentIds: List<String>): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putDocument(document: DocumentEntity)

    // A real UPDATE, not REPLACE: REPLACE deletes the row first, which would cascade-delete its pages.
    @Update
    fun updateDocument(document: DocumentEntity)

    @Query("SELECT ocr_script FROM documents WHERE id = :id LIMIT 1")
    fun loadOcrScript(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putPages(pages: List<PageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putFtsRows(rows: List<PageFtsEntity>)

    @Query("DELETE FROM pages WHERE document_id = :documentId")
    fun deletePagesForDocument(documentId: String)

    @Query("DELETE FROM page_fts WHERE document_id = :documentId")
    fun deleteFtsForDocument(documentId: String)

    @Query("DELETE FROM documents WHERE id = :id")
    fun deleteDocument(id: String)

    @Query("SELECT DISTINCT document_id FROM page_fts WHERE page_fts MATCH :ftsQuery")
    fun searchOcrDocumentIds(ftsQuery: String): List<String>

    @Query(
        "SELECT * FROM documents WHERE trashed_at = 0 AND " +
            "(LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(folder_name) LIKE '%' || LOWER(:query) || '%' OR LOWER(tags_json) LIKE '%' || LOWER(:query) || '%') " +
            "ORDER BY updated_at DESC"
    )
    fun searchMetadata(query: String): List<DocumentEntity>

    @Transaction
    fun replaceDocument(document: DocumentEntity, pages: List<PageEntity>, ftsRows: List<PageFtsEntity>) {
        putDocument(document)
        deletePagesForDocument(document.id)
        deleteFtsForDocument(document.id)
        if (pages.isNotEmpty()) putPages(pages)
        if (ftsRows.isNotEmpty()) putFtsRows(ftsRows)
    }
}

@Database(
    entities = [DocumentEntity::class, PageEntity::class, PageFtsEntity::class],
    version = 1,
    exportSchema = true
)
internal abstract class ScanRoomDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}

/**
 * Small Room-backed facade that preserves the repository API while giving the app
 * transactions, foreign keys, schema validation and FTS-backed OCR search.
 * All calls are expected from Dispatchers.IO.
 */
@Singleton
class DocumentDatabase @Inject constructor(
    @ApplicationContext context: Context
) {
    private val room = Room.databaseBuilder(
        context,
        ScanRoomDatabase::class.java,
        "scanpro-room.db"
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    private val dao = room.scanDao()

    fun saveDocument(document: ScanDocument) {
        val entity = document.toEntity()
        val pageEntities = document.pages.sortedBy { it.position }.map { it.toEntity() }
        val ftsRows = pageEntities.map {
            PageFtsEntity(
                documentId = it.documentId,
                pageId = it.id,
                content = it.ocrText
            )
        }
        dao.replaceDocument(entity, pageEntities, ftsRows)
    }

    fun loadAll(includeTrashed: Boolean = false): List<ScanDocument> = room.runInTransaction<List<ScanDocument>> {
        val docs = if (includeTrashed) {
            dao.loadActiveDocuments() + dao.loadTrashDocuments()
        } else {
            dao.loadActiveDocuments()
        }
        hydrateAll(docs)
    }

    fun loadTrash(): List<ScanDocument> = room.runInTransaction<List<ScanDocument>> {
        hydrateAll(dao.loadTrashDocuments())
    }

    fun loadDocument(id: String): ScanDocument? = dao.loadDocument(id)?.let(::hydrate)

    /** Saves document-level fields only (title, tags, folder, favorite, trash state); pages are untouched. */
    fun saveDocumentMetadata(document: ScanDocument) = dao.updateDocument(document.toEntity())

    fun loadOcrScript(id: String): OcrScript? =
        dao.loadOcrScript(id)?.let { runCatching { OcrScript.valueOf(it) }.getOrNull() }

    fun deleteDocument(id: String) {
        room.runInTransaction(Runnable {
            dao.deleteFtsForDocument(id)
            dao.deleteDocument(id)
        })
    }

    fun search(query: String): List<ScanDocument> {
        val clean = query.trim()
        if (clean.isBlank()) return loadAll()
        return room.runInTransaction<List<ScanDocument>> {
            val metadata = dao.searchMetadata(clean)
            val fts = buildFtsQuery(clean)?.let { safeQuery ->
                runCatching { dao.searchOcrDocumentIds(safeQuery) }.getOrDefault(emptyList())
            }.orEmpty().toSet()

            val byId = LinkedHashMap<String, DocumentEntity>()
            metadata.forEach { byId[it.id] = it }
            val ftsOnly = fts.filterNot { it in byId }
            ftsOnly.chunked(SQL_VARIABLE_CHUNK)
                .flatMap(dao::loadDocuments)
                .filter { it.trashedAt == 0L }
                .forEach { byId[it.id] = it }
            hydrateAll(byId.values.sortedByDescending { it.updatedAt })
        }
    }

    private fun hydrate(entity: DocumentEntity): ScanDocument = entity.toModel(
        pages = dao.loadPages(entity.id).map { it.toModel() }
    )

    /** Loads pages for many documents in batched IN queries instead of one query per document. */
    private fun hydrateAll(entities: List<DocumentEntity>): List<ScanDocument> {
        if (entities.isEmpty()) return emptyList()
        val pagesByDocument = entities.map { it.id }
            .chunked(SQL_VARIABLE_CHUNK)
            .flatMap(dao::loadPagesForDocuments)
            .groupBy({ it.documentId }, { it.toModel() })
        return entities.map { it.toModel(pages = pagesByDocument[it.id].orEmpty()) }
    }

    private fun buildFtsQuery(raw: String): String? {
        val tokens = raw.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .take(8)
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" AND ") { "\"${it.replace("\"", "") }\"*" }
    }
}

private fun ScanDocument.toEntity() = DocumentEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pdfPath = pdfPath,
    sizeBytes = sizeBytes,
    isFavorite = isFavorite,
    tagsJson = JsonCodec.encodeStringList(tags),
    folderName = folderName,
    pdfQuality = pdfQuality.name,
    pdfPageSize = pdfPageSize.name,
    searchablePdf = searchablePdf,
    pageNumbers = pageNumbers,
    scanMode = scanMode.name,
    ocrScript = ocrScript.name,
    trashedAt = if (isTrashed && trashedAt == 0L) System.currentTimeMillis() else trashedAt
)

private fun DocumentEntity.toModel(pages: List<ScanPage>) = ScanDocument(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pdfPath = pdfPath,
    pages = pages,
    sizeBytes = sizeBytes,
    isFavorite = isFavorite,
    tags = JsonCodec.decodeStringList(tagsJson),
    folderName = folderName,
    pdfQuality = enumValueOrDefault(pdfQuality, PdfQuality.BALANCED),
    pdfPageSize = enumValueOrDefault(pdfPageSize, PdfPageSize.A4),
    searchablePdf = searchablePdf,
    pageNumbers = pageNumbers,
    scanMode = enumValueOrDefault(scanMode, ScanMode.DOCUMENT),
    ocrScript = enumValueOrDefault(ocrScript, OcrScript.LATIN),
    isTrashed = trashedAt > 0L,
    trashedAt = trashedAt
)

private fun ScanPage.toEntity() = PageEntity(
    id = id,
    documentId = documentId,
    position = position,
    sourcePath = sourcePath,
    imagePath = imagePath,
    ocrText = ocrText,
    filter = filter.name,
    rotationDegrees = rotationDegrees,
    cropJson = JsonCodec.encodeCrop(crop),
    adjustmentsJson = JsonCodec.encodeAdjustments(adjustments),
    annotationsJson = JsonCodec.encodeAnnotations(annotations)
)

private fun PageEntity.toModel() = ScanPage(
    id = id,
    documentId = documentId,
    position = position,
    sourcePath = sourcePath,
    imagePath = imagePath,
    ocrText = ocrText,
    filter = enumValueOrDefault(filter, PageFilter.ORIGINAL),
    rotationDegrees = rotationDegrees,
    crop = JsonCodec.decodeCrop(cropJson),
    adjustments = JsonCodec.decodeAdjustments(adjustmentsJson),
    annotations = JsonCodec.decodeAnnotations(annotationsJson)
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
