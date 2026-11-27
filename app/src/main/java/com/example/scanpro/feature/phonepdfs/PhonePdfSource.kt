package com.example.scanpro.feature.phonepdfs

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Finds PDFs, images, Office and text files in the phone's shared storage, for "Files on this phone". Read only:
 * nothing here writes, moves or deletes a file outside ScanPro, and nothing leaves the phone.
 *
 * Needs "All files access" (MANAGE_EXTERNAL_STORAGE) on Android 11 and later, or READ_EXTERNAL_STORAGE up to Android
 * 10; the user grants it after an explainer, never at launch. MediaStore's Files collection answers, filtered by MIME
 * type, newest first and capped; a capped walk of the storage is only the fallback below Android 11 when MediaStore
 * has nothing (an unindexed card), never on Android 11 and later, where MediaStore indexes every file.
 */
object PhonePdfSource {
    /** Whether ScanPro may read the files in shared storage right now. Checked again on every resume. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Looks the app up by its uid; treated as "no access" if the system can't answer.
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Whether this build can offer the access at all. A build made with `-Pscanpro.allFilesAccess=false` (for a Play
     * release without the All files access declaration) leaves MANAGE_EXTERNAL_STORAGE out of the manifest, so on
     * Android 11 and later the permission can never be granted and must not be asked for.
     */
    val available: Boolean get() = com.example.scanpro.BuildConfig.ALL_FILES_ACCESS || Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    /** Android 11 and later grant the access on a system settings page; older versions ask with the usual dialog. */
    val usesSettingsPage: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Opens ScanPro's own "All files access" page, else the list of all apps. False when neither opens. */
    fun openAllFilesAccessSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return openAppDetails(context)
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        )
        return intents.any { start(context, it) }
    }

    /** App info, where a storage permission refused with "Don't ask again" can still be turned on. */
    fun openAppDetails(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** The collection every query and the change observer use. */
    val collection: Uri get() = MediaStore.Files.getContentUri("external")

    /** Every listed file found, newest first; empty without access. Runs off the main thread and stops when cancelled. */
    suspend fun load(context: Context): List<PhoneFile> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        val resolver = context.contentResolver
        val documentMimes = PhoneFileKind.entries.filter { it != PhoneFileKind.IMAGE }.flatMap { PhoneFiles.MIME_TYPES.getValue(it) }
        val imageMimes = PhoneFiles.MIME_TYPES.getValue(PhoneFileKind.IMAGE)
        val indexed = runCatching {
            query(resolver, documentMimes, PhoneFiles.DOCUMENT_CAP) + query(resolver, imageMimes, PhoneFiles.IMAGE_CAP)
        }.getOrNull()
        coroutineContext.ensureActive()
        when {
            !indexed.isNullOrEmpty() -> indexed.sortedByDescending { it.modified }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> walk(Environment.getExternalStorageDirectory())
            else -> indexed.orEmpty()
        }
    }

    /**
     * The files MediaStore knows of with one of [mimeTypes], newest first, at most [limit]. With All files access this
     * covers every indexed file, not only those ScanPro made.
     */
    @Suppress("DEPRECATION") // DATA gives the folder before Android 10, which has no RELATIVE_PATH.
    fun query(resolver: ContentResolver, mimeTypes: List<String>, limit: Int): List<PhoneFile> {
        val collection = collection
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            if (hasRelativePath) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IN (${mimeTypes.joinToString(",") { "?" }})"
        val args = mimeTypes.toTypedArray()
        val order = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ takes the limit as a query argument, so MediaStore never builds the rows past it.
            resolver.query(collection, projection, Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }, null)
        } else {
            resolver.query(collection, projection, selection, args, order)
        }
        val result = ArrayList<PhoneFile>()
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val placeCol = it.getColumnIndexOrThrow(projection[5])
            while (it.moveToNext() && result.size < limit) {
                val place = it.getString(placeCol).orEmpty()
                val name = it.getString(nameCol)?.takeIf { n -> n.isNotBlank() }
                    ?: place.substringAfterLast('/').takeIf { n -> n.isNotBlank() }
                    ?: continue
                if (name.startsWith(".")) continue
                val mime = it.getString(mimeCol).orEmpty()
                val kind = PhoneFiles.kindOf(mime, name) ?: continue
                result += PhoneFile(
                    uri = ContentUris.withAppendedId(collection, it.getLong(idCol)),
                    name = name,
                    kind = kind,
                    mimeType = mime.ifBlank { PhoneFiles.mimeForName(name) },
                    size = it.getLong(sizeCol).coerceAtLeast(0),
                    modified = it.getLong(dateCol).coerceAtLeast(0) * 1000,
                    folder = if (hasRelativePath) place.trim('/') else folderFromAbsolute(place)
                )
            }
        }
        return result
    }

    private fun folderFromAbsolute(path: String): String {
        val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull().orEmpty()
        val relative = if (root.isNotEmpty() && path.startsWith(root)) path.removePrefix(root) else path
        return PhoneFiles.folderOf(relative)
    }

    /**
     * Walks [root] when MediaStore has nothing (below Android 11 only): skips hidden folders and other apps'
     * Android/data, stops after [PhoneFiles.WALK_FILE_CAP] entries, and checks for cancellation as it goes.
     */
    suspend fun walk(root: File): List<PhoneFile> {
        val result = ArrayList<PhoneFile>()
        var seen = 0
        val pending = ArrayDeque<File>().apply { add(root) }
        val rootPath = root.absolutePath
        val cap = PhoneFiles.DOCUMENT_CAP + PhoneFiles.IMAGE_CAP
        while (pending.isNotEmpty() && seen < PhoneFiles.WALK_FILE_CAP && result.size < cap) {
            coroutineContext.ensureActive()
            val dir = pending.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (++seen > PhoneFiles.WALK_FILE_CAP) break
                val relative = child.absolutePath.removePrefix(rootPath).trimStart('/')
                if (child.isDirectory) {
                    if (PhoneFiles.shouldEnterDir(relative)) pending.add(child)
                } else if (PhoneFiles.isListedName(child.name) && child.isFile) {
                    result += PhoneFile(
                        uri = Uri.fromFile(child),
                        name = child.name,
                        kind = PhoneFiles.kindOf(null, child.name) ?: continue,
                        mimeType = PhoneFiles.mimeForName(child.name),
                        size = child.length(),
                        modified = child.lastModified(),
                        folder = PhoneFiles.folderOf(relative),
                        path = child.absolutePath
                    )
                    if (result.size >= cap) break
                }
            }
        }
        return result.sortedByDescending { it.modified }
    }

    /** Calls [onChange] (on the main thread) when MediaStore's files change; the returned function stops it. */
    fun observe(context: Context, onChange: () -> Unit): () -> Unit {
        val resolver = context.applicationContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val registered = runCatching { resolver.registerContentObserver(collection, true, observer) }.isSuccess
        return { if (registered) runCatching { resolver.unregisterContentObserver(observer) } }
    }

    /** Whether [path] is in shared storage (where a walk finds files), not in ScanPro's own folders. */
    fun isInSharedStorage(path: String): Boolean {
        val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull() ?: return false
        return path.startsWith("$root/")
    }

    /**
     * A content:// address other apps can be given for [file]. Files from MediaStore already have one; a file found by
     * the walk is first added to MediaStore's index (this only records the existing file, it changes nothing).
     */
    fun shareableUri(context: Context, file: PhoneFile, onReady: (Uri?) -> Unit) {
        val path = file.path
        if (path == null) {
            onReady(file.uri)
            return
        }
        val main = ContextCompat.getMainExecutor(context)
        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), arrayOf(file.mimeType)) { _, uri ->
            main.execute { onReady(uri) }
        }
    }

    /** All of [files] as content:// addresses, in order; files that can't be given one are left out. */
    fun shareableUris(context: Context, files: List<PhoneFile>, onReady: (List<Uri>) -> Unit) {
        if (files.all { it.path == null }) {
            onReady(files.map { it.uri })
            return
        }
        val results = arrayOfNulls<Uri>(files.size)
        var remaining = files.size
        files.forEachIndexed { index, file ->
            shareableUri(context, file) { uri ->
                results[index] = uri
                if (--remaining == 0) onReady(results.filterNotNull())
            }
        }
    }
}
