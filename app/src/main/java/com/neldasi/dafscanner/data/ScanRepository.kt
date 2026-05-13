package com.neldasi.dafscanner.data

import kotlinx.coroutines.flow.Flow

class ScanRepository(private val scanDao: ScanDao) {
    val allParts: Flow<List<ScannedPart>> = scanDao.getAllParts()

    suspend fun getPartByCode(fullCode: String) = scanDao.getPartByCode(fullCode)

    suspend fun insert(part: ScannedPart) {
        scanDao.insertPart(part)
    }

    suspend fun update(part: ScannedPart) {
        scanDao.updatePart(part)
    }

    suspend fun delete(part: ScannedPart) {
        scanDao.deletePart(part)
    }

    suspend fun deleteParts(fullCodes: List<String>) {
        scanDao.deleteParts(fullCodes)
    }

    suspend fun deleteAll() {
        scanDao.deleteAll()
    }
}
