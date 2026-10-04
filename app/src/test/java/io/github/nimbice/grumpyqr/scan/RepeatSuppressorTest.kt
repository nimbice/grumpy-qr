package io.github.nimbice.grumpyqr.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatSuppressorTest {

    private fun scan(text: String) = Scan(text = text, format = "QR_CODE")

    @Test
    fun aDismissedCodeStaysQuietWhileInView() {
        val suppressor = RepeatSuppressor(framesToForget = 3)
        suppressor.suppress(listOf(scan("a")))
        repeat(10) { assertEquals(emptyList<Scan>(), suppressor.filter(listOf(scan("a")))) }
    }

    @Test
    fun aDifferentCodeStillGetsThrough() {
        val suppressor = RepeatSuppressor(framesToForget = 3)
        suppressor.suppress(listOf(scan("a")))
        assertEquals(listOf("b"), suppressor.filter(listOf(scan("a"), scan("b"))).map { it.text })
    }

    @Test
    fun aCodeComesBackAfterLeavingTheView() {
        val suppressor = RepeatSuppressor(framesToForget = 3)
        suppressor.suppress(listOf(scan("a")))
        repeat(3) { suppressor.filter(emptyList()) }
        assertEquals(listOf("a"), suppressor.filter(listOf(scan("a"))).map { it.text })
    }

    @Test
    fun brieflyLosingTheCodeDoesntResetIt() {
        val suppressor = RepeatSuppressor(framesToForget = 3)
        suppressor.suppress(listOf(scan("a")))
        repeat(2) { suppressor.filter(emptyList()) }
        assertEquals(emptyList<Scan>(), suppressor.filter(listOf(scan("a"))))
    }
}
