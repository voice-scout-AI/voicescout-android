package com.voicescout.voicescout_android.record

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class RecordPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val sentences: Array<String>,
) : FragmentStateAdapter(fragmentActivity) {
    override fun createFragment(position: Int): Fragment {
        // 각 페이지에 해당하는 Fragment 생성
        return RecordPageFragment.newInstance(sentences[position], position)
    }

    override fun getItemCount(): Int = sentences.size
}
