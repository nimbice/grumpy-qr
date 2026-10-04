package io.github.nimbice.grumpyqr.scan

import io.github.nimbice.grumpyqr.content.Content
import io.github.nimbice.grumpyqr.content.ContentParser

/** One decoded code. */
class Scan(
    val text: String,
    /** zxing-cpp format name, e.g. "QR_CODE". */
    val format: String,
    /** Raw bytes as encoded in the symbol, when available. */
    val bytes: ByteArray? = null,
    val ecLevel: String? = null,
    val time: Long = System.currentTimeMillis(),
) {
    val content: Content by lazy { ContentParser.parse(text, Formats.isRetail(format)) }

    val formatLabel: String get() = Formats.label(format)
}

object Formats {

    fun label(format: String): String = when (format) {
        "QR_CODE", "QR_CODE_MODEL_1", "QR_CODE_MODEL_2" -> "QR Code"
        "MICRO_QR_CODE" -> "Micro QR Code"
        "RMQR_CODE" -> "rMQR Code"
        "DATA_MATRIX" -> "Data Matrix"
        "AZTEC", "AZTEC_CODE", "AZTEC_RUNE" -> "Aztec"
        "PDF_417", "COMPACT_PDF_417", "MICRO_PDF_417" -> "PDF417"
        "MAXI_CODE" -> "MaxiCode"
        "EAN_13" -> "EAN-13"
        "EAN_8" -> "EAN-8"
        "EAN_5" -> "EAN-5"
        "EAN_2" -> "EAN-2"
        "UPC_A" -> "UPC-A"
        "UPC_E" -> "UPC-E"
        "ISBN" -> "ISBN"
        "CODE_128" -> "Code 128"
        "CODE_39", "CODE_39_STD", "CODE_39_EXT" -> "Code 39"
        "CODE_93" -> "Code 93"
        "CODE_32" -> "Code 32"
        "CODABAR" -> "Codabar"
        "ITF", "ITF_14" -> "ITF"
        "DX_FILM_EDGE" -> "DX Film Edge"
        "PZN" -> "PZN"
        else -> when {
            format.startsWith("DATA_BAR") -> "GS1 DataBar"
            format.startsWith("TELEPEN") -> "Telepen"
            else -> format.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
    }

    fun isRetail(format: String): Boolean =
        format in setOf("EAN_13", "EAN_8", "UPC_A", "UPC_E", "ISBN", "EAN_UPC")

    /** Format names as the classic ZXing SCAN intent reports them. */
    fun zxingIntentName(format: String): String = when {
        format.startsWith("QR_CODE") -> "QR_CODE"
        format == "MAXI_CODE" -> "MAXICODE"
        format.startsWith("DATA_BAR_EXP") -> "RSS_EXPANDED"
        format.startsWith("DATA_BAR") -> "RSS_14"
        format.startsWith("CODE_39") -> "CODE_39"
        format == "ITF_14" -> "ITF"
        format.startsWith("AZTEC") -> "AZTEC"
        format.endsWith("PDF_417") -> "PDF_417"
        else -> format
    }
}
