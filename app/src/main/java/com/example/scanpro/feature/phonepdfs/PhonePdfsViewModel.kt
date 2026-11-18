package com.example.scanpro.feature.phonepdfs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneFilesState(
    /** Null until checked. */
    val hasAccess: Boolean? = null,
    val loading: Boolean = false,
    /** Null until the first search has finished. */
    val all: List<PhoneFile>? = null,
    val filter: PhoneFileFilter = PhoneFileFilter.ALL,
    val query: String = "",
    val sort: PhoneFileSort = PhoneFileSort.DATE,
    val ascending: Boolean = PhoneFiles.defaultAscending(PhoneFileSort.DATE),
    /** Uris of the files ticked by a long-press, in the order they were ticked. */
    val selected: List<String> = emptyList(),
    /** The folder open in the Folders view ("" is the top of the storage); null shows every file in one list. */
    val folderPath: String? = null,
    /** Files as a grid of thumbnails instead of a list. */
    val grid: Boolean = false
) {
    /** The Folders view is showing: a search always looks through every folder. */
    val browsingFolders: Boolean get() = folderPath != null && query.isBlank()

    /** What the list shows: filtered by kind and [query] (and to the open folder when browsing), then sorted. */
    val shown: List<PhoneFile>? get() = all?.let { files ->
        val inPlace = if (browsingFolders) PhoneFiles.filesIn(files, folderPath.orEmpty()) else files
        PhoneFiles.sort(PhoneFiles.filter(inPlace, filter, query), sort, ascending)
    }

    /** The folders inside the open one, for the kind chosen; empty outside the Folders view. */
    val folders: List<PhoneFolder> get() = if (!browsingFolders) emptyList() else all?.let { files ->
        PhoneFiles.sortFolders(PhoneFiles.subfolders(PhoneFiles.filter(files, filter, ""), folderPath.orEmpty()), sort, ascending)
    }.orEmpty()
    val selection: List<PhoneFile> get() = all.orEmpty().associateBy { it.uri.toString() }.let { byUri -> selected.mapNotNull(byUri::get) }
}

/**
 * Files on this phone (Home's section, the Files tab's "On this phone" and the full screen): the access check, the
 * MediaStore search (cancelled with the screen), a refresh when MediaStore reports changes, and the list's options.
 */
@HiltViewModel
class PhonePdfsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    results: com.example.scanpro.data.ToolResultsStore
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(PhoneFilesState(all = lastFound, grid = prefs.getBoolean(KEY_GRID, false)))
    val state: StateFlow<PhoneFilesState> = _state.asStateFlow()

    /** The files the PDF and image tools made, for the "Processed files" entry. */
    val processed: StateFlow<List<com.example.scanpro.data.ToolResultFile>> = results.items
    private var loadJob: Job? = null
    private var changeJob: Job? = null

    // MediaStore reports every new photo or download; wait for a quiet second, then look again.
    private val stopObserving = PhonePdfSource.observe(context) {
        if (_state.value.hasAccess != true) return@observe
        changeJob?.cancel()
        changeJob = viewModelScope.launch {
            delay(1_000)
            refresh()
        }
    }

    /** Called on every resume: the user may have just turned the access on or off in system settings. */
    fun checkAccess() {
        val granted = PhonePdfSource.hasAccess(context)
        val before = _state.value.hasAccess
        _state.update { it.copy(hasAccess = granted, all = if (granted) it.all else null, selected = if (granted) it.selected else emptyList()) }
        if (granted && (before != true || _state.value.all == null)) refresh()
        if (!granted) {
            loadJob?.cancel()
            lastFound = null
        }
    }

    fun refresh() {
        if (!PhonePdfSource.hasAccess(context)) return
        loadJob?.cancel()
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val found = PhonePdfSource.load(context)
                lastFound = found
                _state.update { state ->
                    val present = found.mapTo(HashSet()) { it.uri.toString() }
                    state.copy(all = found, loading = false, selected = state.selected.filter { it in present })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(all = it.all ?: emptyList(), loading = false) }
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun setFilter(filter: PhoneFileFilter) = _state.update { it.copy(filter = filter) }

    /** Choosing the current sort again flips its direction; a new sort starts in its natural direction. */
    fun setSort(sort: PhoneFileSort) = _state.update {
        if (it.sort == sort) it.copy(ascending = !it.ascending) else it.copy(sort = sort, ascending = PhoneFiles.defaultAscending(sort))
    }

    fun toggleSelection(file: PhoneFile) = _state.update {
        val key = file.uri.toString()
        it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptyList()) }

    /** Opens [path] in the Folders view ("" is the top of the storage); null goes back to the single list. */
    fun openFolder(path: String?) = _state.update { it.copy(folderPath = path?.trim('/'), query = if (path != null) "" else it.query) }

    /** One folder up; at the top, leaves the Folders view. */
    fun folderUp() = _state.update { state ->
        val path = state.folderPath ?: return@update state
        state.copy(folderPath = if (path.isEmpty()) null else path.substringBeforeLast('/', ""))
    }

    fun setGrid(grid: Boolean) {
        prefs.edit().putBoolean(KEY_GRID, grid).apply()
        _state.update { it.copy(grid = grid) }
    }

    override fun onCleared() {
        stopObserving()
    }

    private companion object {
        const val PREFS = "phone_files"
        const val KEY_GRID = "grid"

        /** The last list found, shared by Home and Files so switching between them shows it at once while it refreshes. */
        @Volatile
        var lastFound: List<PhoneFile>? = null
    }
}
