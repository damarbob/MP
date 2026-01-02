package id.monpres.app.usecase

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt

/**
 * Use case for resolving theme attribute colors to their integer and hex representations.
 */
class GetColorFromAttrUseCase {

    /**
     * Resolves a theme attribute to its color integer value.
     *
     * @param attr The theme attribute resource identifier
     * @param context The Android context for theme resolution
     * @return The resolved color as an integer
     */
    @ColorInt
    operator fun invoke(attr: Int, context: Context): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return context.resources.getColor(tv.resourceId)
    }

    /**
     * Resolves a theme attribute to its hexadecimal color string.
     *
     * @param attr The theme attribute resource identifier
     * @param context The Android context for theme resolution
     * @return The resolved color as a 6-character hex string (without alpha)
     */
    fun getColorHex(attr: Int, context: Context): String {
        val colorInt = invoke(attr, context)
        return String.format("%06X", 0xFFFFFF and colorInt)
    }

}
