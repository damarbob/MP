package id.monpres.app.ui.itemdecoration

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * An [RecyclerView.ItemDecoration] that adds spacing around items in a [RecyclerView].
 *
 * This decoration adds spacing to the top, bottom, left, and right of each item.
 * The amount of spacing can be customized using the `size` parameter.
 * The `edgeEnabled` parameter controls whether spacing is added to the edges of the RecyclerView.
 *
 * @param size The amount of spacing to add, in logical density of the display (dp).
 * @param edgeEnabled Whether to add spacing to the edges of the RecyclerView.
 */
class SpacingItemDecoration(
    private val size: Int,
    private val edgeEnabled: Boolean = false
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // Pixel to dp
        val sizeInDp = (size * view.context.resources.displayMetrics.density).toInt()

        // Basic item positioning
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount
        val isLastPosition = position == (itemCount - 1)
        val isFirstPosition = position == 0

        // Saved size
        val sizeBasedOnEdge = if (edgeEnabled) sizeInDp else 0
        val sizeBasedOnFirstPosition = if (isFirstPosition) sizeBasedOnEdge else sizeInDp
        val sizeBasedOnLastPosition =
            if (isLastPosition) sizeBasedOnEdge else 0

        // Update properties
        with(outRect) {
            left = sizeBasedOnEdge
            top = sizeBasedOnFirstPosition
            right = sizeBasedOnEdge
            bottom = sizeBasedOnLastPosition
        }
    }
}
