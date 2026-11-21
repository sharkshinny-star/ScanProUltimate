package com.example.scanpro.data

import android.content.Context
import androidx.core.content.edit

/**
 * One-time prompts around files (kept in SharedPreferences and read synchronously, like [FirstRunHints], so a sheet
 * is decided in the same frame as the screen and never pops in late):
 * - the "Set ScanPro as your default PDF viewer" sheet on Home;
 * - the "How to import files" tip before the system file picker;
 * - the "Files on this phone" access cards on Home and in Files, until closed (the list stays in the Files menu).
 * Each is remembered on every way out (buttons, Back, a tap outside), so none of them nags.
 */
object FilesAccessPrompts {
    private const val PREFS = "files_access_prompts"
    private const val KEY_DEFAULT_VIEWER_SHOWN = "default_viewer_sheet_shown"
    private const val KEY_IMPORT_TIP_SHOWN = "import_tip_shown"
    private const val KEY_PHONE_PDFS_CARD_DISMISSED = "phone_pdfs_card_dismissed"
    private const val KEY_PDF_OPENED = "pdf_opened"
    private const val KEY_HOME_FILES_CARD_DISMISSED = "home_files_card_dismissed"
    private const val KEY_START_FILES_ASKED = "start_files_asked"

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun defaultViewerShown(context: Context) = prefs(context).getBoolean(KEY_DEFAULT_VIEWER_SHOWN, false)
    fun markDefaultViewerShown(context: Context) = prefs(context).edit { putBoolean(KEY_DEFAULT_VIEWER_SHOWN, true) }

    fun importTipShown(context: Context) = prefs(context).getBoolean(KEY_IMPORT_TIP_SHOWN, false)
    fun markImportTipShown(context: Context) = prefs(context).edit { putBoolean(KEY_IMPORT_TIP_SHOWN, true) }

    fun phonePdfsCardDismissed(context: Context) = prefs(context).getBoolean(KEY_PHONE_PDFS_CARD_DISMISSED, false)
    fun dismissPhonePdfsCard(context: Context) = prefs(context).edit { putBoolean(KEY_PHONE_PDFS_CARD_DISMISSED, true) }

    /** The same card on Home, closed separately: closing one doesn't hide the other. */
    fun homeFilesCardDismissed(context: Context) = prefs(context).getBoolean(KEY_HOME_FILES_CARD_DISMISSED, false)
    fun dismissHomeFilesCard(context: Context) = prefs(context).edit { putBoolean(KEY_HOME_FILES_CARD_DISMISSED, true) }

    /**
     * "All files access" has been asked for at start (F196): on the welcome's permission page, or by the one-time
     * prompt for people who went through the welcome before it asked. Never asked at start again either way.
     */
    fun startFilesAsked(context: Context) = prefs(context).getBoolean(KEY_START_FILES_ASKED, false)
    fun markStartFilesAsked(context: Context) = prefs(context).edit { putBoolean(KEY_START_FILES_ASKED, true) }

    /** A PDF has been opened in ScanPro's viewer at least once: one of the moments the default-viewer sheet may follow. */
    fun pdfOpened(context: Context) = prefs(context).getBoolean(KEY_PDF_OPENED, false)
    fun markPdfOpened(context: Context) {
        if (!pdfOpened(context)) prefs(context).edit { putBoolean(KEY_PDF_OPENED, true) }
    }
}

/** What decides whether the default-viewer sheet shows now. Pure, so the rules are unit tested. */
data class DefaultViewerPromptState(
    val alreadyShown: Boolean,
    /** A PDF has been opened in the viewer. */
    val pdfOpened: Boolean,
    /** Scans saved so far. */
    val completedScans: Int,
    /** ScanPro already opens PDFs by default, so there is nothing to ask. */
    val alreadyDefault: Boolean,
    val onHome: Boolean,
    /** The welcome, the Home tour, What's new, the rating sheet or the launch animation is up or still due. */
    val otherPromptDue: Boolean,
    /** Another one-time sheet already showed in this session: never two prompts in one visit. */
    val promptShownThisSession: Boolean
)

object DefaultViewerPrompt {
    fun isDue(state: DefaultViewerPromptState): Boolean = with(state) {
        !alreadyShown && !alreadyDefault && onHome && !otherPromptDue && !promptShownThisSession &&
            (pdfOpened || completedScans >= 1)
    }
}
