package id.monpres.app.utils

import android.app.Activity

fun Int.dpToPx(activity: Activity): Int {
    val density = activity.resources.displayMetrics.density
    return (this * density).toInt()
}
