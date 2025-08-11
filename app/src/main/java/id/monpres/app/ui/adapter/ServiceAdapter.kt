package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemServiceBinding
import id.monpres.app.model.Service

class ServiceAdapter(
    private val services: List<Service>?
) : RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    // Click listener interface for service selection
    interface OnServiceClickListener {
        fun onServiceClicked(serviceId: String?)
    }

    private var serviceClickListener: OnServiceClickListener? = null

    fun setOnServiceClickListener(listener: OnServiceClickListener) {
        serviceClickListener = listener
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
        services?.getOrNull(position)?.let { service ->
            holder.bind(service)
        }
    }

    override fun getItemCount(): Int = services?.size ?: 0

    inner class ServiceViewHolder(val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            binding.apply {
                itemServiceTextViewTitle.text = service.name
                itemServiceTextViewSubtitle.text = service.description
                itemServiceImageViewImage.visibility = View.VISIBLE

                // Set click listener on root view of item layout
                root.setOnClickListener {
                    serviceClickListener?.onServiceClicked(service.id)
                }
            }
        }
    }
}