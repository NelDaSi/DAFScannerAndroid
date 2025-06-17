package com.neldasi.jetpackcompose

data class ParsedPart(
    val typeCode: String,
    val supplierCode: String,
    val serialNumber: String,
    val batchNumber: String
)

fun parseScannedCode(code: String): ParsedPart? {
    if (code.length < 18) return null

    val typeCode = code.substring(0, 7)
    val supplierCode = code.substring(7, 12)
    val serialNumber = code.substring(12, 18)
    val batchNumber = code.substring(18)

    return ParsedPart(typeCode, supplierCode, serialNumber, batchNumber)
}