package id.monpres.app.usecase

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt

class GetColorFromAttrUseCase {

    @ColorInt
    operator fun invoke(attr: Int, context: Context): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return context.resources.getColor(tv.resourceId)
    }

    fun getColorHex(attr: Int, context: Context): String {
        val colorInt = invoke(attr, context)
        return String.format("%06X", 0xFFFFFF and colorInt)
    }

}