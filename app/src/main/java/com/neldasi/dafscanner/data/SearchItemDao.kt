package com.neldasi.dafscanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchItemDao {
    @Query("SELECT * FROM search_items")
    fun getAllItems(): Flow<List<SearchItem>>

    @Query("SELECT * FROM search_items WHERE serialNumber = :serial LIMIT 1")
    suspend fun getItemBySerial(serial: String): SearchItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SearchItem>)

    @Update
    suspend fun update(item: SearchItem)

    @Query("DELETE FROM search_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM search_items")
    suspend fun getCount(): Int

    @Query("SELECT MAX(scanOrder) FROM search_items")
    suspend fun getMaxScanOrder(): Int?
}
