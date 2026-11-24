package com.sge.finca.domain.model

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Límites del rancho, en Kotlin puro (sin SDK de mapas).
 *
 * Cada potrero (#71) es un área cercada. Una vaca está dentro del rancho si está dentro de alguno
 * de ellos (o pegada a su cerca, ver [DEFAULT_MARGIN_M]); si está en el terreno que queda entre
 * dos potreros o ranchos separados, está fuera. No se une a los potreros en un solo polígono
 * grande: eso dejaría "dentro" a una vaca perdida entre dos ranchos.
 */
object RanchGeometry {

    /**
     * Tolerancia en metros fuera de la cerca antes de contar a la vaca como fuera: absorbe el error
     * del GPS del collar y evita falsas alarmas cuando pasta pegada a la cerca.
     */
    const val DEFAULT_MARGIN_M = 25.0

    private const val METERS_PER_DEGREE = 111_320.0

    /** Áreas vigiladas: los potreros que encierran un área (3 puntos o más). */
    fun areas(paddocks: List<Paddock>): List<List<Coordinate>> =
        paddocks.map { it.points }.filter { it.size >= 3 }

    /**
     * ¿La vaca en [point] está fuera del rancho? No está dentro de ningún área y está a más de
     * [marginM] metros de la cerca más cercana. Sin áreas nunca está fuera: no hay contra qué comparar.
     */
    fun isOutside(areas: List<List<Coordinate>>, point: Coordinate, marginM: Double = DEFAULT_MARGIN_M): Boolean {
        val valid = areas.filter { it.size >= 3 }
        if (valid.isEmpty()) return false
        if (valid.any { PaddockGeometry.contains(it, point) }) return false
        return valid.minOf { distanceToBoundaryM(it, point) } > marginM
    }

    /** Distancia en metros desde [point] hasta el borde más cercano del polígono. */
    fun distanceToBoundaryM(polygon: List<Coordinate>, point: Coordinate): Double {
        if (polygon.isEmpty()) return Double.POSITIVE_INFINITY
        if (polygon.size == 1) return PaddockGeometry.distanceM(polygon[0], point)
        // Proyección plana local alrededor del punto: exacta a la escala de una finca.
        val metersPerDegreeLng = METERS_PER_DEGREE * cos(Math.toRadians(point.latitude))
        fun east(c: Coordinate) = (c.longitude - point.longitude) * metersPerDegreeLng
        fun north(c: Coordinate) = (c.latitude - point.latitude) * METERS_PER_DEGREE

        var best = Double.POSITIVE_INFINITY
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val d = distanceToSegment(east(a), north(a), east(b), north(b))
            if (d < best) best = d
        }
        return best
    }

    /** Distancia del origen (el punto) al segmento AB, en el plano. */
    private fun distanceToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return hypot(ax, ay)
        val t = (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }
}
