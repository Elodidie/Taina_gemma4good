package com.example.gemma

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DayCount(val date: String, val count: Int)
data class SpeciesCount(val name: String, val count: Int)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DarwinDatabase.getInstance(application).darwinDao()

    val totalRecords: StateFlow<Int> = dao.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Observations grouped by eventDate, sorted chronologically.
     * Capped at the 30 most recent distinct dates to keep the chart readable.
     */
    val recordsPerDay: StateFlow<List<DayCount>> = dao.getAll()
        .map { records ->
            records
                .groupBy { it.eventDate.ifBlank { "?" } }
                .map { (date, list) -> DayCount(date, list.size) }
                .sortedBy { it.date }
                .takeLast(30)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Top 5 species by number of occurrences. */
    val topSpecies: StateFlow<List<SpeciesCount>> = dao.getAll()
        .map { records ->
            records
                .groupBy {
                    it.vernacularName.ifBlank { it.scientificName.ifBlank { "Unknown" } }
                }
                .map { (name, list) -> SpeciesCount(name, list.size) }
                .sortedByDescending { it.count }
                .take(5)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Records that carry GPS coordinates — used to populate the map. */
    val geoRecords: StateFlow<List<DarwinRecord>> = dao.getAll()
        .map { list ->
            list.filter { it.decimalLatitude != null && it.decimalLongitude != null }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
