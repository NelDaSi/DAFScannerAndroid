
package com.neldasi.jetpackcompose
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
            val firstValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue

            if (firstValue != null) {
                onScannedValue(firstValue)
                if (vibrateEnabled) {
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
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