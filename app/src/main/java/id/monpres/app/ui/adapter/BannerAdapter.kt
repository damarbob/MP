package id.monpres.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import id.monpres.app.databinding.ItemCarouselHeroBinding
import id.monpres.app.model.Banner

class BannerAdapter(
    private val bannerList: List<Banner>,
) : RecyclerView.Adapter<BannerAdapter.BannerHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerHolder {
        return BannerHolder.from(parent)
    }

    override fun onBindViewHolder(holder: BannerHolder, position: Int) {
        val banner = bannerList[position]
        holder.bind(banner)
    }

    override fun getItemCount(): Int {
        return bannerList.size
    }

    class BannerHolder(private var itemHolderBinding: ItemCarouselHeroBinding) :
        RecyclerView.ViewHolder(itemHolderBinding.root) {
        fun bind(banner: Banner) {
            Glide.with(itemHolderBinding.root)
                .load(banner.uri)
                .into(itemHolderBinding.itemCarouselHeroImage)
        }

        companion object {
            fun from(parent: ViewGroup) : BannerHolder {
                val itemView = ItemCarouselHeroBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return BannerHolder(itemView)
            }
        }
    }
}
