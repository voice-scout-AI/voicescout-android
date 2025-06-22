package com.voicescout.voicescout_android.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.voicescout.voicescout_android.R

class BannerAdapter(private val bannerCards: List<BannerCard>) :
    RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardImageView: ImageView = itemView.findViewById(R.id.cardImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner_card, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val actualPosition = position % bannerCards.size
        val card = bannerCards[actualPosition]
        
        holder.cardImageView.setImageResource(card.imageResId)
    }

    override fun getItemCount(): Int {
        // 무한 스크롤을 위해 매우 큰 수를 반환
        return if (bannerCards.isEmpty()) 0 else Int.MAX_VALUE
    }

    fun getRealItemCount(): Int = bannerCards.size
} 