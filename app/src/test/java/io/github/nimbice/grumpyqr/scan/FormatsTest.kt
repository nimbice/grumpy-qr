package io.github.nimbice.grumpyqr.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    @Test
    fun labels() {
        assertEquals("QR Code", Formats.label("QR_CODE"))
        assertEquals("EAN-13", Formats.label("EAN_13"))
        assertEquals("GS1 DataBar", Formats.label("DATA_BAR_EXP_STK"))
        assertEquals("Something new", Formats.label("SOMETHING_NEW"))
    }

    @Test
    fun retail() {
        assertTrue(Formats.isRetail("EAN_13"))
        assertFalse(Formats.isRetail("QR_CODE"))
    }

    @Test
    fun zxingIntentNames() {
        assertEquals("QR_CODE", Formats.zxingIntentName("QR_CODE_MODEL_2"))
        assertEquals("MAXICODE", Formats.zxingIntentName("MAXI_CODE"))
        assertEquals("RSS_EXPANDED", Formats.zxingIntentName("DATA_BAR_EXP"))
        assertEquals("RSS_14", Formats.zxingIntentName("DATA_BAR"))
        assertEquals("EAN_13", Formats.zxingIntentName("EAN_13"))
    }
}
