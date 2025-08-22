package id.monpres.app.model

import android.os.Parcelable
import id.monpres.app.enums.UserRole
import kotlinx.parcelize.Parcelize

@Parcelize
data class MontirPresisiUser(
    var userId: String? = null,
    var displayName: String? = null,
    var role: UserRole? = null,
    var phoneNumber: String? = null,
    var locationLat: String? = null,
    var locationLng: String? = null,
    var createdAt: Double? = null,
    var updatedAt: Double? = null,
) : Parcelable {
    companion object {
        val COLLECTION = "users"
    }
}