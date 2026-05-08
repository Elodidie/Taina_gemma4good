package com.example.gemma

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DarwinDatabase.getInstance(application).darwinDao()

    /** Full list of records, newest first — updates live as new records are saved. */
    val records: StateFlow<List<DarwinRecord>> = dao.getAll()
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.WhileSubscribed(5_000),
            initialValue  = emptyList()
        )

    /** Total number of records saved on this device. */
    val totalCount: StateFlow<Int> = dao.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Records not yet synced to GBIF. */
    val pendingCount: StateFlow<Int> = dao.getAll()
        .map { list -> list.count { it.status == "PENDING" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Wipe every record from the local database. */
    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }
}
