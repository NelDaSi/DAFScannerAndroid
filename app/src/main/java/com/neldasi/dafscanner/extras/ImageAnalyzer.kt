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
import android.graphics.ImageFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.datamatrix.DataMatrixReader
import com.google.zxing.LuminanceSource
import com.google.zxing.Reader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.NotFoundException
import kotlin.math.min

private const val VIBRATE_DURATION = 100L

// Size (in pixels) of the square region (center crop) we send to ZXing as fallback.
// This does not correspond to dp; it is in the image buffer's pixel space.
private const val ZXING_CROP_SIZE_PX = 480

/**
 * A simple LuminanceSource for ZXing that wraps a grayscale byte array.
 * `luma` must be width * height bytes, row-major, 8-bit Y/gray.
 */
private class ByteArrayLuminanceSource(
    private val luma: ByteArray,
    dataWidth: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int
) : LuminanceSource(width, height) {

    private val rowStride = dataWidth
    private val topOffset = top
    private val leftOffset = left

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val outRow = row ?: ByteArray(width)
        val srcIndex = (y + topOffset) * rowStride + leftOffset
        System.arraycopy(luma, srcIndex, outRow, 0, width)
        return outRow
    }

    override fun getMatrix(): ByteArray {
        val area = ByteArray(width * height)
        var inputIndex = topOffset * rowStride + leftOffset
        var outIndex = 0
        (0 until height).forEach { y ->
            System.arraycopy(luma, inputIndex, area, outIndex, width)
            inputIndex += rowStride
            outIndex += width
        }
        return area
    }

    override fun isCropSupported(): Boolean = true
}
/**
 * Convert the Y (luma) plane of an ImageProxy in YUV_420_888 format to a single
 * byte array of grayscale values. Returns Pair<width,height> along with the array.
 * If format is unexpected, returns null.
 */
@OptIn(ExperimentalGetImage::class)
private fun extractLumaPlane(imageProxy: ImageProxy): Triple<ByteArray, Int, Int>? {
    val image = imageProxy.image ?: return null
    if (image.format != ImageFormat.YUV_420_888 && imageProxy.format != ImageFormat.YUV_420_888) {
        return null
    }

    val yPlane = imageProxy.planes[0]
    val yBuffer = yPlane.buffer
    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride

    val width = imageProxy.width
    val height = imageProxy.height

    // We'll build a tightly packed grayscale array width*height
    val out = ByteArray(width * height)

    var outIndex = 0
    val rowData = ByteArray(yRowStride)
    for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(rowData, 0, yRowStride.coerceAtMost(yBuffer.remaining()))
        var col = 0
        while (col < width) {
            out[outIndex++] = rowData[col * yPixelStride]
            col++
        }
    }

    return Triple(out, width, height)
}

/**
 * Try to decode a Data Matrix using ZXing from the center crop of the luminance buffer.
 * Returns the decoded string, or null if nothing could be decoded.
 */
private fun tryDecodeWithZXingCenterCrop(luma: ByteArray, width: Int, height: Int): String? {
    // determine crop box centered in the frame
    val cropSize = ZXING_CROP_SIZE_PX
    val cropWidth = min(cropSize, width)
    val cropHeight = min(cropSize, height)
    val left = ((width - cropWidth) / 2).coerceAtLeast(0)
    val top = ((height - cropHeight) / 2).coerceAtLeast(0)

    // build luminance source for that region
    val source: LuminanceSource = ByteArrayLuminanceSource(
        luma = luma,
        dataWidth = width,
        left = left,
        top = top,
        width = cropWidth,
        height = cropHeight
    )

    // Binarize and decode with DataMatrix-only reader
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    val reader: Reader = DataMatrixReader()
    return try {
        val result: Result = reader.decode(bitmap)
        result.text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}

@Volatile
private var frameSkipCounter = 0

@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    context: Context,
    vibrateEnabled: Boolean,
    onScannedValue: (String) -> Unit,
    onError: ((Throwable) -> Unit)? = null
) {
    // Throttle analysis: only run on every other frame to help stability / focus
    frameSkipCounter++
    if (frameSkipCounter % 2 != 0) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        Log.w("Scanner", "ImageProxy contained no image.")
        imageProxy.close()
        onError?.invoke(IllegalStateException("ImageProxy contained no image"))
        return
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

    // 1. Primary path: ML Kit
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            Log.d("Scanner", "Detected ${barcodes.size} barcodes (ML Kit)")
            val firstValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue

            if (firstValue != null) {
                onScannedValue(firstValue)
                if (vibrateEnabled) {
                    val vibrator = context.getSystemService(Vibrator::class.java)
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            VIBRATE_DURATION,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
                return@addOnSuccessListener
            }

            // 2. Fallback: ZXing on center crop (Data Matrix only)
            try {
                val triple = extractLumaPlane(imageProxy)
                if (triple != null) {
                    val (lumaBytes, w, h) = triple
                    val zxingResult = tryDecodeWithZXingCenterCrop(lumaBytes, w, h)
                    if (zxingResult != null) {
                        Log.d("Scanner", "ZXing decoded Data Matrix: $zxingResult")
                        onScannedValue(zxingResult)
                        if (vibrateEnabled) {
                            val vibrator = context.getSystemService(Vibrator::class.java)
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    VIBRATE_DURATION,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        }
                        return@addOnSuccessListener
                    } else {
                        Log.d("Scanner", "ZXing fallback: nothing decoded")
                    }
                } else {
                    Log.w("Scanner", "ZXing fallback: could not extract luma plane")
                }
            } catch (zxEx: Exception) {
                Log.e("Scanner", "ZXing fallback failed", zxEx)
            }
        }
        .addOnFailureListener { exception ->
            Log.e("Scanner", "Barcode scanning failed (ML Kit)", exception)
            onError?.invoke(exception)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}