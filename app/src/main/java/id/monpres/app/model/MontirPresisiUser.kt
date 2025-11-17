package id.monpres.app.model

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import id.monpres.app.enums.PartnerCategory
import id.monpres.app.enums.UserRole
import id.monpres.app.enums.UserVerificationStatus
import kotlinx.parcelize.Parcelize

@Parcelize
data class MontirPresisiUser(
    @DocumentId
    val id: String? = null,

    var userId: String? = null,
    var displayName: String? = null,
    var role: UserRole? = null,
    var phoneNumber: String? = null,
    var locationLat: String? = null,
    var locationLng: String? = null,
    var address: String? = null, // The complete address of the user (required)
    var active: Boolean? = null,
    var createdAt: Double? = null,
    var updatedAt: Double? = null,
    var fcmTokens: List<String>? = null,

    // User verification status
    var verificationStatus: UserVerificationStatus? = UserVerificationStatus.PENDING,

    // Fields for verification
    var instagramId: String? = null,
    var facebookId: String? = null,

    var partnerCategories: List<PartnerCategory>? = null,
) : Parcelable {
    companion object {
        val COLLECTION = "users"
    }
}