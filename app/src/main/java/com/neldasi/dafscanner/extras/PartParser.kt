package com.neldasi.dafscanner.extras

import java.util.Locale

enum class EngineFormat {
    MX11,
    MX13,
    P14,
    UNKNOWN
}

data class ParsedPart(
    val rawCode: String,
    val format: EngineFormat,
    val typeCode: String,
    val supplierCode: String,
    val serialHex: String,
    val serialDecimal: String,
    val batchNumber: String = ""
) {
    // Legacy support for other parts of the app
    val serialNumber: String get() = serialHex
    val decSerial: String get() = serialDecimal
}

fun parseScannedCode(code: String): ParsedPart? {
    if (code.length < 18) return null

    return try {
        val type = code.substring(0, 7)
        val supplier = code.substring(7, 12)

        when {
            // 1. MX11: Contains 'K'
            code.contains("K") -> {
                val hex = code.substring(12, 18).uppercase(Locale.ROOT)
                val decimal = hex.toLong(16).toString()
                ParsedPart(
                    rawCode = code,
                    format = EngineFormat.MX11,
                    typeCode = type,
                    supplierCode = supplier,
                    serialHex = hex,
                    serialDecimal = decimal,
                    batchNumber = code.substring(18)
                )
            }

            // 2. MX13: Contains "0074" or "74" marker logic, or length suggests old format with hex at 12-18
            // Based on prompt: "Else if the code contains '0074' after the first 18 chars, 
            // or matches the older length/structure with hex serial at position 12–17"
            code.indexOf("0074", 18) != -1 || code.indexOf("74", 18) != -1 -> {
                val hex = code.substring(12, 18).uppercase(Locale.ROOT)
                val decimal = try {
                    // Try to extract the decimal serial after the 0074 marker if possible
                    val markerIndex = code.indexOf("0074", 18)
                    if (markerIndex != -1) {
                        code.substring(markerIndex + 4).toLong().toString()
                    } else {
                        hex.toLong(16).toString()
                    }
                } catch (_: Exception) {
                    hex.toLong(16).toString()
                }

                ParsedPart(
                    rawCode = code,
                    format = EngineFormat.MX13,
                    typeCode = type,
                    supplierCode = supplier,
                    serialHex = hex,
                    serialDecimal = decimal,
                    batchNumber = "" // Logic can be refined if batch info is needed
                )
            }

            // 3. P14: Newest format. Numeric only and serial at the end.
            // Example length from prompt: 2473073 88280 8256927 80010027 -> 27 chars
            code.length == 27 && code.all { it.isDigit() } -> {
                val decimalStr = code.substring(code.length - 7)
                val decimalVal = decimalStr.toLong()
                val hex = decimalVal.toString(16).uppercase(Locale.ROOT).padStart(6, '0')
                
                ParsedPart(
                    rawCode = code,
                    format = EngineFormat.P14,
                    typeCode = type,
                    supplierCode = supplier,
                    serialHex = hex,
                    serialDecimal = decimalVal.toString(),
                    batchNumber = code.substring(12, 20) // middle part
                )
            }

            // Fallback for codes that don't strictly match but are >= 18
            else -> {
                val hex = code.substring(12, 18).uppercase(Locale.ROOT)
                val decimal = try { hex.toLong(16).toString() } catch(_: Exception) { "N/A" }
                ParsedPart(
                    rawCode = code,
                    format = EngineFormat.UNKNOWN,
                    typeCode = type,
                    supplierCode = supplier,
                    serialHex = hex,
                    serialDecimal = decimal,
                    batchNumber = if (code.length > 18) code.substring(18) else ""
                )
            }
        }
    } catch (_: Exception) {
        null
    }
}
