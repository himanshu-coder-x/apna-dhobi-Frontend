package com.example.util

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class QrScannerManager(private val context: Context) {

    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()

    private val scanner = GmsBarcodeScanning.getClient(context, options)

    fun startScanning(onSuccess: (String) -> Unit) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue: String? = barcode.rawValue
                if (rawValue != null) {
                    onSuccess(rawValue)
                } else {
                    Toast.makeText(context, "Scan failed: No data found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            .addOnCanceledListener {
                // Handle cancellation if needed
            }
    }
}
