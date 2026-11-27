package com.example.scanpro.feature.settings

import com.example.scanpro.data.OversizedPdfs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Free up space" card can be closed, and stays closed until a clearly larger amount could be freed. */
class FreeSpaceOfferTest {
    private val mb = 1_000_000L

    @Test
    fun nothingToFreeShowsNothing() {
        assertNull(freeSpaceOffer(null, -1L))
        assertNull(freeSpaceOffer(null, 200 * mb))
    }

    @Test
    fun neverClosedShowsTheOffer() {
        val found = OversizedPdfs(3, 226 * mb)
        assertEquals(found, freeSpaceOffer(found, -1L))
    }

    @Test
    fun closedStaysClosedForTheSameOrASlightlyLargerAmount() {
        assertNull(freeSpaceOffer(OversizedPdfs(3, 226 * mb), 226 * mb))
        // 226 MB closed: it takes more than 226 + 113 + 100 = 439 MB to bring the card back.
        assertNull(freeSpaceOffer(OversizedPdfs(4, 300 * mb), 226 * mb))
        assertNull(freeSpaceOffer(OversizedPdfs(4, 439 * mb), 226 * mb))
    }

    @Test
    fun closedComesBackWhenMuchMoreCouldBeFreed() {
        val found = OversizedPdfs(9, 600 * mb)
        assertEquals(found, freeSpaceOffer(found, 226 * mb))
    }

    @Test
    fun aSmallDismissedAmountStillNeedsAHundredMegabytesMore() {
        assertNull(freeSpaceOffer(OversizedPdfs(1, 90 * mb), 10 * mb))
        assertEquals(OversizedPdfs(2, 130 * mb), freeSpaceOffer(OversizedPdfs(2, 130 * mb), 10 * mb))
    }
}
