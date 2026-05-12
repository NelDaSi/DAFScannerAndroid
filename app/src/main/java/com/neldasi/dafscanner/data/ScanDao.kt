package com.neldasi.dafscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scanned_parts ORDER BY timestamp DESC")
    fun getAllParts(): Flow<List<ScannedPart>>

    @Query("SELECT * FROM scanned_parts WHERE fullCode = :fullCode")
    suspend fun getPartByCode(fullCode: String): ScannedPart?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPart(part: ScannedPart)

    @Update
    suspend fun updatePart(part: ScannedPart)

    @Delete
    suspend fun deletePart(part: ScannedPart)

    @Query("DELETE FROM scanned_parts WHERE fullCode IN (:fullCodes)")
    suspend fun deleteParts(fullCodes: List<String>)

    @Query("DELETE FROM scanned_parts")
    suspend fun deleteAll()
}
