package com.example.scanpro.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.Embedded
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    @ColumnInfo(name = "ocr_edited", defaultValue = "0") val ocrEdited: Boolean = false,
    @ColumnInfo(name = "ocr_words_json", defaultValue = "") val ocrWordsJson: String = "",
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

/** A document row with its page count and first page, for lists; see [DocumentSummary]. */
internal data class DocumentSummaryRow(
    @Embedded val document: DocumentEntity,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "cover_path") val coverPath: String?
)

private const val SUMMARY_COLUMNS = "d.*, " +
    "(SELECT COUNT(*) FROM pages p WHERE p.document_id = d.id) AS page_count, " +
    "(SELECT p.image_path FROM pages p WHERE p.document_id = d.id ORDER BY p.position ASC LIMIT 1) AS cover_path"

@Dao
internal interface ScanDao {
    @Query("SELECT * FROM documents WHERE trashed_at = 0 ORDER BY updated_at DESC")
    fun loadActiveDocuments(): List<DocumentEntity>

    @Query("SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 ORDER BY d.updated_at DESC")
    fun loadActiveSummaries(): List<DocumentSummaryRow>

    @Query("SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at > 0 ORDER BY d.trashed_at DESC")
    fun loadTrashSummaries(): List<DocumentSummaryRow>

    @Query("SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.id IN (:ids)")
    fun loadSummaries(ids: List<String>): List<DocumentSummaryRow>

    // The Files list, paged. An empty folder means every folder; the folder name compares ignoring case,
    // like the folder chips do. The direction is a parameter: one CASE orders ascending, the other descending,
    // and whichever does not apply is NULL for every row (R35).
    @Query(
        "SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 " +
            "AND (:folder = '' OR TRIM(d.folder_name) = :folder COLLATE NOCASE) AND (:favoritesOnly = 0 OR d.is_favorite = 1) " +
            "AND (:tag = '' OR LOWER(d.tags_json) LIKE '%\"' || LOWER(:tag) || '\"%') " +
            "ORDER BY (CASE WHEN :ascending = 1 THEN d.updated_at END) ASC, (CASE WHEN :ascending = 0 THEN d.updated_at END) DESC, d.id"
    )
    fun pagedByUpdated(folder: String, favoritesOnly: Boolean, tag: String, ascending: Boolean): PagingSource<Int, DocumentSummaryRow>

    @Query(
        "SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 " +
            "AND (:folder = '' OR TRIM(d.folder_name) = :folder COLLATE NOCASE) AND (:favoritesOnly = 0 OR d.is_favorite = 1) " +
            "AND (:tag = '' OR LOWER(d.tags_json) LIKE '%\"' || LOWER(:tag) || '\"%') " +
            "ORDER BY (CASE WHEN :ascending = 1 THEN d.created_at END) ASC, (CASE WHEN :ascending = 0 THEN d.created_at END) DESC, d.id"
    )
    fun pagedByCreated(folder: String, favoritesOnly: Boolean, tag: String, ascending: Boolean): PagingSource<Int, DocumentSummaryRow>

    @Query(
        "SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 " +
            "AND (:folder = '' OR TRIM(d.folder_name) = :folder COLLATE NOCASE) AND (:favoritesOnly = 0 OR d.is_favorite = 1) " +
            "AND (:tag = '' OR LOWER(d.tags_json) LIKE '%\"' || LOWER(:tag) || '\"%') " +
            "ORDER BY (CASE WHEN :ascending = 1 THEN d.title END) COLLATE NOCASE ASC, (CASE WHEN :ascending = 0 THEN d.title END) COLLATE NOCASE DESC, d.id"
    )
    fun pagedByName(folder: String, favoritesOnly: Boolean, tag: String, ascending: Boolean): PagingSource<Int, DocumentSummaryRow>

    @Query(
        "SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 " +
            "AND (:folder = '' OR TRIM(d.folder_name) = :folder COLLATE NOCASE) AND (:favoritesOnly = 0 OR d.is_favorite = 1) " +
            "AND (:tag = '' OR LOWER(d.tags_json) LIKE '%\"' || LOWER(:tag) || '\"%') " +
            "ORDER BY (CASE WHEN :ascending = 1 THEN d.size_bytes END) ASC, (CASE WHEN :ascending = 0 THEN d.size_bytes END) DESC, d.id"
    )
    fun pagedBySize(folder: String, favoritesOnly: Boolean, tag: String, ascending: Boolean): PagingSource<Int, DocumentSummaryRow>

