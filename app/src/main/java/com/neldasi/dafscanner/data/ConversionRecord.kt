package com.neldasi.dafscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hex: String,
    val dec: String,
    val timestamp: Long = System.currentTimeMillis()
)
