package com.sge.finca.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RanchGeometryTest {

    // Metros -> grados cerca de Bogotá.
    private val lat0 = 4.6
    private val lng0 = -74.1
    private val mLat = 1.0 / 111_320.0
    private val mLng = 1.0 / (111_320.0 * Math.cos(Math.toRadians(lat0)))

    /** Punto a [east] y [north] metros del origen. */
    private fun at(east: Double, north: Double) = Coordinate(lat0 + north * mLat, lng0 + east * mLng)

    private fun square(east: Double, north: Double, size: Double) = listOf(
        at(east, north),
        at(east + size, north),
        at(east + size, north + size),
        at(east, north + size),
    )

    private fun paddock(id: String, points: List<Coordinate>) =
        Paddock(id, "finca", id, points, 0.0, "uid", 0L, 0L)

    // Dos ranchos de 100 m separados por 300 m de terreno ajeno.
    private val west = square(0.0, 0.0, 100.0)
    private val east = square(400.0, 0.0, 100.0)
    private val ranch = listOf(west, east)

    @Test
    fun areasAreThePaddocksThatEncloseSomething() {
        val line = paddock("raya", listOf(at(0.0, 0.0), at(10.0, 0.0)))
        val areas = RanchGeometry.areas(listOf(paddock("1", west), line, paddock("2", east)))
        assertEquals(listOf(west, east), areas)
    }

    @Test
    fun cowInsideEitherRanchIsInside() {
        assertFalse(RanchGeometry.isOutside(ranch, at(50.0, 50.0)))
        assertFalse(RanchGeometry.isOutside(ranch, at(450.0, 50.0)))
    }

    @Test
    fun cowBetweenTwoRanchesIsOutside() {
        // En la mitad de los dos ranchos: 150 m de cada cerca.
        assertTrue(RanchGeometry.isOutside(ranch, at(250.0, 50.0)))
    }

    @Test
    fun cowJustPastTheFenceIsWithinTheMargin() {
        // 10 m al este del rancho oeste: menos que el margen de 25 m (error del GPS).
        assertFalse(RanchGeometry.isOutside(ranch, at(110.0, 50.0)))
    }

    @Test
    fun cowFarFromEveryFenceIsOutside() {
        assertTrue(RanchGeometry.isOutside(ranch, at(50.0, 160.0)))
        assertTrue(RanchGeometry.isOutside(ranch, at(-80.0, 50.0)))
    }

    @Test
    fun withoutPaddocksNoCowIsOutside() {
        assertFalse(RanchGeometry.isOutside(emptyList(), at(9_999.0, 9_999.0)))
        assertFalse(RanchGeometry.isOutside(listOf(listOf(at(0.0, 0.0), at(10.0, 0.0))), at(9_999.0, 9_999.0)))
    }

    @Test
    fun distanceToBoundaryIsInMeters() {
        assertEquals(60.0, RanchGeometry.distanceToBoundaryM(west, at(50.0, 160.0)), 1.0)
        // Diagonal desde la esquina (100, 100): 30 m este y 40 m norte = 50 m.
        assertEquals(50.0, RanchGeometry.distanceToBoundaryM(west, at(130.0, 140.0)), 1.0)
    }

    @Test
    fun marginCanBeChanged() {
        val point = at(50.0, 140.0) // 40 m fuera del rancho oeste
        assertTrue(RanchGeometry.isOutside(ranch, point, marginM = 25.0))
        assertFalse(RanchGeometry.isOutside(ranch, point, marginM = 50.0))
    }
}
