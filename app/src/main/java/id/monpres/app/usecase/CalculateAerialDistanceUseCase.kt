package id.monpres.app.usecase

import android.location.Location

/**
 * A use case for calculating the aerial distance (great-circle distance) between two geographical points.
 *
 * This class uses the Android `Location.distanceBetween` method to perform the calculation,
 * which provides an accurate approximation of the distance in meters over the Earth's surface.
 *
 * It is implemented as an invokable class, allowing it to be called like a function.
 *
 * Example usage:
 * ```
 * val calculateDistance = CalculateAerialDistanceUseCase()
 * val distanceInMeters = calculateDistance(lat1, lon1, lat2, lon2)
 * ```
 */
class CalculateAerialDistanceUseCase {
    operator fun invoke(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
