package id.monpres.app.model

data class Service(
    var id: String? = null,
    var typeId: String? = null,
    var name: String? = null,
    var description: String? = null,
    var price: Double? = null,
    var recurring: Boolean? = null,
    var active: Boolean? = null,
    var imageUris: List<String>? = null,
    var searchTokens: List<String>? = null,
    var categoryId: String? = null,
)