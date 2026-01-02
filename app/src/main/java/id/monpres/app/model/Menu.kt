package id.monpres.app.model

import androidx.annotation.IntegerRes

data class Menu(
    var id: String? = null,
    var title: String? = null,
    var subtitle: String? = null,
    @field:IntegerRes
    var iconRes: Int? = null,
)
