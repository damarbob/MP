package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.R
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.model.MontirPresisiUser

class PartnerAdapter(
    private val onItemClick: (MontirPresisiUser) -> Unit
) : ListAdapter<PartnerAdapter.PartnerItem, PartnerAdapter.ViewHolder>(PartnerDiffCallback()) {

    // Data class to hold both partner and distance
    data class PartnerItem(
        val partner: MontirPresisiUser,
        val distance: Double? // in kilometers, null if distance not available
    )

    class PartnerDiffCallback : DiffUtil.ItemCallback<PartnerItem>() {
        override fun areItemsTheSame(
            oldItem: PartnerItem,
            newItem: PartnerItem
        ): Boolean = oldItem.partner.userId == newItem.partner.userId

        override fun areContentsTheSame(
            oldItem: PartnerItem,
            newItem: PartnerItem
        ): Boolean = oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(partnerItem: PartnerItem) {
            val partner = partnerItem.partner
            binding.itemTwoLineImageViewImage.setImageDrawable(
                ResourcesCompat.getDrawable(
                    binding.root.resources,
                    R.drawable.engineering_24px,
                    null
                )
            )
            binding.itemTwoLineTextViewTitle.text = partner.displayName

            // Format distance information
            val distanceText = partnerItem.distance?.let { distance ->
                binding.root.context.getString(R.string.x_km).format(distance)
            } ?: binding.root.context.getString(R.string.distance_not_available)

            binding.itemTwoLineTextViewSubtitle.text = distanceText

            // TODO: Add other essential info (if any)
            binding.itemTwoLineTextViewFirstLabel.text = ""
            binding.itemTwoLineTextViewSecondLabel.text = ""

            binding.itemTwoLineTextViewFirstLabel.isSelected = true
            binding.itemTwoLineTextViewSecondLabel.isSelected = true
            binding.itemTwoLineTextViewSubtitle.isSelected = true

            binding.root.setOnClickListener {
                onItemClick(partner)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder = ViewHolder(
        ItemTwoLineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    // Helper method to update with partners and distances
    fun submitPartnersWithDistance(partnersWithDistance: List<Pair<MontirPresisiUser, Double?>>) {
        val partnerItems = partnersWithDistance.map { (partner, distance) ->
            PartnerItem(partner, distance)
        }
        submitList(partnerItems)
    }
}