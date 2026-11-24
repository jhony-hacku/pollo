package com.sge.finca.domain.model

import kotlin.math.cos
import kotlin.math.hypot

/**
 * Límite del rancho, en Kotlin puro (sin SDK de mapas).
 *
 * El rancho es la envolvente convexa de todos los potreros: el polígono más pequeño que los
 * encierra a todos, como una cerca tendida alrededor de la finca. Así una vaca que camina por el
 * corral o por el callejón entre dos potreros sigue "dentro del rancho", y solo se considera que
 * salió cuando se aleja de la finca. No hace falta dibujar nada nuevo: se arma con los potreros
 * que ya existen (#71).
 */
object RanchGeometry {

    /**
     * Tolerancia en metros fuera del límite antes de contar a la vaca como fuera: absorbe el error
     * del GPS del collar y evita falsas alarmas cuando pasta pegada a la cerca.
     */
    const val DEFAULT_MARGIN_M = 25.0

    private const val METERS_PER_DEGREE = 111_320.0

    /** Límite del rancho a partir de los potreros. Vacío si no hay al menos 3 puntos distintos. */
    fun perimeter(paddocks: List<Paddock>): List<Coordinate> = convexHull(paddocks.flatMap { it.points })

    /**
     * Envolvente convexa (algoritmo de la cadena monótona de Andrew) en sentido antihorario.
     * Devuelve una lista vacía si los puntos no encierran un área (menos de 3 o todos en línea).
     */
    fun convexHull(points: List<Coordinate>): List<Coordinate> {
        val sorted = points.distinct().sortedWith(compareBy({ it.longitude }, { it.latitude }))
        if (sorted.size < 3) return emptyList()

        val lower = mutableListOf<Coordinate>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
            lower.add(p)
        }
        val upper = mutableListOf<Coordinate>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
            upper.add(p)
        }
        // El último de cada mitad es el primero de la otra.
        val hull = lower.dropLast(1) + upper.dropLast(1)
        return if (hull.size >= 3) hull else emptyList()
    }

    /**
     * ¿La vaca en [point] está fuera del rancho? Fuera del polígono y a más de [marginM] metros
     * de su borde. Sin límite (menos de 3 puntos) nunca está fuera: no hay contra qué comparar.
     */
    fun isOutside(perimeter: List<Coordinate>, point: Coordinate, marginM: Double = DEFAULT_MARGIN_M): Boolean {
        if (perimeter.size < 3) return false
        if (PaddockGeometry.contains(perimeter, point)) return false
        return distanceToBoundaryM(perimeter, point) > marginM
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

    /** > 0 si o→a→b gira a la izquierda (antihorario), sobre (lng, lat). */
    private fun cross(o: Coordinate, a: Coordinate, b: Coordinate): Double =
        (a.longitude - o.longitude) * (b.latitude - o.latitude) -
            (a.latitude - o.latitude) * (b.longitude - o.longitude)
}
