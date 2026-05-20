package com.neldasi.dafscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_items")
data class SearchItem(
    @PrimaryKey
    val serialNumber: String,
    val typeCode: String,
    val decSerial: String,
    val scanTimestamp: Long? = null,
    val scanOrder: Int? = null,
    val machine: String? = null,
    val outputMaterial: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val completeDate: String? = null,
    val completeTime: String? = null,
)
