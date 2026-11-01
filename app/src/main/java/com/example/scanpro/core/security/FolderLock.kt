package com.example.scanpro.core.security

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.scanpro.core.di.ApplicationScope
import com.example.scanpro.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Locked folders: their documents stay out of All, Recent and search, and opening the folder or one of its
 * documents asks for the phone's own unlock (fingerprint, face or screen lock). A folder opened that way stays open
 * until the app has been in the background for a while ([relockAll], same timeout as the app lock).
 *
 * This is a privacy screen inside the app, not encryption: the files on the phone are as readable as any other
 * document's, and backups include them. The settings say so.
 */
@Singleton
class FolderLock @Inject constructor(
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    /** Locked folder names, lower-cased (folder names are compared ignoring case everywhere in the app). */
    val locked: StateFlow<Set<String>> =
        settings.lockedFolders.map { set -> set.map(::key).toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val unlocked = MutableStateFlow<Set<String>>(emptySet())

    /** Locked folders not opened this session: their documents are hidden. */
    val hidden: StateFlow<Set<String>> =
        combine(locked, unlocked) { l, u -> l - u }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isLocked(folder: String): Boolean = folder.isNotBlank() && key(folder) in locked.value
    fun isHidden(folder: String): Boolean = folder.isNotBlank() && key(folder) in hidden.value

    /** Locks or unlocks [folder] for good. */
    fun setLocked(folder: String, lock: Boolean) {
        if (folder.isBlank()) return
        // A folder locked while it is open stays open until the app is next left; the user is looking at it.
        if (lock) unlocked.value = unlocked.value + key(folder)
        scope.launch { settings.setFolderLocked(key(folder), lock) }
    }

    /** Opens [folder] for this session, after the phone's unlock succeeded. */
    fun unlockForSession(folder: String) { unlocked.value = unlocked.value + key(folder) }

    /** Closes every folder opened this session. */
    fun relockAll() { unlocked.value = emptySet() }

    /** Keeps the lock when a folder is renamed. */
    fun rename(oldName: String, newName: String) {
        if (!isLocked(oldName)) return
        scope.launch {
            settings.setFolderLocked(key(oldName), false)
            settings.setFolderLocked(key(newName), true)
        }
        if (key(oldName) in unlocked.value) unlocked.value = unlocked.value - key(oldName) + key(newName)
    }

    companion object {
        fun key(folder: String) = folder.trim().lowercase()
    }
}

/**
 * Asks for the phone's own unlock and runs the callback on success. Provided by MainActivity, which owns the
 * system prompt; the default does nothing, for previews.
 */
val LocalDeviceUnlock = staticCompositionLocalOf<(onSuccess: () -> Unit) -> Unit> { { } }
