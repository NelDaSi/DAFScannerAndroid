package com.neldasi.dafscanner.extras

data class ParsedPart(
    val typeCode: String,
    val supplierCode: String,
    val serialNumber: String,
    val batchNumber: String,
) {
    val decSerial: String
        get() = try {
            serialNumber.toLong(16).toString()
        } catch (_: Exception) {
            "N/A"
        }
}

fun parseScannedCode(code: String): ParsedPart? {
    return try {
        if (code.length < 18) return null
        
        if (code.length == 27) {
            // New structure: 7 (Type) + 5 (Supplier) + 9 (Batch: Unknown 6 + Middle 3) + 6 (Serial)
            ParsedPart(
                typeCode = code.substring(0, 7),
                supplierCode = code.substring(7, 12),
                serialNumber = code.substring(21),
                batchNumber = code.substring(12, 21)
            )
        } else {
            // Old structure (29 chars or fallback): 7 (Type) + 5 (Supplier) + 6 (Serial) + Rest (Batch)
            ParsedPart(
                typeCode = code.substring(0, 7),
                supplierCode = code.substring(7, 12),
                serialNumber = code.substring(12, 18),
                batchNumber = code.substring(18)
            )
        }
    } catch (_: Exception) {
        null
    }
}