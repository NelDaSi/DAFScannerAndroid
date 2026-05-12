package com.neldasi.dafscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "scanned_parts")
@Serializable
data class ScannedPart(
    @PrimaryKey val fullCode: String,
    val timestamp: Long,
    val imageUri: String? = null,
    val note: String? = null,
    val ordinal: Int = 0,
)
