package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemOrderItemEditorBinding
import id.monpres.app.model.OrderItem
import id.monpres.app.usecase.CurrencyFormatterUseCase

class OrderItemEditorAdapter(
) : ListAdapter<OrderItem, OrderItemEditorAdapter.ViewHolder>(OrderItemDiffCallback()) {

    class OrderItemDiffCallback: DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(
            oldItem: OrderItem,
            newItem: OrderItem
        ): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: OrderItem,
            newItem: OrderItem
        ): Boolean = oldItem == newItem

    }

    interface ItemClickListener {
        fun onEditClick(item: OrderItem)
        fun onDeleteClick(item: OrderItem)
    }

    private var listener: ItemClickListener? = null
    private val currencyFormatterUseCase = CurrencyFormatterUseCase()

    fun registerItemClickListener(listener: ItemClickListener) {
        this.listener = listener
    }

    inner class ViewHolder(val binding: ItemOrderItemEditorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OrderItem) {
            binding.orderItemEditorTextViewName.text = item.name
            binding.orderItemEditorTextViewQuantity.text = "x${item.quantity}"
            binding.orderItemEditorTextViewPrice.text = "${currencyFormatterUseCase(item.price)}"
            binding.orderItemEditorTextViewSubtotal.text = "${currencyFormatterUseCase(item.subtotal)}"

            binding.orderItemEditorButtonEdit.visibility = if (item.isFixed) ViewGroup.GONE else ViewGroup.VISIBLE
            binding.orderItemEditorButtonDelete.visibility = if (item.isFixed) ViewGroup.GONE else ViewGroup.VISIBLE

            binding.orderItemEditorTextViewPrice.isSelected = true
            binding.orderItemEditorTextViewSubtotal.isSelected = true

            binding.orderItemEditorButtonEdit.setOnClickListener {
                listener?.onEditClick(item)
            }

            binding.orderItemEditorButtonDelete.setOnClickListener {
                listener?.onDeleteClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemOrderItemEditorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}