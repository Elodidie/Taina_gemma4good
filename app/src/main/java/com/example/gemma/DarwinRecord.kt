package com.example.gemma

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "darwin_records")
data class DarwinRecord(
    @PrimaryKey val occurrenceID: String = UUID.randomUUID().toString(),
    val scientificName: String = "",
    val vernacularName: String = "",
    val decimalLatitude: Double? = null,
    val decimalLongitude: Double? = null,
    val locality: String = "",
    val eventDate: String = "",
    val individualCount: Int = 1,
    val habitat: String = "",
    val notes: String = "",
    val photoPath: String = "",
    val status: String = "PENDING", // PENDING | SYNCED
    val createdAt: Long = System.currentTimeMillis()
)