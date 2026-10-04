package io.github.nimbice.grumpyqr.scan

/**
 * Stops the same code from popping up again the moment you close its result
 * (or come back to the scanner from another screen). A code is ignored until
 * it has been out of view for a little while.
 *
 * Lives in the view model so it survives navigation and rotation. It's only
 * ever used from one camera analysis thread at a time, but that thread can
 * change, hence @Volatile.
 */
class RepeatSuppressor(private val framesToForget: Int = 15) {
    @Volatile private var suppressed: Set<String> = emptySet()
    @Volatile private var framesWithout = 0

    fun suppress(scans: List<Scan>) {
        suppressed = scans.mapTo(HashSet()) { it.text }
        framesWithout = 0
    }

    /** Returns the scans that should be shown, given one camera frame's results. */
    fun filter(scans: List<Scan>): List<Scan> {
        if (suppressed.isEmpty()) return scans
        if (scans.any { it.text in suppressed }) {
            framesWithout = 0
        } else if (++framesWithout >= framesToForget) {
            // Frames where the decoder simply missed (motion blur) count too, so keep this generous.
            suppressed = emptySet()
        }
        return scans.filterNot { it.text in suppressed }
    }
}
