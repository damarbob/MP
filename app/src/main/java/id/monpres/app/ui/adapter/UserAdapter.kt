package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.R
import id.monpres.app.databinding.ItemUserBinding
import id.monpres.app.model.MontirPresisiUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. REMOVE constructor list, and extend ListAdapter
class UserAdapter :
    ListAdapter<MontirPresisiUser, UserAdapter.ServiceViewHolder>(UserDiffCallback()) {

    // Click listener interface (no change needed)
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
        // 2. Get the item using ListAdapter's method
        val user = getItem(position)
        holder.bind(user)
    }

    // 3. REMOVE getItemCount() - ListAdapter handles this automatically.

    inner class ServiceViewHolder(val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // The bind method logic is perfect, no changes needed
        fun bind(user: MontirPresisiUser) {

            val userCreatedAtTimestamp = user.createdAt

            // Format the timestamp to a user-readable date string
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val createdAtDate = userCreatedAtTimestamp?.let { Date(it.toLong()) }
            val formattedDate = createdAtDate?.let { sdf.format(it) } ?: "N/A"

            binding.apply {
                itemUserTextViewTitle.text = user.displayName
                itemUserTextViewSubtitle.text =
                    root.context.getString(R.string.joined_at_x, formattedDate)

                // Your click listener setup is fine
                root.setOnClickListener {
                    menuClickListener?.onMenuClicked(user)
                }
            }
        }
    }

    // 4. ADD this DiffCallback class inside UserAdapter
    class UserDiffCallback : DiffUtil.ItemCallback<MontirPresisiUser>() {
        override fun areItemsTheSame(
            oldItem: MontirPresisiUser,
            newItem: MontirPresisiUser
        ): Boolean {
            // This checks if the items represent the same object
            // You MUST have a unique ID in your model (see note below)
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: MontirPresisiUser,
            newItem: MontirPresisiUser
        ): Boolean {
            // This checks if the item's contents have changed
            // This works perfectly if MontirPresisiUser is a data class
            return oldItem == newItem
        }
    }
}