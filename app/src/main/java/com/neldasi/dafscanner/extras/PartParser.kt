package com.neldasi.dafscanner.extras

data class ParsedPart(
    val typeCode: String,
    val supplierCode: String,
    val serialNumber: String,
    val batchNumber: String
)

fun parseScannedCode(code: String): ParsedPart? {
    return try {
        if (code.length < 18) return null
        ParsedPart(
            typeCode = code.substring(0, 7),
            supplierCode = code.substring(7, 12),
            serialNumber = code.substring(12, 18),
            batchNumber = code.substring(18)
        )
    } catch (_: Exception) {
        null
    }
}