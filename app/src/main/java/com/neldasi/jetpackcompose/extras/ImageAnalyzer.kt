package com.neldasi.jetpackcompose.extras
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage

private const val VIBRATE_DURATION = 100L

@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    context: Context,
    vibrateEnabled: Boolean,
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
            Log.d("Scanner", "Detected ${barcodes.size} barcodes")
            val firstValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue

            if (firstValue != null) {
                onScannedValue(firstValue)
                if (vibrateEnabled) {
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_DURATION, VibrationEffect.DEFAULT_AMPLITUDE))
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