package id.monpres.app.model

data class UserIdentity(
    var userId: String? = null,
    var email: String? = null,
    var createdAt: Double? = null,
    var updatedAt: Double? = null,
) {
    companion object {
        const val COLLECTION = "userIdentities"
    }
}
