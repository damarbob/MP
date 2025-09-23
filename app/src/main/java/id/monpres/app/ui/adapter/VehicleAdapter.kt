package id.monpres.app.ui.adapter

import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.model.Vehicle

/**
 * Adapter for displaying a list of [Vehicle] items in a RecyclerView.
 * This adapter supports item selection and a contextual action bar (CAB).
 *
 * @property onItemClick Lambda to be invoked when an item is clicked in normal mode.
 * @property onSelectionModeChanged Lambda to be invoked when the selection mode changes
 *                                  (e.g., to show or hide the CAB).
 * @property onItemSelected Lambda to be invoked when an item's selection state changes.
 */
class VehicleAdapter(
    private val onItemClick: (Vehicle) -> Unit, // Regular click
    private val onSelectionModeChanged: (Boolean) -> Unit, // To notify Fragment about CAB
    private val onItemSelected: (Vehicle) -> Unit // To notify when an item is selected/deselected
) : ListAdapter<Vehicle, VehicleAdapter.ViewHolder>(VehicleDiffCallback()) {

    constructor(onItemClick: (Vehicle) -> Unit) : this(onItemClick, {}, {}) {
        this.activateLongClickListener = false
    }

    private var activateLongClickListener = true

    private val selectedItems = SparseBooleanArray()
    var isInSelectionMode = false
        private set

    class VehicleDiffCallback : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(oldItem: Vehicle, newItem: Vehicle) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Vehicle, newItem: Vehicle) =
            oldItem == newItem // Make sure your Vehicle data class implements equals correctly
    }

    inner class ViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(vehicle: Vehicle, isSelected: Boolean) {
            binding.itemTwoLineTextViewTitle.text = vehicle.name
            binding.itemTwoLineTextViewSubtitle.text = vehicle.registrationNumber

            // Visual indication for selection
            binding.root.apply {
                isCheckable = true
                isChecked = isSelected // Use state_checked
            }

            binding.root.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val vehicle = getItem(position)
                    if (isInSelectionMode) {
                        toggleSelection(position, vehicle)
                    } else {
                        onItemClick(vehicle)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION && activateLongClickListener) {
                    if (!isInSelectionMode) {
                        startSelectionMode()
                    }
                    toggleSelection(position, getItem(position))
                    true // Consume the long click
                } else {
                    false
                }
            }

            // Or change background color directly (less ideal than state_checked with a selector drawable)
            // itemView.setBackgroundColor(if (isSelected) Color.LTGRAY else Color.TRANSPARENT)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemTwoLineBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), selectedItems.get(position, false))
    }

    private fun startSelectionMode() {
        if (!isInSelectionMode) {
            isInSelectionMode = true
            onSelectionModeChanged(true) // Notify Fragment to start CAB
        }
    }

    fun finishSelectionMode() {
        if (isInSelectionMode) {
            isInSelectionMode = false
            selectedItems.clear()
            notifyDataSetChanged() // To clear visual selection
            onSelectionModeChanged(false) // Notify Fragment to finish CAB
        }
    }

    private fun toggleSelection(position: Int, vehicle: Vehicle) {
        if (selectedItems.get(position, false)) {
            selectedItems.delete(position)
        } else {
            selectedItems.put(position, true)
        }
        notifyItemChanged(position)
        onItemSelected(vehicle) // Notify fragment about the item selection change

        // If no items are selected anymore, but still in selection mode, finish it
        if (isInSelectionMode && selectedItems.isEmpty()) {
            finishSelectionMode()
        } else if (!isInSelectionMode && selectedItems.isNotEmpty()) {
            // This case might happen if selection starts outside a long press (e.g. checkbox)
            startSelectionMode()
        }
    }

    fun getSelectedVehicleIds(): List<String> {
        val ids = ArrayList<String>()
        for (i in 0 until selectedItems.size) {
            if (selectedItems.valueAt(i)) { // Check if the value is true (selected)
                val position = selectedItems.keyAt(i)
                ids.add(getItem(position).id)
            }
        }
        return ids
    }

    fun getSelectedVehicles(): List<Vehicle> {
        val vehicles = ArrayList<Vehicle>()
        for (i in 0 until selectedItems.size) {
            if (selectedItems.valueAt(i)) {
                val position = selectedItems.keyAt(i)
                vehicles.add(getItem(position))
            }
        }
        return vehicles
    }


    fun getSelectedItemCount(): Int {
        var count = 0
        for (i in 0 until selectedItems.size) {
            if (selectedItems.valueAt(i)) {
                count++
            }
        }
        return count
    }

    // Call this if your list data changes externally while in selection mode
    fun clearSelection() {
        if (isInSelectionMode) {
            selectedItems.clear()
            notifyDataSetChanged()
            // Optionally finish selection mode if desired
            if (selectedItems.isEmpty()) {
                finishSelectionMode()
            }
        }
    }
}