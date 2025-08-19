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
    private val onItemClick: (MontirPresisiUser) -> Unit // Regular click
) : ListAdapter<MontirPresisiUser, PartnerAdapter.ViewHolder>(PartnerDiffCallback()) {

    class PartnerDiffCallback : DiffUtil.ItemCallback<MontirPresisiUser>() {
        override fun areItemsTheSame(
            oldItem: MontirPresisiUser,
            newItem: MontirPresisiUser
        ): Boolean = oldItem.userId == newItem.userId

        override fun areContentsTheSame(
            oldItem: MontirPresisiUser,
            newItem: MontirPresisiUser
        ): Boolean = oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(partner: MontirPresisiUser) {
            binding.itemTwoLineImageViewImage.setImageDrawable(ResourcesCompat.getDrawable(binding.root.resources, R.drawable.engineering_24px, null))
            binding.itemTwoLineTextViewTitle.text = partner.displayName

            // TODO: Add estimated distance and other essential info
            binding.itemTwoLineTextViewSubtitle.text = ""
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
    ): ViewHolder =
        ViewHolder(ItemTwoLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }
}