package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemOrderItemBinding
import id.monpres.app.model.OrderItem
import id.monpres.app.usecase.IndonesianCurrencyFormatter

class OrderItemAdapter() :
    ListAdapter<OrderItem, OrderItemAdapter.OrderItemViewHolder>(OrderItemDiffCallback()) {

    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()

    class OrderItemDiffCallback() : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(
            oldItem: OrderItem,
            newItem: OrderItem
        ): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: OrderItem,
            newItem: OrderItem
        ): Boolean = oldItem == newItem

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): OrderItemViewHolder = OrderItemViewHolder(
        ItemOrderItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(
        holder: OrderItemViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class OrderItemViewHolder(val binding: ItemOrderItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(orderItem: OrderItem) {
            binding.orderItemTextViewName.text = when (orderItem.id) {
                OrderItem.PLATFORM_FEE_ID -> binding.root.context.getString(OrderItem.PLATFORM_FEE_NAME)
                OrderItem.DISTANCE_FEE_ID -> binding.root.context.getString(OrderItem.DISTANCE_FEE_NAME)
                else -> orderItem.name
            }
            binding.orderItemTextViewPrice.text = indonesianCurrencyFormatter(orderItem.price)
            binding.orderItemTextViewQuantity.text = "x${orderItem.quantity}"
            binding.orderItemTextViewSubtotal.text = indonesianCurrencyFormatter(orderItem.subtotal)

        }
    }

}
