package com.neldasi.jetpackcompose

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onScannedValue: (String) -> Unit,
    onError: ((Throwable) -> Unit)? = null // optional error callback
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        var valueFound = false // To ensure onScannedValue is called only once per frame analysis

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                // Log.d("Scanner", "Found ${barcodes.size} barcode(s)")
                for (barcode in barcodes) {
                    if (valueFound) return@addOnSuccessListener // Already processed a barcode in this frame

                    barcode.rawValue?.let { value ->
                        // Log.d("Scanner", "Scanned value: $value")
                        onScannedValue(value)
                        valueFound = true // Mark as found
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Scanner", "Barcode scanning failed", exception)
                onError?.invoke(exception)
            }
            .addOnCompleteListener {
                imageProxy.close() // CRITICAL: Always close the ImageProxy
            }
    } else {
        Log.w("Scanner", "ImageProxy contained no image.")
        imageProxy.close() // Still close if no image
        onError?.invoke(IllegalStateException("ImageProxy contained no image"))
    }
}