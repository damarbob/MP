package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.MainApplication
import id.monpres.app.R
import id.monpres.app.databinding.ItemTwoLineBinding
import id.monpres.app.model.OrderService
import id.monpres.app.usecase.IndonesianCurrencyFormatter
import id.monpres.app.utils.toDateTimeDisplayString

class OrderServiceAdapter(
    private val onItemClick: (OrderService, View) -> Unit // Regular click
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
            binding.itemTwoLineImageViewImage.setImageDrawable(
                AppCompatResources.getDrawable(
                    binding.root.context,
                    R.drawable.build_24px
                )
            )
            binding.itemTwoLineTextViewTitle.text =
                MainApplication.services?.find { it.id == orderService.serviceId }?.name
            binding.itemTwoLineTextViewSubtitle.text =
                "${orderService.vehicle?.name} - $date"

            binding.itemTwoLineTextViewFirstLabel.text =
                if (orderService.price != null) indonesianCurrencyFormatter(orderService.price!!) else ""
            binding.itemTwoLineTextViewSecondLabel.text =
                (orderService.status?.getLabel(binding.root.context) ?: "")
            binding.itemTwoLineTextViewFirstLabel.isSelected = true
            binding.itemTwoLineTextViewSecondLabel.isSelected = true
            binding.itemTwoLineTextViewSubtitle.isSelected = true

            binding.root.transitionName =
                binding.root.context.getString(R.string.transition_name_item, orderService.id)
            binding.root.setOnClickListener {
                onItemClick(orderService, binding.root)
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