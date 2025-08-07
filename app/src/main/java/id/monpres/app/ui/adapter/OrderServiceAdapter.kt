package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.model.OrderService

class OrderServiceAdapter(
    private val onItemClick: (OrderService) -> Unit // Regular click
) : ListAdapter<OrderService, OrderServiceAdapter.ViewHolder>(OrderServiceDiffCallback()) {

    class OrderServiceDiffCallback : DiffUtil.ItemCallback<OrderService>() {
        override fun areItemsTheSame(
            oldItem: OrderService,
            newItem: OrderService
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: OrderService,
            newItem: OrderService
        ): Boolean = oldItem == newItem
    }

    inner class ViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(orderService: OrderService) {
            // TODO: Bind data to view
            binding.itemTwoLineTextViewTitle.text = orderService.name
            binding.itemTwoLineTextViewSubtitle.text =
                "${orderService.vehicle?.name} - ${orderService.serviceId}"
            binding.itemTwoLineTextViewFirstLabel.text = (orderService.price ?: "") as CharSequence?
            binding.root.setOnClickListener {
                onItemClick(orderService)
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