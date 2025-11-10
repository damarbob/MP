package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.R
import id.monpres.app.databinding.ItemTwoLineToggleableBinding
import id.monpres.app.model.PaymentMethod

class PaymentMethodAdapter(
    private val paymentMethods: List<PaymentMethod>,
    private var selectedId: String,
    private val onItemClicked: (PaymentMethod) -> Unit
) : RecyclerView.Adapter<PaymentMethodAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTwoLineToggleableBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTwoLineToggleableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = paymentMethods[position]
        with(holder.binding) {
            root.background = when (position) {
                0 -> ResourcesCompat.getDrawable(root.resources,R.drawable.item_background_first, null)
                itemCount - 1 -> ResourcesCompat.getDrawable(root.resources,R.drawable.item_background_last, null)
                else -> ResourcesCompat.getDrawable(root.resources,R.drawable.item_background_mid, null)
            }

            itemTwoLineToggleableTextViewTitle.text = item.name
            itemTwoLineToggleableTextViewSubtitle.text = item.description ?: ""
            itemTwoLineToggleableImageViewImage.setImageResource(item.iconRes)

            val isSelected = item.id == selectedId
            itemTwoLineToggleableImageViewCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            root.isChecked = isSelected

            root.setOnClickListener {
                val previousSelectedPosition = paymentMethods.indexOfFirst { it.id == selectedId }
                selectedId = item.id
                notifyItemChanged(previousSelectedPosition) // Un-check the old item
                notifyItemChanged(position) // Check the new item
                onItemClicked(item)
            }
        }
    }

    override fun getItemCount() = paymentMethods.size
}
