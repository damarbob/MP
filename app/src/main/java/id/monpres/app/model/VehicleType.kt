package id.monpres.app.model

import android.content.Context
import android.os.Parcelable
import id.monpres.app.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class VehicleType(
    var id: String? = null,
    var name: String? = null,
) : Parcelable {
    companion object {
        const val CAR_ID = "CAR"
        const val MOTORCYCLE_ID = "MOTORCYCLE"
        const val OTHER_ID = "OTHER"

        // Generate sample list of vehicles
        fun getSampleList(context: Context): List<VehicleType> {
            return listOf(
                VehicleType(CAR_ID, context.getString(R.string.car)),
                VehicleType(MOTORCYCLE_ID, context.getString(R.string.motorcycle)),
                VehicleType(OTHER_ID, context.getString(R.string.other)),

            )
        }
    }
}