    @Query("SELECT COUNT(*) FROM documents WHERE trashed_at = 0")
    fun countActive(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE trashed_at = 0 AND is_favorite = 1")
    fun countFavorites(): Int

    @Query("SELECT DISTINCT TRIM(folder_name) FROM documents WHERE trashed_at = 0 AND TRIM(folder_name) != ''")
    fun loadFolderNames(): List<String>

    @Query("SELECT DISTINCT tags_json FROM documents WHERE trashed_at = 0 AND tags_json != '' AND tags_json != '[]'")
    fun loadTagsJson(): List<String>

    @Query("SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 ORDER BY d.updated_at DESC, d.id LIMIT :limit")
    fun loadRecentSummaries(limit: Int): List<DocumentSummaryRow>

    @Query("SELECT $SUMMARY_COLUMNS FROM documents d WHERE d.trashed_at = 0 AND d.id != :excludeId ORDER BY d.updated_at DESC, d.id LIMIT :limit")
    fun loadOtherSummaries(excludeId: String, limit: Int): List<DocumentSummaryRow>

    @Query("SELECT updated_at AS updatedAt, size_bytes AS sizeBytes FROM documents WHERE id = :id AND trashed_at = 0 LIMIT 1")
    fun loadActiveStamp(id: String): DocumentStamp?

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

    // Targeted UPDATEs, never REPLACE (which deletes the row and cascade-deletes its pages). Each writer
    // touches only its own columns, so a background PDF rebuild and a rename can't overwrite each other.
    @Query(
        "UPDATE documents SET title = :title, updated_at = :updatedAt, is_favorite = :isFavorite, " +
            "tags_json = :tagsJson, folder_name = :folderName, scan_mode = :scanMode, trashed_at = :trashedAt " +
            "WHERE id = :id"
    )
    fun updateMetadata(
        id: String,
        title: String,
        updatedAt: Long,
        isFavorite: Boolean,
        tagsJson: String,
        folderName: String,
        scanMode: String,
        trashedAt: Long
    )

    @Query("UPDATE documents SET size_bytes = :sizeBytes WHERE id = :id")
    fun updateSize(id: String, sizeBytes: Long)

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

    /** FTS4 `snippet()`: about a dozen words of page text (column 2, `content`) around the first match. */
    @Query(
        "SELECT snippet(page_fts, :open, :close, '…', 2, 12) FROM page_fts " +
            "WHERE page_fts MATCH :ftsQuery AND document_id = :documentId LIMIT 1"
    )
    fun ocrSnippet(ftsQuery: String, documentId: String, open: String, close: String): String?

    @Query(
        "SELECT * FROM documents WHERE trashed_at = 0 AND " +
            "(LOWER(title) LIKE '%' || LOWER(:query) || '%' ESCAPE '\\' OR " +
            "LOWER(folder_name) LIKE '%' || LOWER(:query) || '%' ESCAPE '\\' OR " +
            "LOWER(tags_json) LIKE '%' || LOWER(:query) || '%' ESCAPE '\\') " +
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
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
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
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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

    fun loadSummaries(): List<DocumentSummary> = dao.loadActiveSummaries().map { it.toSummary() }

    /**
     * The Files list as pages of summaries, straight from the database: it stays current by itself, because
     * Room invalidates the source whenever the documents or pages tables change.
     */
    fun pagedSummaries(folder: String, favoritesOnly: Boolean, sort: LibrarySort, ascending: Boolean = sort.defaultAscending, tag: String = ""): Flow<PagingData<DocumentSummary>> =
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 30, enablePlaceholders = false)) {
            // The tag is matched as a whole JSON string, quotes included, so "tax" does not match "taxi".
            val wantedTag = JsonCodec.encodeStringList(listOf(tag.trim())).removePrefix("[").removeSuffix("]").removeSurrounding("\"").takeIf { tag.isNotBlank() }.orEmpty()
            when (sort) {
                LibrarySort.UPDATED -> dao.pagedByUpdated(folder.trim(), favoritesOnly, wantedTag, ascending)
                LibrarySort.CREATED -> dao.pagedByCreated(folder.trim(), favoritesOnly, wantedTag, ascending)
                LibrarySort.NAME -> dao.pagedByName(folder.trim(), favoritesOnly, wantedTag, ascending)
                LibrarySort.SIZE -> dao.pagedBySize(folder.trim(), favoritesOnly, wantedTag, ascending)
            }
        }.flow.map { data -> data.map { row -> row.toSummary() } }

    fun loadOverview(recentLimit: Int): LibraryOverview = room.runInTransaction<LibraryOverview> {
        LibraryOverview(
            documentCount = dao.countActive(),
            favoriteCount = dao.countFavorites(),
            // One entry per folder whatever the case of its name, as the filter itself ignores case.
            folders = dao.loadFolderNames().distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            tags = dao.loadTagsJson().flatMap { JsonCodec.decodeStringList(it) }.map { it.trim() }.filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            recent = dao.loadRecentSummaries(recentLimit).map { it.toSummary() }
        )
    }

    fun loadOtherSummaries(excludeId: String, limit: Int): List<DocumentSummary> =
        dao.loadOtherSummaries(excludeId, limit).map { it.toSummary() }

    fun loadActiveStamp(id: String): DocumentStamp? = dao.loadActiveStamp(id)

    fun loadTrashSummaries(): List<DocumentSummary> = dao.loadTrashSummaries().map { it.toSummary() }

    /** Summaries for [ids], in the same order (ids without a document are skipped). */
    private fun loadSummaries(ids: List<String>): List<DocumentSummary> {
        val byId = ids.chunked(SQL_VARIABLE_CHUNK).flatMap(dao::loadSummaries).associateBy { it.document.id }
        return ids.mapNotNull { byId[it]?.toSummary() }
    }

