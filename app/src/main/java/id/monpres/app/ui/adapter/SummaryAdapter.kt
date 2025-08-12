package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.monpres.app.databinding.ItemSimpleOneLineBinding
import id.monpres.app.model.Summary
import java.text.NumberFormat
import java.util.Locale

class SummaryAdapter(private val summaries: List<Summary>) :
    RecyclerView.Adapter<SummaryAdapter.SummaryViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SummaryViewHolder = SummaryViewHolder(
        ItemSimpleOneLineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(
        holder: SummaryViewHolder,
        position: Int
    ) {
        holder.bind(summaries[position])
    }

    override fun getItemCount(): Int = summaries.size

    inner class SummaryViewHolder(val binding: ItemSimpleOneLineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(summary: Summary) {
            val idrFormat = NumberFormat.getCurrencyInstance(
                Locale.Builder().setRegion("ID").setLanguage("id").build()
            )
            idrFormat.maximumFractionDigits = 0
            binding.itemSimpleOneLineTextViewTitle.text = summary.title
            binding.itemSimpleOneLineTextViewSubtitle.text = idrFormat.format(summary.price)
            binding.itemSimpleOneLineImageViewImage.visibility = View.GONE
        }
    }

}