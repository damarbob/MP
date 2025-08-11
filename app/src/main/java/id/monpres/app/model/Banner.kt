package id.monpres.app.model

data class Banner (
    val uri: String? = null,
    val order: Int? = null
) {
    companion object {
        const val COLLECTION = "banners"
    }
}
