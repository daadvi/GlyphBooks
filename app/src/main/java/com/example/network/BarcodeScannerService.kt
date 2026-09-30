package com.example.network

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

object BarcodeScannerService {
    private const val TAG = "BarcodeScannerService"

    /**
     * Attempts to detect and decode an EAN-13 / ISBN barcode from a Bitmap.
     * Tries full image first, and if not found, tries central crops and scaled versions.
     */
    fun scanBarcodeFromBitmap(bitmap: Bitmap): String? {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(
                DecodeHintType.POSSIBLE_FORMATS,
                listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.QR_CODE
                )
            )
            put(DecodeHintType.TRY_HARDER, true)
        }

        // 1. Try full bitmap
        tryDecode(bitmap, hints)?.let { return it }

        // 2. Try scaled down if bitmap is very large (> 1200px)
        if (bitmap.width > 1200 || bitmap.height > 1200) {
            val scale = 1200f / maxOf(bitmap.width, bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
            tryDecode(scaled, hints)?.let { return it }
        }

        // 3. Try central crop (users usually center the barcode)
        try {
            val cropW = (bitmap.width * 0.8f).toInt()
            val cropH = (bitmap.height * 0.6f).toInt()
            val startX = (bitmap.width - cropW) / 2
            val startY = (bitmap.height - cropH) / 2
            if (cropW > 50 && cropH > 50) {
                val cropped = Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
                tryDecode(cropped, hints)?.let { return it }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Crop decode skipped: ${e.message}")
        }

        return null
    }

    private fun tryDecode(bmp: Bitmap, hints: Map<DecodeHintType, Any>): String? {
        return try {
            val width = bmp.width
            val height = bmp.height
            val pixels = IntArray(width * height)
            bmp.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            reader.setHints(hints)
            val result = reader.decodeWithState(binaryBitmap)

            val text = result.text.trim()
            val clean = text.replace("-", "").replace(" ", "")
            Log.i(TAG, "Barcode found successfully: $clean (format: ${result.barcodeFormat})")
            clean
        } catch (e: Exception) {
            null
        }
    }
}
