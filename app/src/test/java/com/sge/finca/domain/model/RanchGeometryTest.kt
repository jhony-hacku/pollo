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

    // Dos potreros de 100 m separados por un callejón de 50 m: el rancho los abarca a ambos.
    private val west = paddock("Potrero 1", square(0.0, 0.0, 100.0))
    private val east = paddock("Potrero 2", square(150.0, 0.0, 100.0))
    private val ranch = RanchGeometry.perimeter(listOf(west, east))

    @Test
    fun perimeterOfTwoSquaresIsTheirOuterRectangle() {
        assertEquals(4, ranch.size)
        assertTrue(ranch.contains(at(0.0, 0.0)))
        assertTrue(ranch.contains(at(250.0, 0.0)))
        assertTrue(ranch.contains(at(250.0, 100.0)))
        assertTrue(ranch.contains(at(0.0, 100.0)))
    }

    @Test
    fun hullDropsInnerPoints() {
        val points = square(0.0, 0.0, 100.0) + at(50.0, 50.0) + at(10.0, 90.0)
        assertEquals(4, RanchGeometry.convexHull(points).size)
    }

    @Test
    fun noRanchWithoutAnArea() {
        assertTrue(RanchGeometry.perimeter(emptyList()).isEmpty())
        assertTrue(RanchGeometry.convexHull(listOf(at(0.0, 0.0), at(10.0, 0.0))).isEmpty())
        // Todos en línea: no encierran nada.
        assertTrue(RanchGeometry.convexHull(listOf(at(0.0, 0.0), at(10.0, 0.0), at(20.0, 0.0))).isEmpty())
        assertFalse(RanchGeometry.isOutside(emptyList(), at(9_999.0, 9_999.0)))
    }

    @Test
    fun cowInsideAPaddockIsInside() {
        assertFalse(RanchGeometry.isOutside(ranch, at(50.0, 50.0)))
    }

    @Test
    fun cowInTheLaneBetweenPaddocksIsStillInsideTheRanch() {
        assertFalse(RanchGeometry.isOutside(ranch, at(125.0, 50.0)))
    }

    @Test
    fun cowJustPastTheFenceIsWithinTheMargin() {
        // 10 m al norte del borde: menos que el margen de 25 m (error del GPS).
        assertFalse(RanchGeometry.isOutside(ranch, at(125.0, 110.0)))
    }

    @Test
    fun cowFarFromTheFenceIsOutside() {
        assertTrue(RanchGeometry.isOutside(ranch, at(125.0, 160.0)))
        assertTrue(RanchGeometry.isOutside(ranch, at(-80.0, 50.0)))
    }

    @Test
    fun distanceToBoundaryIsInMeters() {
        assertEquals(60.0, RanchGeometry.distanceToBoundaryM(ranch, at(125.0, 160.0)), 1.0)
        // Diagonal desde la esquina (250, 100): 30 m este y 40 m norte = 50 m.
        assertEquals(50.0, RanchGeometry.distanceToBoundaryM(ranch, at(280.0, 140.0)), 1.0)
    }

    @Test
    fun marginCanBeChanged() {
        val point = at(125.0, 140.0) // 40 m fuera
        assertTrue(RanchGeometry.isOutside(ranch, point, marginM = 25.0))
        assertFalse(RanchGeometry.isOutside(ranch, point, marginM = 50.0))
    }
}
