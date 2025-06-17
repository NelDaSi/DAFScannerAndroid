package com.neldasi.jetpackcompose

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

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
                onScannedValue(firstValue)
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