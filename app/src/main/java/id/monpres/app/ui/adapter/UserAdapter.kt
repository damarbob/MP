package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemUserBinding
import id.monpres.app.model.MontirPresisiUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserAdapter(
    private val users: List<MontirPresisiUser>?
) : RecyclerView.Adapter<UserAdapter.ServiceViewHolder>() {

    // Click listener interface for service selection
    interface OnItemClickListener {
        fun onMenuClicked(user: MontirPresisiUser?)
    }

    private var menuClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        menuClickListener = listener
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ServiceViewHolder = ServiceViewHolder(
        ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        users?.getOrNull(position)?.let { user ->
            holder.bind(user)
        }
    }

    override fun getItemCount(): Int = users?.size ?: 0

    inner class ServiceViewHolder(val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: MontirPresisiUser) {

            val userCreatedAtTimestamp = user.createdAt

            // Format the timestamp to a user-readable date string
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val createdAtDate = userCreatedAtTimestamp?.let { Date(it.toLong()) }
            val formattedDate = createdAtDate?.let { sdf.format(it) } ?: "N/A"

            binding.apply {
                itemUserTextViewTitle.text = user.displayName
                itemUserTextViewSubtitle.text = "$formattedDate"

                // Set click listener on root view of item layout
                root.setOnClickListener {
                    menuClickListener?.onMenuClicked(user)
                }
            }
        }
    }
}