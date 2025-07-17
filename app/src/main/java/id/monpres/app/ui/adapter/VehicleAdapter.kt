package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.databinding.ItemTwoLineFirstBinding
import id.monpres.app.databinding.ItemTwoLineLastBinding
import id.monpres.app.databinding.ItemTwoLineMidBinding
import id.monpres.app.model.Vehicle

class VehicleAdapter(
    private var vehicles: List<Vehicle>,
    private val onItemClick: ((Vehicle) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM_FIRST = 1
        private const val VIEW_TYPE_ITEM_MID = 2
        private const val VIEW_TYPE_ITEM_LAST = 3
        private const val VIEW_TYPE_ITEM_ALONE = 4
    }

    override fun getItemViewType(position: Int): Int {
        return if (itemCount == 1) {
            VIEW_TYPE_ITEM_ALONE
        } else if (position == 0) {
            VIEW_TYPE_ITEM_FIRST
        } else if (position == itemCount - 1) {
            VIEW_TYPE_ITEM_LAST
        } else {
            VIEW_TYPE_ITEM_MID
        }
    }

    override fun getItemCount(): Int {
        return vehicles.size
    }

    inner class VehicleAloneViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(vehicle: Vehicle) {
            binding.itemTwoLineTextViewTitle.text = vehicle.name
            binding.itemTwoLineTextViewSubtitle.text = vehicle.registrationNumber

            binding.root.setOnClickListener {
                onItemClick?.let { onClick ->
                    onClick(vehicle)
                }
            }
        }
    }

    inner class VehicleFirstViewHolder(val binding: ItemTwoLineFirstBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(vehicle: Vehicle) {
            binding.itemTwoLineTextViewTitle.text = vehicle.name
            binding.itemTwoLineTextViewSubtitle.text = vehicle.registrationNumber

            binding.root.setOnClickListener {
                onItemClick?.let { onClick ->
                    onClick(vehicle)
                }
            }
        }
    }

    inner class VehicleLastViewHolder(val binding: ItemTwoLineLastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(vehicle: Vehicle) {
            binding.itemTwoLineTextViewTitle.text = vehicle.name
            binding.itemTwoLineTextViewSubtitle.text = vehicle.registrationNumber

            binding.root.setOnClickListener {
                onItemClick?.let { onClick ->
                    onClick(vehicle)
                }
            }
        }
    }

    inner class VehicleMidViewHolder(val binding: ItemTwoLineMidBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(vehicle: Vehicle) {
            binding.itemTwoLineTextViewTitle.text = vehicle.name
            binding.itemTwoLineTextViewSubtitle.text = vehicle.registrationNumber

            binding.root.setOnClickListener {
                onItemClick?.let { onClick ->
                    onClick(vehicle)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ITEM_ALONE) {
            VehicleAloneViewHolder(
                ItemTwoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else if (viewType == VIEW_TYPE_ITEM_FIRST) {
            VehicleFirstViewHolder(
                ItemTwoLineFirstBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else if (viewType == VIEW_TYPE_ITEM_LAST) {
            VehicleLastViewHolder(
                ItemTwoLineLastBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        } else {
            VehicleMidViewHolder(
                ItemTwoLineMidBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VehicleAloneViewHolder -> holder.bind(vehicles[position])
            is VehicleFirstViewHolder -> holder.bind(vehicles[position])
            is VehicleLastViewHolder -> holder.bind(vehicles[position])
            is VehicleMidViewHolder -> holder.bind(vehicles[position])
        }
    }
}