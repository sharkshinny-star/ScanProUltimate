package com.example.scanpro.feature.kept

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanpro.R
import com.example.scanpro.core.operations.OperationRunner
import com.example.scanpro.work.KeptScan
import com.example.scanpro.work.KeptScanStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class KeptScansViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: KeptScanStore,
    private val operations: OperationRunner
) : ViewModel() {
    /** Null until the first look at the session folder, so the screen doesn't flash its empty state. */
    val scans: StateFlow<List<KeptScan>?> = store.scans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry(id: String) {
        viewModelScope.launch {
            store.retry(id)
            operations.showMessage(context.getString(R.string.kept_msg_saving_again))
        }
    }

    fun discard(id: String) {
        viewModelScope.launch {
            store.discard(id)
            operations.showMessage(context.getString(R.string.kept_msg_discarded))
        }
    }
}
