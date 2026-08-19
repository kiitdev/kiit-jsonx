package kiit.jsonx.parser.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =================================================================================================
// DuplicateKeyTrackerTest
// =================================================================================================
class DuplicateKeyTrackerTest {
    @Test
    fun offer_firstOccurrence_returnsFalse() {
        val tracker = DuplicateKeyTracker()
        assertFalse(tracker.offer("host"))
    }

    @Test
    fun offer_repeatedKey_returnsTrue() {
        val tracker = DuplicateKeyTracker()
        tracker.offer("host")
        assertTrue(tracker.offer("host"))
    }

    @Test
    fun offer_distinctKeys_neverReportsDuplicate() {
        val tracker = DuplicateKeyTracker()
        assertFalse(tracker.offer("host"))
        assertFalse(tracker.offer("port"))
        assertFalse(tracker.offer("debug"))
    }

    @Test
    fun offer_isIndependentPerInstance() {
        val first = DuplicateKeyTracker()
        val second = DuplicateKeyTracker()
        first.offer("host")
        assertFalse(second.offer("host"))
    }
}
