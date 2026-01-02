package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.R
import id.monpres.app.databinding.ItemServiceBinding
import id.monpres.app.model.Menu

class MenuAdapter(
    private val menu: List<Menu>?
) : RecyclerView.Adapter<MenuAdapter.ServiceViewHolder>() {

    // Click listener interface for service selection
    interface OnMenuClickListener {
        fun onMenuClicked(menuId: String?)
    }

    private var menuClickListener: OnMenuClickListener? = null

    fun setOnMenuClickListener(listener: OnMenuClickListener) {
        menuClickListener = listener
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ServiceViewHolder = ServiceViewHolder(
        ItemServiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        menu?.getOrNull(position)?.let { service ->
            holder.bind(service)
        }
    }

    override fun getItemCount(): Int = menu?.size ?: 0

    inner class ServiceViewHolder(val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(menu: Menu) {
            binding.apply {
                itemServiceTextViewTitle.text = menu.title
                itemServiceTextViewSubtitle.text = menu.subtitle
                itemServiceImageViewImage.setImageResource(menu.iconRes ?: R.drawable.build_24px)
                itemServiceImageViewImage.visibility = View.VISIBLE

                // Set click listener on root view of item layout
                root.setOnClickListener {
                    menuClickListener?.onMenuClicked(menu.id)
                }
            }
        }
    }
}
