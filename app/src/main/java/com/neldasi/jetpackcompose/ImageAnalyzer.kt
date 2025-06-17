package com.neldasi.jetpackcompose

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

@OptIn(ExperimentalGetImage::class)
data class ParsedPart(
    val typeCode: String,
    val supplierCode: String,
    val serialNumber: String,
    val batchNumber: String
)

val validTypes = listOf(
    "2261325", "XYZ5678", "LMN9012"
)

fun parseScannedCode(code: String): ParsedPart? {
    if (code.length < 18) return null

    val typeCode = code.substring(0, 7)
    val supplierCode = code.substring(7, 12)
    val serialNumber = code.substring(12, 18)
    val batchNumber = code.substring(18)

    if (typeCode !in validTypes) return null

    return ParsedPart(typeCode, supplierCode, serialNumber, batchNumber)
}

@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onScannedValue: (String) -> Unit,
    onError: ((Throwable) -> Unit)? = null
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        Log.w("Scanner", "ImageProxy contained no image.")
        imageProxy.close()
        onError?.invoke(IllegalStateException("ImageProxy contained no image"))
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val firstValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue

            if (firstValue != null) {
                // ✅ Now we add filtering here:
                val parsed = parseScannedCode(firstValue)
                if (parsed != null) {
                    // ✅ We only care about the SerialNumber
                    onScannedValue(parsed.serialNumber)
                } else {
                    Log.w("Scanner", "Invalid part type detected: $firstValue")
                }
            }
        }
        .addOnFailureListener { exception ->
            Log.e("Scanner", "Barcode scanning failed", exception)
            onError?.invoke(exception)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}