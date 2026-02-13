package com.oo_dev17.qrnotes

import com.journeyapps.barcodescanner.ScanOptions

fun buildQrScanOptions(prompt: String): ScanOptions =
    ScanOptions().apply {
        setDesiredBarcodeFormats(
            listOf(
                ScanOptions.EAN_13,
                ScanOptions.EAN_8,
                ScanOptions.QR_CODE
            )
        )
        setPrompt(prompt)
        setCameraId(0)
        setBeepEnabled(true)
        setBarcodeImageEnabled(true)
    }
