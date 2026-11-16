package com.example.scanpro.feature.phonepdfs

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.scanpro.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot: set by Files on this phone just before switching to the Tools tab, taken by the Tools screen, which then
 * runs its usual flow on these files ("merge": the order editor; "img2pdf": the Images to PDF editor; "office": Office
 * to PDF on the first file). Like ToolsFocusRequest, kept in memory only.
 */
object PhoneFilesToTools {
    data class Request(val tool: String, val uris: List<Uri>)

    val pending = MutableStateFlow<Request?>(null)

    const val MERGE = "merge"
    const val IMAGES_TO_PDF = "img2pdf"
    const val OFFICE_TO_PDF = "office"
}

/** Hands [uris] to an app the user picks; read access only, for this share. */
fun shareUris(context: Context, uris: List<Uri>, mimeType: String, chooserTitle: String) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = mimeType
        clipData = ClipData.newRawUri(chooserTitle, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.core_no_app_can_share), Toast.LENGTH_SHORT).show()
    }
}

/** Opens [uri] in another app the user picks (Office and text files, which ScanPro doesn't display itself). */
fun openWith(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.phone_files_no_app), Toast.LENGTH_SHORT).show()
    }
}

/** The MIME type for sharing several files: their own when they all agree, else the shared family ("image/x"), else any. */
fun commonMimeType(files: List<PhoneFile>): String {
    val types = files.map { it.mimeType }.distinct()
    if (types.size == 1) return types.single()
    val families = types.map { it.substringBefore('/') }.distinct()
    return if (families.size == 1) "${families.single()}/*" else "*/*"
}
