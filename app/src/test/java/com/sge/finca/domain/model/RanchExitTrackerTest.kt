package com.sge.finca.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RanchExitTrackerTest {

    private val lat0 = 4.6
    private val lng0 = -74.1
    private val mLat = 1.0 / 111_320.0
    private val mLng = 1.0 / (111_320.0 * Math.cos(Math.toRadians(lat0)))
    private fun at(east: Double, north: Double) = Coordinate(lat0 + north * mLat, lng0 + east * mLng)

    // Rancho de 200 m x 200 m.
    private val ranch = listOf(at(0.0, 0.0), at(200.0, 0.0), at(200.0, 200.0), at(0.0, 200.0))
    private val inside = at(100.0, 100.0)
    private val outside = at(100.0, 400.0)

    private fun cow(id: String, where: Coordinate) =
        TrackedAnimal(Animal(id = id, name = "Vaca $id"), where.latitude, where.longitude)

    @Test
    fun cowThatStaysInsideNeverAlerts() {
        val tracker = RanchExitTracker()
        repeat(5) { assertTrue(tracker.update(listOf(ranch), listOf(cow("1", inside)), it.toLong()).isEmpty()) }
        assertTrue(tracker.currentExits.isEmpty())
    }

    @Test
    fun exitIsConfirmedAfterTwoReadingsAndAnnouncedOnce() {
        val tracker = RanchExitTracker(confirmReadings = 2)
        assertTrue(tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L).isEmpty())

        val events = tracker.update(listOf(ranch), listOf(cow("1", outside)), 2L)
        assertEquals(1, events.size)
        val exit = (events.single() as RanchEvent.Exited).exit
        assertEquals("1", exit.animalId)
        assertEquals("Vaca 1", exit.animalName)
        assertEquals(2L, exit.since)

        // Sigue afuera: no se repite el aviso.
        assertTrue(tracker.update(listOf(ranch), listOf(cow("1", outside)), 3L).isEmpty())
        assertEquals(1, tracker.currentExits.size)
    }

    @Test
    fun aSingleGpsJumpDoesNotAlert() {
        val tracker = RanchExitTracker(confirmReadings = 2)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L)
        tracker.update(listOf(ranch), listOf(cow("1", inside)), 2L)
        assertTrue(tracker.update(listOf(ranch), listOf(cow("1", outside)), 3L).isEmpty())
        assertTrue(tracker.currentExits.isEmpty())
    }

    @Test
    fun returnIsAnnouncedAfterTwoReadingsInside() {
        val tracker = RanchExitTracker(confirmReadings = 2)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 2L)

        assertTrue(tracker.update(listOf(ranch), listOf(cow("1", inside)), 3L).isEmpty())
        val events = tracker.update(listOf(ranch), listOf(cow("1", inside)), 4L)
        assertEquals(listOf(RanchEvent.Returned("1", "Vaca 1")), events)
        assertTrue(tracker.currentExits.isEmpty())
    }

    @Test
    fun exitKeepsTheLatestPosition() {
        val tracker = RanchExitTracker(confirmReadings = 1)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L)
        val further = at(100.0, 500.0)
        tracker.update(listOf(ranch), listOf(cow("1", further)), 2L)
        assertEquals(further, tracker.currentExits.single().position)
        assertEquals(1L, tracker.currentExits.single().since)
    }

    @Test
    fun eachCowIsTrackedSeparately() {
        val tracker = RanchExitTracker(confirmReadings = 1)
        val events = tracker.update(listOf(ranch), listOf(cow("1", outside), cow("2", inside), cow("3", outside)), 1L)
        assertEquals(listOf("1", "3"), events.map { (it as RanchEvent.Exited).exit.animalId })
        assertEquals(listOf("1", "3"), tracker.currentExits.map { it.animalId })
    }

    @Test
    fun deletedCowIsForgottenSilently() {
        val tracker = RanchExitTracker(confirmReadings = 1)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L)
        assertTrue(tracker.update(listOf(ranch), emptyList(), 2L).isEmpty())
        assertTrue(tracker.currentExits.isEmpty())
    }

    @Test
    fun withoutARanchNothingIsWatched() {
        val tracker = RanchExitTracker(confirmReadings = 1)
        tracker.update(listOf(ranch), listOf(cow("1", outside)), 1L)
        assertTrue(tracker.update(emptyList(), listOf(cow("1", outside)), 2L).isEmpty())
        assertTrue(tracker.currentExits.isEmpty())
    }
}
