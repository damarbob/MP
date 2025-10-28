package id.monpres.app.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.utils.toDateTimeDisplayString

class OrderServiceAdapter(
    private val context: Context,
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

    private val indonesianCurrencyFormatter = IndonesianCurrencyFormatter()

    inner class ViewHolder(val binding: ItemTwoLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(orderService: OrderService) {
            val date = orderService.updatedAt.toDateTimeDisplayString()
            binding.itemTwoLineTextViewTitle.text = orderService.name
            binding.itemTwoLineTextViewSubtitle.text =
                "${orderService.vehicle?.name} - $date"

            binding.itemTwoLineTextViewFirstLabel.text =
                if (orderService.price != null) indonesianCurrencyFormatter(orderService.price!!) else ""
            binding.itemTwoLineTextViewSecondLabel.text = (orderService.status?.getLabel(context) ?: "")
            binding.itemTwoLineTextViewFirstLabel.isSelected = true
            binding.itemTwoLineTextViewSecondLabel.isSelected = true
            binding.itemTwoLineTextViewSubtitle.isSelected = true
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