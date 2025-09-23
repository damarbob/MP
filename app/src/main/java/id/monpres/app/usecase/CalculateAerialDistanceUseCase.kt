package id.monpres.app.usecase

import android.location.Location

class CalculateAerialDistanceUseCase {
    operator fun invoke(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}