    fun loadDocument(id: String): ScanDocument? = dao.loadDocument(id)?.let(::hydrate)

    /** Saves document-level fields only (title, tags, folder, favourite, mode, trash state); pages and PDF size are untouched. */
    fun saveDocumentMetadata(document: ScanDocument) {
        val entity = document.toEntity()
        dao.updateMetadata(
            id = entity.id,
            title = entity.title,
            updatedAt = entity.updatedAt,
            isFavorite = entity.isFavorite,
            tagsJson = entity.tagsJson,
            folderName = entity.folderName,
            scanMode = entity.scanMode,
            trashedAt = entity.trashedAt
        )
    }

    fun updateSize(id: String, sizeBytes: Long) = dao.updateSize(id, sizeBytes)

    fun loadOcrScript(id: String): OcrScript? =
        dao.loadOcrScript(id)?.let { runCatching { OcrScript.valueOf(it) }.getOrNull() }

    fun deleteDocument(id: String) {
        room.runInTransaction {
            dao.deleteFtsForDocument(id)
            dao.deleteDocument(id)
        }
    }

    /**
     * Every word of the query must be found somewhere in the document: in its title, folder or tags, or in
     * the text of any of its pages. Words may be on different pages, and a query can mix a title word with
     * a page-text word.
     */
    fun search(query: String): List<DocumentSummary> {
        val clean = query.trim()
        if (clean.isBlank()) return loadSummaries()
        val words = clean.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.take(8)
        return room.runInTransaction<List<DocumentSummary>> {
            var matching: Set<String>? = null
            // Documents found by a word that is only in their page text, with that word's query: the result shows
            // the text around it, or it would be unclear why the document matched.
            val textOnly = HashMap<String, String>()
            for (word in words) {
                val ids = HashSet<String>()
                // Escape LIKE wildcards so typing % or _ searches for those characters instead of matching everything.
                dao.searchMetadata(word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
                    .filter { entity -> entity.title.contains(word, true) || entity.folderName.contains(word, true) ||
                        JsonCodec.decodeStringList(entity.tagsJson).any { it.contains(word, true) } }
                    .forEach { ids += it.id }
                val inMetadata = HashSet(ids)
                buildFtsQuery(word)?.let { safeQuery ->
                    runCatching { dao.searchOcrDocumentIds(safeQuery) }.getOrDefault(emptyList()).forEach { id ->
                        ids += id
                        if (id !in inMetadata && id !in textOnly) textOnly[id] = safeQuery
                    }
                }
                matching = matching?.intersect(ids) ?: ids
                if (matching.isEmpty()) break
            }
            val found = matching.orEmpty()
            if (found.isEmpty()) return@runInTransaction emptyList()
            val entities = found.toList().chunked(SQL_VARIABLE_CHUNK)
                .flatMap(dao::loadDocuments)
                .filter { it.trashedAt == 0L }
            loadSummaries(entities.sortedByDescending { it.updatedAt }.map { it.id }).map { summary ->
                val ftsQuery = textOnly[summary.id] ?: return@map summary
                val snippet = runCatching { dao.ocrSnippet(ftsQuery, summary.id, SNIPPET_MATCH_START, SNIPPET_MATCH_END) }.getOrNull()
                    ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
                summary.copy(matchSnippet = snippet)
            }
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
        // FTS4 prefix queries are `term*` without quotes ("term"* never matches), and limiting each term to
        // the content column keeps short prefixes from matching document/page id fragments. Tokens are
        // letters and digits only, so they need no further escaping. Space-separated terms are ANDed.
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "content:$it*" }
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

private fun DocumentSummaryRow.toSummary() = DocumentSummary(
    id = document.id,
    title = document.title,
    createdAt = document.createdAt,
    updatedAt = document.updatedAt,
    pdfPath = document.pdfPath,
    pageCount = pageCount,
    coverPath = coverPath,
    sizeBytes = document.sizeBytes,
    isFavorite = document.isFavorite,
    tags = JsonCodec.decodeStringList(document.tagsJson),
    folderName = document.folderName,
    scanMode = enumValueOrDefault(document.scanMode, ScanMode.DOCUMENT),
    isTrashed = document.trashedAt > 0L,
    trashedAt = document.trashedAt
)

private fun ScanPage.toEntity() = PageEntity(
    id = id,
    documentId = documentId,
    position = position,
    sourcePath = sourcePath,
    imagePath = imagePath,
    ocrText = ocrText,
    ocrEdited = ocrEdited,
    ocrWordsJson = if (ocrEdited) "" else JsonCodec.encodeWords(ocrWords),
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
    ocrEdited = ocrEdited,
    ocrWords = JsonCodec.decodeWords(ocrWordsJson),
    filter = enumValueOrDefault(filter, PageFilter.ORIGINAL),
    rotationDegrees = rotationDegrees,
    crop = JsonCodec.decodeCrop(cropJson),
    adjustments = JsonCodec.decodeAdjustments(adjustmentsJson),
    annotations = JsonCodec.decodeAnnotations(annotationsJson)
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
