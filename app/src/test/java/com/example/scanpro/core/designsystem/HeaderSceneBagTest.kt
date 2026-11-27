package com.example.scanpro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HeaderSceneBagTest {

    @Test
    fun `every scene plays once before any comes round again`() {
        var bag = emptyList<HeaderScene>()
        var last: HeaderScene? = null
        val seen = mutableListOf<HeaderScene>()
        repeat(HeaderScene.entries.size) {
            val (picked, rest) = HeaderSceneBag.draw(bag, last, Random(7))
            seen += picked
            bag = rest
            last = picked
        }
        assertEquals(HeaderScene.entries.toSet(), seen.toSet())
        assertTrue(bag.isEmpty())
    }

    @Test
    fun `a refilled bag never starts with the scene just shown`() {
        for (seed in 0 until 200) {
            val last = HeaderScene.entries[seed % HeaderScene.entries.size]
            val (picked, rest) = HeaderSceneBag.draw(emptyList(), last, Random(seed))
            assertNotEquals(last, picked)
            assertEquals(HeaderScene.entries.size - 1, rest.size)
        }
    }

    @Test
    fun `the bag kept between launches carries on where it stopped`() {
        val stored = HeaderSceneBag.encode(listOf(HeaderScene.TRAIN, HeaderScene.BIRDS))
        val (picked, rest) = HeaderSceneBag.draw(HeaderSceneBag.decode(stored), HeaderScene.ROCKET)
        assertEquals(HeaderScene.TRAIN, picked)
        assertEquals(listOf(HeaderScene.BIRDS), rest)
    }

    @Test
    fun `a damaged or outdated stored bag is read as far as it makes sense`() {
        assertEquals(emptyList<HeaderScene>(), HeaderSceneBag.decode(null))
        assertEquals(emptyList<HeaderScene>(), HeaderSceneBag.decode(""))
        assertEquals(listOf(HeaderScene.TRAIN), HeaderSceneBag.decode("STEAM_ENGINE,TRAIN,,"))
        // A repeated name does not make a scene play twice in a round.
        val (_, rest) = HeaderSceneBag.draw(HeaderSceneBag.decode("TRAIN,TRAIN,BIRDS"), null)
        assertEquals(listOf(HeaderScene.BIRDS), rest)
    }
}
