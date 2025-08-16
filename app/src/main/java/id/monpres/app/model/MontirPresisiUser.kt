package id.monpres.app.model

import id.monpres.app.enums.UserRole

data class MontirPresisiUser(
    var userId: String? = null,
    var displayName: String? = null,
    var role: UserRole? = null,
    var phoneNumber: String? = null,
    var createdAt: Double? = null,
    var updatedAt: Double? = null,
) {
    companion object {
        val COLLECTION = "users"
    }
}