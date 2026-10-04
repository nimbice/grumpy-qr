package io.github.nimbice.grumpyqr.scan

import zxingcpp.BarcodeReader

/** Thin wrapper around zxing-cpp. Readers aren't thread-safe: one per thread. */
object Decoder {

    /** Tuned for live camera frames: thorough, but no expensive image pyramids. */
    fun forCamera(): BarcodeReader = BarcodeReader(
        BarcodeReader.Options(
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
        ),
    )

    /** Tuned for still pictures, where we can afford to try everything. */
    fun forImages(): BarcodeReader = BarcodeReader(
        BarcodeReader.Options(
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
            tryDenoise = true,
        ),
    )
}

fun BarcodeReader.Result.toScan(): Scan? {
    val decoded = text ?: bytes?.toString(Charsets.ISO_8859_1) ?: return null
    if (decoded.isEmpty()) return null
    return Scan(text = decoded, format = format.name, bytes = bytes, ecLevel = ecLevel)
}

fun List<BarcodeReader.Result>.toScans(): List<Scan> =
    mapNotNull { it.toScan() }.distinctBy { it.format to it.text }
